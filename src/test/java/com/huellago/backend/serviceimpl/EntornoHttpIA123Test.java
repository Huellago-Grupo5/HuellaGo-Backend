package com.huellago.backend.serviceimpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Solo mapas, JDBC Mockito y archivos locales; nunca DriverManager ni servidor. */
class EntornoHttpIA123Test {
    static Map<String,String> variables() {
        return new HashMap<>(Map.of(EntornoHttpIA123.ACTIVACION,"true","HUELLAGO_IA123_PG_URL",EntornoHttpIA123.URL,
                "HUELLAGO_IA123_PG_USUARIO",EntornoHttpIA123.ROL,"HUELLAGO_IA123_CONFIRMAR_BASE",EntornoHttpIA123.BASE,
                "HUELLAGO_IA123_PG_CLAVE","secreto-SINTETICO-unitario"));
    }
    @ParameterizedTest @ValueSource(strings={"ausente","false","TRUE","1"})
    void sinActivacionExactaNoHayConexion(String valor) {
        var v=variables(); if(valor.equals("ausente")) v.remove(EntornoHttpIA123.ACTIVACION); else v.put(EntornoHttpIA123.ACTIVACION,valor);
        assertThrows(IllegalStateException.class,()->EntornoHttpIA123.validar(v));
    }
    @ParameterizedTest @ValueSource(strings={"huellagodb","huellagodb_hu29_test","postgres","prueba_db","huellagodb_ia_integracion_test_otro"})
    void rechazaOtrasBasesAntesDeAbrir(String base) throws Exception {
        var v=variables(); v.put("HUELLAGO_IA123_PG_URL","jdbc:postgresql://localhost:5432/"+base);
        FuenteHttpIA123.Abrir abrir=mock(FuenteHttpIA123.Abrir.class);
        assertThrows(IllegalArgumentException.class,()->new FuenteHttpIA123(EntornoHttpIA123.validar(v),abrir)); verifyNoInteractions(abrir);
    }
    @ParameterizedTest @ValueSource(strings={"127.0.0.1:5432","localhost:5433","otro:5432","localhost:5432,otro:5432","localhost:5432/huellagodb?x="})
    void rechazaHostPuertoYParametros(String destino) {
        var v=variables(); v.put("HUELLAGO_IA123_PG_URL","jdbc:postgresql://"+destino+"/"+EntornoHttpIA123.BASE);
        assertThrows(IllegalArgumentException.class,()->EntornoHttpIA123.validar(v));
    }
    @ParameterizedTest @ValueSource(strings={"postgres","otro","huellago_ia119_test ",""})
    void rechazaOtroRol(String rol) {
        var v=variables(); v.put("HUELLAGO_IA123_PG_USUARIO",rol); assertThrows(IllegalArgumentException.class,()->EntornoHttpIA123.validar(v));
    }
    @Test void configuracionIndependienteValidateSinInicializadores() throws Exception {
        assertEquals("validate",EntornoHttpIA123.propiedadesJpa().get("hibernate.hbm2ddl.auto"));
        assertEquals("false",EntornoHttpIA123.propiedadesJpa().get("hibernate.show_sql"));
        assertEquals("127.0.0.1",EntornoHttpIA123.DIRECCION); assertEquals(18081,EntornoHttpIA123.PUERTO);
        String src=Files.readString(Path.of("src/test/java/com/huellago/backend/serviceimpl/AplicacionHttpIA123.java"));
        assertFalse(src.contains("@ComponentScan")); assertFalse(src.contains("@EnableAutoConfiguration"));
        assertFalse(src.contains("@SpringBootApplication")); assertFalse(src.contains("SpringApplication.run"));
        assertFalse(src.contains("RefrescoRecomendacionesListener")); assertFalse(src.contains("@EnableScheduling"));
        assertFalse(src.contains("data.sql")); assertFalse(src.contains("application.properties"));
        assertFalse(src.contains("ResourceDatabasePopulator")); assertFalse(src.contains("DataSourceScriptDatabaseInitializer"));
        assertFalse(src.contains("Flyway")); assertFalse(src.contains("Liquibase"));
    }
    @ParameterizedTest @ValueSource(strings={"base","usuario","sesion","administrador"})
    void destinoEfectivoInvalidoSeCierraSinExponerConexion(String caso) throws Exception {
        var c=mock(Connection.class); var s=mock(Statement.class); var r=mock(ResultSet.class);
        when(c.createStatement()).thenReturn(s); when(s.executeQuery(anyString())).thenReturn(r); when(r.next()).thenReturn(true);
        when(r.getString(1)).thenReturn(caso.equals("base")?"huellagodb":EntornoHttpIA123.BASE);
        when(r.getString(2)).thenReturn(caso.equals("usuario")?"otro":EntornoHttpIA123.ROL);
        when(r.getString(3)).thenReturn(caso.equals("sesion")?"postgres":EntornoHttpIA123.ROL);
        when(r.getBoolean(4)).thenReturn(caso.equals("administrador"));
        var ds=new FuenteHttpIA123(EntornoHttpIA123.validar(variables()),(url,p)->c);
        assertThrows(SQLException.class,ds::getConnection); verify(c).close();
        verify(s,times(1)).executeQuery(startsWith("SELECT current_database()"));
    }
    @Test void compruebaCadaConexionYSanitizaFallos() throws Exception {
        var abrir=mock(FuenteHttpIA123.Abrir.class); var c=mock(Connection.class); var s=mock(Statement.class); var r=mock(ResultSet.class);
        when(abrir.conectar(eq(EntornoHttpIA123.URL),any())).thenReturn(c); when(c.createStatement()).thenReturn(s);
        when(s.executeQuery(anyString())).thenReturn(r); when(r.next()).thenReturn(true);
        when(r.getString(1)).thenReturn(EntornoHttpIA123.BASE); when(r.getString(2)).thenReturn(EntornoHttpIA123.ROL); when(r.getString(3)).thenReturn(EntornoHttpIA123.ROL);
        var ds=new FuenteHttpIA123(EntornoHttpIA123.validar(variables()),abrir);
        assertSame(c,ds.getConnection()); assertSame(c,ds.getConnection()); verify(s,times(2)).executeQuery(anyString());
        assertThrows(SQLException.class,()->ds.getConnection("otro","otra"));
        when(abrir.conectar(anyString(),any())).thenThrow(new SQLException("No revelar secreto-SINTETICO-unitario","08001"));
        var error=assertThrows(SQLException.class,ds::getConnection);
        assertFalse(error.getMessage().contains("secreto-SINTETICO-unitario")); assertNull(error.getCause());
        assertFalse(ds.toString().contains("secreto-SINTETICO-unitario"));
    }
    @Test void clasesDePruebaNoPertenecenALaSalidaProductiva() throws Exception {
        for(String clase:List.of("AplicacionHttpIA123","ControladorHttpIA123","SeguridadHttpIA123","EntornoHttpIA123","FuenteHttpIA123","DatosSinteticosIA123")) {
            assertTrue(Files.exists(Path.of("target/test-classes/com/huellago/backend/serviceimpl/"+clase+".class")));
            assertFalse(Files.exists(Path.of("target/classes/com/huellago/backend/serviceimpl/"+clase+".class")));
        }
        String pom=Files.readString(Path.of("pom.xml"));
        assertFalse(pom.contains("<testOutputDirectory>")); assertFalse(pom.contains("<includeTestScope>"));
    }
    @Test void conectorPreparadoSoloLoopbackSinIniciarServidor() {
        var tomcat=AplicacionHttpIA123.servidorSinArrancar();
        var conector=tomcat.getConnector();
        assertEquals(18081,conector.getPort()); assertEquals(1,tomcat.getService().findConnectors().length);
        var protocolo=(org.apache.coyote.AbstractProtocol<?>)conector.getProtocolHandler();
        assertEquals("127.0.0.1",protocolo.getAddress().getHostAddress()); assertTrue(protocolo.getAddress().isLoopbackAddress());
        assertFalse(tomcat.getServer().getState().isAvailable()); assertEquals(-1,tomcat.getServer().getPort());
        assertEquals(-1,conector.getLocalPort()); // sin bind ni socket abierto
    }
}
