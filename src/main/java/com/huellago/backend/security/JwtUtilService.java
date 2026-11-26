package com.huellago.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtUtilService {
    private static final long JWT_TOKEN_VALIDITY = 1000 * 60 * 60 * 3L;
    private static final byte[] LOCAL_SECRET_BYTES = new byte[32];

    private String secretoExterno = "";
    private final Environment entorno;
    private final boolean permiteGeneracionLocal;

    @Autowired
    public JwtUtilService(@Value("${huellago.jwt.secret:}") String secretoExterno, Environment entorno) {
        this(secretoExterno, entorno, true);
    }

    /** Constructor conservado para usos explícitos y pruebas unitarias. */
    public JwtUtilService(String secretoExterno) {
        this(secretoExterno, null, false);
    }

    /** Constructor conservado para pruebas que inyectan la clave mediante reflexión. */
    public JwtUtilService() {
        this("", null, false);
    }

    private JwtUtilService(String secretoExterno, Environment entorno, boolean permiteGeneracionLocal) {
        this.secretoExterno = secretoExterno;
        this.entorno = entorno;
        this.permiteGeneracionLocal = permiteGeneracionLocal;
    }

    @jakarta.annotation.PostConstruct
    void verificarConfiguracion() {
        if (secretoExterno == null || secretoExterno.isBlank()) {
            if (!permiteGeneracionLocal || esProduccion()) {
                throw new IllegalStateException(
                        "HUELLAGO_JWT_SECRET es obligatorio en producción y en instancias sin generación local habilitada");
            }
            secretoExterno = cargarOCrearSecretoLocal();
        }
        getSigningKey();
    }

    private boolean esProduccion() {
        return entorno != null && entorno.acceptsProfiles(Profiles.of("prod", "production"));
    }

    private SecretKey getSigningKey() {
        if (secretoExterno == null || secretoExterno.isBlank()) {
            throw new IllegalStateException("HUELLAGO_JWT_SECRET no configurado");
        }
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretoExterno));
    }

    private static synchronized String cargarOCrearSecretoLocal() {
        Path directorio = Path.of(System.getProperty("user.home"), ".huellago");
        Path archivoSecreto = directorio.resolve("jwt-secret");
        Path archivoBloqueo = directorio.resolve("jwt-secret.lock");

        try {
            crearDirectorioPrivado(directorio);
            crearArchivoPrivadoSiNoExiste(archivoBloqueo);

            try (FileChannel canal = FileChannel.open(archivoBloqueo, StandardOpenOption.WRITE);
                    FileLock bloqueo = canal.lock()) {
                if (!bloqueo.isValid()) {
                    throw new IOException("No se pudo bloquear el archivo de clave JWT local");
                }

                if (Files.exists(archivoSecreto, LinkOption.NOFOLLOW_LINKS)) {
                    if (Files.isSymbolicLink(archivoSecreto)) {
                        throw new IOException("El archivo local de clave JWT no puede ser un enlace simbólico");
                    }
                    restringirPermisos(archivoSecreto, "rw-------");
                    String existente = Files.readString(archivoSecreto, StandardCharsets.US_ASCII).trim();
                    if (existente.isEmpty()) {
                        throw new IOException("El archivo local de clave JWT está vacío");
                    }
                    return existente;
                }

                byte[] bytes = new byte[LOCAL_SECRET_BYTES.length];
                new SecureRandom().nextBytes(bytes);
                String generado = Base64.getEncoder().encodeToString(bytes);
                Path temporal = Files.createTempFile(directorio, ".jwt-secret-", ".tmp");
                try {
                    restringirPermisos(temporal, "rw-------");
                    Files.writeString(temporal, generado, StandardCharsets.US_ASCII, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                    try {
                        Files.move(temporal, archivoSecreto, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException error) {
                        Files.move(temporal, archivoSecreto);
                    }
                    restringirPermisos(archivoSecreto, "rw-------");
                    return generado;
                } finally {
                    Files.deleteIfExists(temporal);
                }
            }
        } catch (IOException | UnsupportedOperationException error) {
            throw new IllegalStateException(
                    "No se pudo crear o leer la clave JWT local persistente en " + archivoSecreto, error);
        }
    }

    private static void crearDirectorioPrivado(Path directorio) throws IOException {
        try {
            Files.createDirectories(directorio, PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwx------")));
        } catch (UnsupportedOperationException error) {
            Files.createDirectories(directorio);
        }
        restringirPermisos(directorio, "rwx------");
    }

    private static void crearArchivoPrivadoSiNoExiste(Path archivo) throws IOException {
        try {
            Files.createFile(archivo, PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rw-------")));
        } catch (FileAlreadyExistsException ignored) {
            // El archivo de bloqueo ya existe y se reutiliza entre reinicios.
        } catch (UnsupportedOperationException error) {
            if (!Files.exists(archivo)) Files.createFile(archivo);
        }
        restringirPermisos(archivo, "rw-------");
    }

    private static void restringirPermisos(Path ruta, String permisos) throws IOException {
        try {
            Files.setPosixFilePermissions(ruta, PosixFilePermissions.fromString(permisos));
        } catch (UnsupportedOperationException ignored) {
            // En sistemas sin permisos POSIX se mantienen los permisos predeterminados del usuario.
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsFunction) {
        return claimsFunction.apply(extractAllClaims(token));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean validateToken(String token, UserSecurity user) {
        return extractPurpose(token) == null
                && !isTokenExpired(token)
                && ValidacionCuenta.correo(extractUsername(token)).equals(ValidacionCuenta.correo(user.getUsername()));
    }

    public String extractPurpose(String token) {
        return extractClaim(token, claims -> claims.get("purpose", String.class));
    }

    private String createToken(String subject, Map<String, Object> claims) {
        return Jwts.builder()
                .claims(claims)
                .id(java.util.UUID.randomUUID().toString())
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + JWT_TOKEN_VALIDITY))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String generateToken(UserSecurity securityUser) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", securityUser.getUser().getId());
        return createToken(ValidacionCuenta.correo(securityUser.getUsername()), claims);
    }
}
