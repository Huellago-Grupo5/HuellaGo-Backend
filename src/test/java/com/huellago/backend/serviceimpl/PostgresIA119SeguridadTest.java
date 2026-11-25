package com.huellago.backend.serviceimpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** No instancia contextos JPA ni solicita conexiones. */
class PostgresIA119SeguridadTest {
    @Test void permiteSoloDestinoExactoLocal() {
        assertEquals("jdbc:postgresql://localhost:5432/huellagodb_ia_integracion_test",
                PostgresIA119Soporte.validarUrl("jdbc:postgresql://localhost:5432/huellagodb_ia_integracion_test"));
        assertDoesNotThrow(()->PostgresIA119Soporte.validarUrl("jdbc:postgresql://127.0.0.1:5433/huellagodb_ia_integracion_test"));
    }
    @ParameterizedTest @ValueSource(strings={"huellagodb","huellagodb_hu29_test","postgres","prueba_db","huellagodb_ia_integracion_test_extra"})
    void rechazaOtrasBasesAntesDeAbrirConexion(String base) {
        assertThrows(IllegalArgumentException.class,()->PostgresIA119Soporte.validarUrl("jdbc:postgresql://localhost:5432/"+base));
        assertThrows(java.sql.SQLException.class,()->PostgresIA119Soporte.comprobarBase(base));
    }
    @ParameterizedTest @ValueSource(strings={
            "jdbc:postgresql://localhost:5432/huellagodb_ia_integracion_test?currentSchema=otro",
            "jdbc:postgresql://localhost:5432/huellagodb_ia_integracion_test?user=x&password=x",
            "jdbc:postgresql://localhost:5432/huellagodb_ia_integracion_test/../huellagodb",
            "jdbc:postgresql://localhost:5432/%68uellagodb_ia_integracion_test",
            "jdbc:postgresql://otro:5432/huellagodb_ia_integracion_test",
            "jdbc:postgresql://localhost,otro:5432/huellagodb_ia_integracion_test",
            "jdbc:postgresql:huellagodb_ia_integracion_test",
            "jdbc:postgresql://localhost:99999/huellagodb_ia_integracion_test"})
    void rechazaParametrosCodificacionYRedireccion(String url) {
        assertThrows(IllegalArgumentException.class,()->PostgresIA119Soporte.validarUrl(url));
    }
    @Test void erroresDeDestinoNoExponenElValorRecibido() {
        var e=assertThrows(IllegalArgumentException.class,()->PostgresIA119Soporte.validarUrl("CREDENCIAL_NO_IMPRIMIR"));
        assertFalse(e.getMessage().contains("CREDENCIAL_NO_IMPRIMIR"));
    }
    @Test void integracionExigeBanderaExplicitaAntesDeBeforeAll() {
        var a=PersistenciaRecomendacionesIAPostgresIntegrationTest.class.getAnnotation(
                org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable.class);
        assertNotNull(a); assertEquals("HUELLAGO_IA_PG_REAL_TEST",a.named()); assertEquals("true",a.matches());
    }
    @Test void configuracionSoloValidaEsquemaSinAbrirDatasourceNiImportarBoot() {
        var ds=new org.springframework.jdbc.datasource.AbstractDataSource() {
            public java.sql.Connection getConnection() { throw new AssertionError("No debe abrir conexiones"); }
            public java.sql.Connection getConnection(String u,String p) { throw new AssertionError("No debe abrir conexiones"); }
        };
        var fabrica=new PostgresIA119Soporte.Configuracion().entityManagerFactory(ds);
        assertEquals("validate",fabrica.getJpaPropertyMap().get("hibernate.hbm2ddl.auto"));
        assertEquals("false",fabrica.getJpaPropertyMap().get("hibernate.show_sql"));
        assertNull(PostgresIA119Soporte.Configuracion.class.getAnnotation(org.springframework.boot.autoconfigure.EnableAutoConfiguration.class));
        assertNull(PostgresIA119Soporte.Configuracion.class.getAnnotation(org.springframework.context.annotation.PropertySource.class));
    }
}
