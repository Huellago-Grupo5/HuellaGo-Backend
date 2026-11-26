package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;

/** HTTP interceptado; las respuestas sinteticas no representan inferencias reales. */
class OllamaResumen135Test {
    final HuellaContexto135Test h=new HuellaContexto135Test();
    final CatalogoAccionesAmbientalesServiceImpl catalogo=new CatalogoAccionesAmbientalesServiceImpl();
    final JsonMapper mapper=JsonMapper.builder().build();
    MockRestServiceServer servidor; OllamaRecomendacionesServiceImpl cliente;
    @BeforeEach void preparar() {
        h.preparar(); var builder=RestClient.builder().baseUrl("http://localhost:11434");
        servidor=MockRestServiceServer.bindTo(builder).build();
        cliente=new OllamaRecomendacionesServiceImpl(builder.build(),new OllamaConfiguration.Ajustes(
                URI.create("http://localhost:11434"),"qwen3:4b-instruct",Duration.ofSeconds(3),Duration.ofSeconds(120),65536));
    }
    List<CatalogoAccionesAmbientalesService.Candidata> permitidas(ContextoRecomendacionesIAService.Resultado r) {
        return catalogo.compatibles(r.contexto(),Set.of()).stream().filter(c->c.definicion().id().equals("TR_PLANIFICAR_DESPLAZAMIENTOS")).toList();
    }
    String propuesta(String categoria,String accion) {
        var p=new Propuesta(categoria,Decision.PROPONER,new Accion(accion,"1",Map.of()),List.of(
                new Evidencia("/transporte/medio",new Texto("Auto"),Ambito.NO_APLICA),
                new Evidencia("/transporte/kmSemana",new Numero(new BigDecimal("100")),Ambito.NO_APLICA),
                new Evidencia("/transporte/diasSemana",new Numero(new BigDecimal("5")),Ambito.NO_APLICA)),null,null,null,List.of(),null);
        return mapper.writeValueAsString(new RecomendacionFundamentadaIADTO("2",List.of(p)));
    }
    String envoltura(String contenido) {
        return mapper.writeValueAsString(Map.of("model","qwen3:4b-instruct","done",true,"message",Map.of("role","assistant","content",contenido)));
    }
    @Test void resumenSeparadoEsquemaSinCambiosYSubconjuntoExacto() {
        var r=h.constructor.construir(68L); var candidatas=permitidas(r); var original=propuesta("Transporte","TR_PLANIFICAR_DESPLAZAMIENTOS");
        var alcance=new ValidadorRecomendacionFundamentadaIA().construirAlcanceExperimental(r.contexto(),candidatas,Set.of());
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andExpect(req->{
            var cuerpo=((MockClientHttpRequest)req).getBodyAsString(); int bytes=cuerpo.getBytes(StandardCharsets.UTF_8).length;
            System.out.println("PASO135 subset con resumen: "+bytes+" bytes"); assertTrue(bytes<=16384);
            var solicitud=mapper.readTree(cuerpo); var datos=mapper.readTree(solicitud.get("messages").get(1).get("content").asString());
            var base=ValidadorRecomendacionesIA.contexto(r.contexto());
            assertEquals(mapper.readTree(mapper.writeValueAsString(ContratoOllamaV2.hechos(base))),datos.get("hechosDisponibles"));
            assertEquals(mapper.valueToTree(ContratoOllamaV2.esquemaConAlcance(base,candidatas)),solicitud.get("format"));
            assertEquals(1,datos.get("accionesCandidatas").size());
            assertEquals("TR_PLANIFICAR_DESPLAZAMIENTOS",datos.get("accionesCandidatas").get(0).get("id").asString());
            assertEquals("12.345",datos.get("hechosDisponibles").get("/energia/consumoKwhDiario").get("valor").get("valor").toString());
            assertEquals("HOGAR",datos.get("hechosDisponibles").get("/energia/consumoKwhDiario").get("ambito").asString());
            var resumen=datos.get("resumenAmbiental"); assertEquals("VIGENTE",resumen.get("estado").asString());
            assertEquals("PERIMETROS_NO_COMPARABLES",resumen.get("comparabilidad").asString()); assertTrue(resumen.get("focos").isEmpty());
            assertEquals(4,resumen.get("componentes").size()); assertFalse(resumen.has("usuarioId")); assertFalse(resumen.has("referencia"));
            var referencias=resumen.get("referenciasNumericas"); assertEquals(4,referencias.size());
            assertEquals("Energía",referencias.get(0).get("categoria").asString());
            assertEquals("HOGAR_SEGUN_CONTEXTO",referencias.get(0).get("perimetro").asString());
            assertTrue(solicitud.get("messages").get(0).get("content").asString().contains("no atribuyas energía del hogar"));
        }).andRespond(withSuccess(envoltura(original),MediaType.APPLICATION_JSON));
        var resultado=cliente.generarV2ConAlcanceInterno(r,candidatas,Set.of());
        assertEquals(original,resultado.propuestaJson()); assertEquals(ResultadoEvaluacionRecomendacionIADTO.Estado.ACEPTADA_ESTRUCTURALMENTE,resultado.evaluacion().estado());
        assertEquals(List.of("Transporte"),resultado.propuesta().resultados().stream().map(Propuesta::categoria).toList());
        servidor.verify();
    }
    @Test void sinResumenConservaExactamenteLaSolicitudAnterior() {
        var r=h.constructor.construir(68L); var anterior=new ContextoRecomendacionesIAService.Resultado(r.contexto(),r.categoriasIncompletas(),r.observaciones(),r.reglasInterpretacion());
        var candidatas=permitidas(anterior); var alcance=new ValidadorRecomendacionFundamentadaIA().construirAlcanceExperimental(r.contexto(),candidatas,Set.of());
        var datos=ValidadorRecomendacionesIA.contexto(r.contexto());
        var esperada=mapper.valueToTree(Map.of("model","qwen3:4b-instruct","stream",false,"format",ContratoOllamaV2.esquemaConAlcance(datos,candidatas),
                "messages",List.of(Map.of("role","system","content",ContratoOllamaV2.sistemaConAlcance()),Map.of("role","user","content",mapper.writeValueAsString(ContratoOllamaV2.datos(datos,candidatas))))));
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andExpect(req->{
            var cuerpo=((MockClientHttpRequest)req).getBodyAsString(); assertEquals(esperada,mapper.readTree(cuerpo));
            System.out.println("PASO135 subset sin resumen: "+cuerpo.getBytes(StandardCharsets.UTF_8).length+" bytes");
        }).andRespond(withSuccess(envoltura(propuesta("Transporte","TR_PLANIFICAR_DESPLAZAMIENTOS")),MediaType.APPLICATION_JSON));
        cliente.generarV2ConAlcanceInterno(anterior,candidatas,Set.of()); servidor.verify();
    }
    @ParameterizedTest @ValueSource(strings={"inexistente","obsoleta","invalida","noVerificable"})
    void noEnvioMagnitudesComoVigentesSiNoLoSon(String caso) {
        switch(caso) { case "inexistente" -> h.huella=null; case "obsoleta" -> h.f.campo("medio").setFechaActualizacion(h.huella.getFechaCalculo().plusHours(1));
            case "invalida" -> h.huella.setCo2Energia(null); default -> h.f.campo("medio").setFechaRegistro(null); }
        var r=h.constructor.construir(68L); var candidatas=permitidas(r);
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andExpect(req->{
            var cuerpo=mapper.readTree(((MockClientHttpRequest)req).getBodyAsString());
            var resumen=mapper.readTree(cuerpo.get("messages").get(1).get("content").asString()).get("resumenAmbiental");
            assertFalse(resumen.has("componentes")); assertFalse(resumen.has("perimetros")); assertTrue(resumen.get("focos").isEmpty());
            assertTrue(resumen.get("referenciasNumericas").isEmpty());
            assertEquals(r.resumenHuella().vigencia().name(),resumen.get("estado").asString());
        }).andRespond(withSuccess(envoltura(propuesta("Transporte","TR_PLANIFICAR_DESPLAZAMIENTOS")),MediaType.APPLICATION_JSON));
        assertEquals(ResultadoEvaluacionRecomendacionIADTO.Estado.ACEPTADA_ESTRUCTURALMENTE,
                cliente.generarV2ConAlcanceInterno(r,candidatas,Set.of()).evaluacion().estado()); servidor.verify();
    }
    @Test void catalogoCompletoNoSeTruncaParaOcultarExcesoDeBytes() {
        var r=h.constructor.construir(68L); var candidatas=catalogo.compatibles(r.contexto(),Set.of());
        String completa=cliente.solicitudV2(r,candidatas,null); int bytes=completa.getBytes(StandardCharsets.UTF_8).length;
        System.out.println("PASO135 catalogo completo con resumen: "+bytes+" bytes"); assertTrue(bytes>16384);
        assertEquals(candidatas.size(),mapper.readTree(mapper.readTree(completa).get("messages").get(1).get("content").asString()).get("accionesCandidatas").size());
        var e=assertThrows(com.huellago.backend.exceptions.OllamaClientException.class,()->cliente.generarV2ParaPruebasSinteticas(r,Set.of()));
        assertEquals(com.huellago.backend.exceptions.OllamaClientException.Tipo.CONTEXTO_INVALIDO,e.getTipo()); servidor.verify();
    }
    @Test void resumenNoPermiteAccionFueraDeSubconjunto() {
        var r=h.constructor.construir(68L);
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andRespond(withSuccess(
                envoltura(propuesta("Transporte","TR_EXPLORAR_ALTERNATIVAS_AUTO")),MediaType.APPLICATION_JSON));
        assertNotEquals(ResultadoEvaluacionRecomendacionIADTO.Estado.ACEPTADA_ESTRUCTURALMENTE,
                cliente.generarV2ConAlcanceInterno(r,permitidas(r),Set.of()).evaluacion().estado()); servidor.verify();
    }
    @Test void v1NoRecibeResumenNiCambiaSuContrato() {
        var r=h.constructor.construir(68L);
        var recomendaciones=r.categoriasValidas().stream().map(c->new RecomendacionGeneradaIADTO(c,"Titulo sintetico","Descripcion sintetica","Motivo sintetico")).toList();
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andExpect(req->{
            var cuerpo=mapper.readTree(((MockClientHttpRequest)req).getBodyAsString());
            assertEquals(mapper.readTree(mapper.writeValueAsString(ValidadorRecomendacionesIA.contexto(r.contexto()))),mapper.readTree(cuerpo.get("messages").get(1).get("content").asString()));
            assertFalse(cuerpo.toString().contains("resumenAmbiental"));
        }).andRespond(withSuccess(envoltura(mapper.writeValueAsString(Map.of("recomendaciones",recomendaciones))),MediaType.APPLICATION_JSON));
        assertEquals(4,cliente.generarDesdeContexto(r).size()); servidor.verify();
    }
}
