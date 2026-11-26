package com.huellago.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huellago.backend.entities.Reto;
import com.huellago.backend.entities.ResultadoRetoColectivo;
import com.huellago.backend.entities.Sesion;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.entities.UsuarioRecomendacionIA;
import com.huellago.backend.entities.UsuarioReto;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class PostgresProjectInitializerTest {
    @Test
    void noSeparaPuntosYComasDentroDeBloquesDoNiComentarios() {
        String sql = "-- inicio; comentario\nDO $tag$ BEGIN PERFORM ';'; /* interno; */ END $tag$;\nSELECT 'fin; literal';";
        var commands = PostgresProjectInitializer.splitStatements(sql);
        assertEquals(2, commands.size());
        assertEquals("-- inicio; comentario\nDO $tag$ BEGIN PERFORM ';'; /* interno; */ END $tag$", commands.get(0));
        assertEquals("SELECT 'fin; literal'", commands.get(1));
    }

    @Test
    void admiteComillasDollarYComentariosAnidados() {
        String sql = "DO $$ BEGIN /* exterior /* interior; */ fin */ PERFORM 1; END $$; SELECT 2;";
        assertEquals(2, PostgresProjectInitializer.splitStatements(sql).size());
    }

    @Test
    void rechazaScriptConBloqueDollarSinCerrar() {
        assertThrows(IllegalArgumentException.class,
                () -> PostgresProjectInitializer.splitStatements("DO $x$ BEGIN PERFORM 1;"));
    }

    @Test
    void separaTodosLosScriptsDelProyectoSinPerderBloquesDo() throws Exception {
        try (Stream<Path> migrationPaths = Files.list(Path.of("db/migration"))) {
            var paths = Stream.concat(migrationPaths, Stream.of(Path.of("src/main/resources/data.sql"))).toList();
            for (Path path : paths) {
                var statements = PostgresProjectInitializer.splitStatements(Files.readString(path));
                assertTrue(!statements.isEmpty(), path.toString());
                assertTrue(statements.stream().anyMatch(command -> command.trim().toUpperCase().startsWith("DO ")),
                        "Debe conservar al menos un bloque DO en " + path);
            }
        }
    }

    @Test
    void configuracionYProteccionesAceptanLaBaseActualYLaDeCompose() throws Exception {
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));
        String data = Files.readString(Path.of("src/main/resources/data.sql"));
        String migrationV5 = Files.readString(Path.of("db/migration/V5__cuentas_recuperacion_segura.sql"));
        assertTrue(properties.contains("jdbc:postgresql://localhost:5432/huellagodb}"));
        assertTrue(data.contains("current_database() NOT IN ('huellagodb', 'huellagodb_hu29_test')"));
        assertTrue(migrationV5.contains("current_database() NOT IN ('huellagodb', 'huellagodb_hu29_test')"));
    }

    @Test
    void migracionesRespetanElEsquemaCreadoPorHibernate() throws Exception {
        // IDs y relaciones que V1/V2/V3 utilizan como claves foráneas.
        assertEquals(Integer.class, Reto.class.getDeclaredField("id").getType());
        assertEquals(Reto.class, ResultadoRetoColectivo.class.getDeclaredField("reto").getType());
        assertEquals(Integer.class, UsuarioReto.class.getDeclaredField("id").getType());
        assertEquals(Integer.class, UsuarioRecomendacionIA.class.getDeclaredField("id").getType());
        assertEquals(Long.class, Usuario.class.getDeclaredField("id").getType());
        assertEquals(2048, Sesion.class.getDeclaredField("token").getAnnotation(jakarta.persistence.Column.class).length());

        String v1 = Files.readString(Path.of("db/migration/V1__retos_colectivos.sql"));
        String v2 = Files.readString(Path.of("db/migration/V2__conversion_recomendaciones_retos.sql"));
        String v3 = Files.readString(Path.of("db/migration/V3__publicaciones_comunitarias.sql"));
        assertTrue(v1.contains("CREATE TABLE IF NOT EXISTS public.resultados_retos_colectivos"));
        assertTrue(v1.contains("ADD COLUMN IF NOT EXISTS tipo varchar(20)"));
        assertTrue(v2.contains("ADD COLUMN IF NOT EXISTS usuario_recomendacion_ia_id integer"));
        assertTrue(v3.contains("CREATE TABLE IF NOT EXISTS public.publicaciones_comunitarias"));

        try (Stream<Path> migrations = Files.list(Path.of("db/migration"))) {
            for (Path migration : migrations.toList()) {
                String sql = Files.readString(migration).toUpperCase();
                assertFalse(sql.matches("(?s).*\\bDROP\\s+TABLE\\b.*"), migration.toString());
                assertFalse(sql.matches("(?s).*\\bDELETE\\s+FROM\\b.*"), migration.toString());
            }
        }
    }
}
