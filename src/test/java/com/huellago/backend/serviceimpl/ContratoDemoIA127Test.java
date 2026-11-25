package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.GeneracionRecomendacionesDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContratoDemoIA127Test {
    final JsonMapper mapper=JsonMapper.builder().build();
    static Map<String,String> variables() {
        return new HashMap<>(Map.of("HUELLAGO_IA127_ACTIVAR","true","HUELLAGO_IA127_MODO","DEMO_ACADEMICA_LOCAL",
                "HUELLAGO_IA127_PG_URL",EntornoHttpIA123.URL,"HUELLAGO_IA127_PG_USUARIO",EntornoHttpIA123.ROL,
                "HUELLAGO_IA127_PG_CLAVE","clave-unitaria-SINTETICA","HUELLAGO_IA127_CONFIRMAR_BASE",EntornoHttpIA123.BASE,
                "HUELLAGO_IA127_BIND","127.0.0.1","HUELLAGO_IA127_PUERTO","8081"));
    }
    @Test void jsonLegadoExactamenteIgualIncluidosNullsAnteriores() {
        var dto=new GeneracionRecomendacionesDTO(91L,"REGLAS_DETERMINISTAS","GENERADAS","Mensaje","HABITOS_REGISTRADOS",null,null,null,null,List.of(),List.of(),List.of());
        String esperado="{\"usuarioId\":91,\"metodo\":\"REGLAS_DETERMINISTAS\",\"estado\":\"GENERADAS\",\"mensaje\":\"Mensaje\",\"basePriorizacion\":\"HABITOS_REGISTRADOS\",\"huellaId\":null,\"fechaCalculoHuella\":null,\"unidad\":null,\"periodo\":null,\"datosFaltantes\":[],\"advertencias\":[],\"recomendaciones\":[]}";
        // Jackson 3 ordena las propiedades alfabeticamente; conservar exactamente los doce campos previos.
        assertEquals(mapper.readTree(esperado),mapper.readTree(mapper.writeValueAsString(dto)));
        assertEquals("{\"advertencias\":[],\"basePriorizacion\":\"HABITOS_REGISTRADOS\",\"datosFaltantes\":[],\"estado\":\"GENERADAS\",\"fechaCalculoHuella\":null,\"huellaId\":null,\"mensaje\":\"Mensaje\",\"metodo\":\"REGLAS_DETERMINISTAS\",\"periodo\":null,\"recomendaciones\":[],\"unidad\":null,\"usuarioId\":91}",mapper.writeValueAsString(dto));
        assertNull(dto.getFinalidad()); assertNull(dto.getEstadoEditorial()); assertNull(dto.getRevisionHumana()); assertNull(dto.getPublicable());
        assertFalse(mapper.readTree(mapper.writeValueAsString(new GeneracionRecomendacionesDTO())).has("publicable"));
    }
    @Test void demoSerializaCuatroMetadatosConBooleanFalseReal() {
        var dto=EntradaDemoIA127.respuesta("SIN_PROPUESTA"); var json=mapper.readTree(mapper.writeValueAsString(dto));
        assertEquals("DEMO_ACADEMICA_LOCAL",json.get("finalidad").asString()); assertEquals("BORRADOR",json.get("estadoEditorial").asString());
        assertEquals("PENDIENTE_REVISION",json.get("revisionHumana").asString()); assertTrue(json.get("publicable").isBoolean()); assertFalse(json.get("publicable").asBoolean());
    }
    @ParameterizedTest @ValueSource(strings={"HUELLAGO_IA127_ACTIVAR","HUELLAGO_IA127_MODO","HUELLAGO_IA127_PG_URL","HUELLAGO_IA127_PG_USUARIO","HUELLAGO_IA127_BIND","HUELLAGO_IA127_PUERTO","HUELLAGO_IA127_CONFIRMAR_BASE","HUELLAGO_IA127_PG_CLAVE"})
    void controlesObligatoriosNoSustituiblesPorUnaBandera(String clave) {
        var v=variables(); v.remove(clave); assertThrows(RuntimeException.class,()->ProteccionDemoIA127.entorno(v));
    }
    @ParameterizedTest @ValueSource(strings={"huellagodb","huellagodb_hu29_test","postgres","prueba_db"})
    void ningunaOtraBaseEsValida(String base) {
        var v=variables(); v.put("HUELLAGO_IA127_PG_URL","jdbc:postgresql://localhost:5432/"+base);
        assertThrows(IllegalArgumentException.class,()->ProteccionDemoIA127.entorno(v));
    }
    @ParameterizedTest @ValueSource(strings={"127.0.0.1:5432","localhost:5433","otro:5432"})
    void hostPuertoJdbcRechazados(String host) {
        var v=variables(); v.put("HUELLAGO_IA127_PG_URL","jdbc:postgresql://"+host+"/"+EntornoHttpIA123.BASE);
        assertThrows(IllegalArgumentException.class,()->ProteccionDemoIA127.entorno(v));
    }
    @Test void rolInterfazYModoNoAutorizadosRechazados() {
        for(var p:Map.of("HUELLAGO_IA127_PG_USUARIO","postgres","HUELLAGO_IA127_BIND","0.0.0.0","HUELLAGO_IA127_PUERTO","18081","HUELLAGO_IA127_MODO","PUBLICABLE").entrySet()) {
            var v=variables(); v.put(p.getKey(),p.getValue()); assertThrows(RuntimeException.class,()->ProteccionDemoIA127.entorno(v));
        }
    }
    @Test void servidorPreparado8081LoopbackSinBindNiConfiguracionProductiva() {
        ProteccionDemoIA127.entorno(variables()); var t=AplicacionDemoIA127.servidorSinArrancar();
        assertEquals(8081,t.getConnector().getPort()); assertEquals(-1,t.getConnector().getLocalPort());
        assertEquals("127.0.0.1",((org.apache.coyote.AbstractProtocol<?>)t.getConnector().getProtocolHandler()).getAddress().getHostAddress());
        var c=AplicacionDemoIA127.nuevoContexto();
        try { assertEquals("never",c.getEnvironment().getProperty("spring.sql.init.mode")); assertEquals("validate",c.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
            assertEquals("false",c.getEnvironment().getProperty("spring.flyway.enabled")); assertEquals("false",c.getEnvironment().getProperty("spring.liquibase.enabled")); assertFalse(c.isActive()); }
        finally { c.close(); }
    }
    @Test void artefactoPrincipalNoContieneComposicionNiFixture() throws Exception {
        for(String nombre:List.of("AplicacionDemoIA127","EntradaDemoIA127","ProteccionDemoIA127","AutenticacionDemoIA127","RegistroEditorialPruebaInternaFixture"))
            assertFalse(Files.exists(Path.of("target/classes/com/huellago/backend/serviceimpl/"+nombre+".class")));
        String controlador=Files.readString(Path.of("src/main/java/com/huellago/backend/controllers/RecomendacionController.java"));
        assertFalse(controlador.contains("System.getenv")); assertFalse(controlador.contains("serviceimpl.Aplicacion"));
        assertFalse(controlador.contains("Fixture"));
    }
}
