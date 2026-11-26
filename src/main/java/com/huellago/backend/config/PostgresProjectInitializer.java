package com.huellago.backend.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.SmartInitializingSingleton;

/** Runs PostgreSQL scripts after Hibernate, with support for dollar-quoted DO blocks. */
@Component
@ConditionalOnProperty(name = "spring.jpa.hibernate.ddl-auto", havingValue = "update")
public class PostgresProjectInitializer implements SmartInitializingSingleton {
    private static final String MIGRATION_PATTERN = "classpath*:db/migration/V*.sql";
    private static final Pattern TRANSACTION_CONTROL = Pattern.compile(
            "(?is)^(?:\\s|--[^\\r\\n]*(?:\\r?\\n|$)|/\\*.*?\\*/)*(BEGIN|COMMIT|ROLLBACK)(?:\\s|;)*$");
    private final DataSource dataSource;
    @SuppressWarnings("unused")
    private final EntityManagerFactory entityManagerFactory;
    private final PathMatchingResourcePatternResolver resources = new PathMatchingResourcePatternResolver();

    public PostgresProjectInitializer(DataSource dataSource, EntityManagerFactory entityManagerFactory) {
        this.dataSource = dataSource;
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            Resource[] migrations = resources.getResources(MIGRATION_PATTERN);
            List<Resource> ordered = new ArrayList<>(List.of(migrations));
            ordered.sort(Comparator.comparing(resource -> resource.getFilename()));
            ensureHistoryTable();
            for (Resource migration : ordered) {
                applyMigration(migration);
            }
            applyDataScript(resources.getResource("classpath:data.sql"));
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo inicializar el esquema y los datos de HuellaGo", exception);
        }
    }

    private void ensureHistoryTable() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS public.huellago_schema_migrations ("
                    + "version varchar(255) PRIMARY KEY, checksum char(64) NOT NULL, applied_at timestamptz NOT NULL DEFAULT now())");
        }
    }

    private void applyMigration(Resource resource) throws IOException, SQLException {
        String filename = resource.getFilename();
        String version = filename.substring(0, filename.indexOf("__"));
        String sql = resource.getContentAsString(StandardCharsets.UTF_8);
        String checksum = sha256(sql);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                advisoryLock(connection);
                String previous = findChecksum(connection, version);
                if (previous != null) {
                    if (!previous.equals(checksum)) {
                        throw new SQLException("La migración " + version + " cambió después de aplicarse; no se ejecutará automáticamente");
                    }
                    connection.commit();
                    return;
                }
                executeScript(connection, sql);
                try (var insert = connection.prepareStatement(
                        "INSERT INTO public.huellago_schema_migrations(version, checksum) VALUES (?, ?)")) {
                    insert.setString(1, version);
                    insert.setString(2, checksum);
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (Exception exception) {
                rollback(connection, exception);
                if (exception instanceof SQLException sqlException) throw sqlException;
                throw new SQLException("Falló la migración " + version, exception);
            }
        }
    }

    private void applyDataScript(Resource resource) throws IOException, SQLException {
        if (!resource.exists()) throw new IOException("No se encontró classpath:data.sql");
        String sql = resource.getContentAsString(StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                advisoryLock(connection);
                executeScript(connection, sql);
                connection.commit();
            } catch (Exception exception) {
                rollback(connection, exception);
                if (exception instanceof SQLException sqlException) throw sqlException;
                throw new SQLException("Falló la carga de data.sql", exception);
            }
        }
    }

    private static void advisoryLock(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT pg_advisory_xact_lock(hashtext('HuellaGo automatic initializer'))");
        }
    }

    private static String findChecksum(Connection connection, String version) throws SQLException {
        try (var query = connection.prepareStatement(
                "SELECT checksum FROM public.huellago_schema_migrations WHERE version = ?")) {
            query.setString(1, version);
            try (var result = query.executeQuery()) {
                return result.next() ? result.getString(1).trim() : null;
            }
        }
    }

    private static void executeScript(Connection connection, String sql) throws SQLException {
        for (String command : splitStatements(sql)) {
            if (TRANSACTION_CONTROL.matcher(command).matches()) continue;
            try (Statement statement = connection.createStatement()) {
                statement.execute(command);
            }
        }
    }

    static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String dollarTag = null;
        char quote = 0;
        boolean lineComment = false;
        int blockCommentDepth = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;
            if (lineComment) {
                current.append(c);
                if (c == '\n') lineComment = false;
                continue;
            }
            if (blockCommentDepth > 0) {
                current.append(c);
                if (c == '/' && next == '*') { current.append(next); i++; blockCommentDepth++; }
                else if (c == '*' && next == '/') { current.append(next); i++; blockCommentDepth--; }
                continue;
            }
            if (dollarTag != null) {
                if (sql.startsWith(dollarTag, i)) {
                    current.append(dollarTag);
                    i += dollarTag.length() - 1;
                    dollarTag = null;
                } else current.append(c);
                continue;
            }
            if (quote != 0) {
                current.append(c);
                if (c == quote) {
                    if (next == quote) { current.append(next); i++; }
                    else if (quote == '\'' && i > 0 && sql.charAt(i - 1) == '\\') { /* escaped quote */ }
                    else quote = 0;
                }
                continue;
            }
            if (c == '-' && next == '-') { current.append(c).append(next); i++; lineComment = true; continue; }
            if (c == '/' && next == '*') { current.append(c).append(next); i++; blockCommentDepth = 1; continue; }
            if (c == '\'' || c == '"') { quote = c; current.append(c); continue; }
            if (c == '$') {
                int end = sql.indexOf('$', i + 1);
                if (end >= 0) {
                    String candidate = sql.substring(i, end + 1);
                    if (candidate.matches("\\$[A-Za-z_][A-Za-z0-9_]*\\$|\\$\\$")) {
                        dollarTag = candidate;
                        current.append(candidate);
                        i = end;
                        continue;
                    }
                }
            }
            if (c == ';') {
                String command = current.toString().trim();
                if (!command.isEmpty()) statements.add(command);
                current.setLength(0);
            } else current.append(c);
        }
        String command = current.toString().trim();
        if (!command.isEmpty()) statements.add(command);
        if (quote != 0 || dollarTag != null || blockCommentDepth != 0) {
            throw new IllegalArgumentException("SQL incompleto: literal, comentario o bloque dollar sin cerrar");
        }
        return statements;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }

    private static void rollback(Connection connection, Exception original) {
        try { connection.rollback(); } catch (SQLException rollbackFailure) { original.addSuppressed(rollbackFailure); }
    }
}
