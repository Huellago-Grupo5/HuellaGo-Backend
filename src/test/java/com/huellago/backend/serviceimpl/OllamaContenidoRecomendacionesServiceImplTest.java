package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.services.*;
import com.huellago.backend.exceptions.OllamaClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** SOLO HTTP simulado. Los textos ficticios de estas pruebas no son resultados reales de Qwen3. */
class OllamaContenidoRecomendacionesServiceImplTest {
    final JsonMapper mapper = JsonMapper.builder().build();
    final CatalogoAccionesAmbientalesServiceImpl catalogo = new CatalogoAccionesAmbientalesServiceImpl();
    final ContextoRecomendacionesIADTO completo = new ContextoRecomendacionesIADTO(
            new ContextoRecomendacionesIADTO.Transporte("Auto", new BigDecimal("100"), 5),
            new ContextoRecomendacionesIADTO.Energia("Casa", 3, "Electricidad", new BigDecimal("12.345")),
            new ContextoRecomendacionesIADTO.Alimentacion("Vegetariana"),
            new ContextoRecomendacionesIADTO.Residuos("Medio", false));
    MockRestServiceServer servidor;
    OllamaContenidoRecomendacionesServiceImpl cliente;

    @BeforeEach void preparar() {
        var b = RestClient.builder().baseUrl("http://localhost:11434");
        servidor = MockRestServiceServer.bindTo(b).build();
        cliente = new OllamaContenidoRecomendacionesServiceImpl(b.build(), new OllamaConfiguration.Ajustes(
                URI.create("http://localhost:11434"), "qwen3:4b-instruct", Duration.ofSeconds(3), Duration.ofSeconds(120), 65536), catalogo);
    }

    @Test void cuatroCategoriasSolicitudCompactaYResultadoPendienteSinMutaciones() throws Exception {
        var s = seleccion(completo, false); String original = mapper.writeValueAsString(s);
        String contenido = respuesta(s);
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(req -> {
                    String body = ((MockClientHttpRequest)req).getBodyAsString(StandardCharsets.UTF_8);
                    assertTrue(body.getBytes(StandardCharsets.UTF_8).length <= 16384);
                    assertNull(req.getHeaders().getFirst("Authorization"));
                    var n = mapper.readTree(body);
                    assertEquals("qwen3:4b-instruct", n.get("model").asString()); assertFalse(n.get("stream").asBoolean());
                    var entries = mapper.readTree(n.get("messages").get(1).get("content").asString()).get("selecciones");
                    assertEquals(4, entries.size());
                    var energia = entries.get(1).get("datosAmbientales");
                    assertEquals(0, new BigDecimal("12.345").compareTo(energia.get("consumoKwhDiario").decimalValue()));
                    assertTrue(n.get("messages").get(0).get("content").asString().contains("HOGAR"));
                    assertTrue(body.contains("BORRADOR")); assertTrue(body.contains("restricciones"));
                    assertFalse(body.contains("correo")); assertFalse(body.contains("usuarioId")); assertFalse(body.contains("password"));
                    var ramas = n.get("format").get("properties").get("contenidos").get("items").get("oneOf");
                    assertEquals(4, ramas.size());
                    for (var r : ramas) {
                        assertEquals(5, r.get("required").size());
                        assertEquals(5, r.get("properties").size());
                        assertEquals("string", r.get("properties").get("titulo").get("type").asString());
                        assertEquals(160, r.get("properties").get("titulo").get("maxLength").asInt());
                        assertTrue(r.get("properties").get("accion").has("const"));
                    }
                    Files.createDirectories(Path.of("target/paso94"));
                    Files.writeString(Path.of("target/paso94/solicitud-simulada.json"), body, StandardCharsets.UTF_8);
                }).andRespond(withSuccess(envoltura(contenido), MediaType.APPLICATION_JSON));
        var r = generar(completo,s);
        assertEquals(4, r.contenidos().size()); assertEquals(PENDIENTE_REVISION, r.estado()); assertFalse(r.publicable());
        assertSame(s.propuesta(), r.seleccionOriginal()); assertEquals(original, mapper.writeValueAsString(s));
        assertTrue(r.traza().origen().contains("NO_CERTIFICA_INFERENCIA_REAL")); assertEquals(64,r.traza().sha256Solicitud().length());
        for (int i=0;i<4;i++) assertSame(s.propuesta().resultados().get(i).accion(),r.contenidos().get(i).accion());
        Files.writeString(Path.of("target/paso94/respuesta-simulada.json"), contenido, StandardCharsets.UTF_8);
        Files.writeString(Path.of("target/paso94/traza-prueba.json"), mapper.writeValueAsString(Map.of(
                "tipoEjecucion", "HTTP_SIMULADO_NO_INFERENCIA_REAL", "traza", r.traza())), StandardCharsets.UTF_8);
        servidor.verify();
    }

    @Test void categoriasParcialesYParametrosPersonalizadosConservados() {
        var c = new ContextoRecomendacionesIADTO(completo.transporte(),null,null,completo.residuos());
        var s = seleccion(c,true); responder(respuesta(s));
        var r=generar(c,s); assertEquals(2,r.contenidos().size());
        assertFalse(r.contenidos().getFirst().accion().parametros().isEmpty()); servidor.verify();
    }

    @Test void abstencionesSinLlamadaHttp() {
        var ps = ValidadorRecomendacionesIA.contexto(completo).keySet().stream().map(c -> new Propuesta(c,
                Decision.ABSTENERSE,null,List.of(),null,null,null,List.of(),MotivoAbstencion.NO_VERIFICABLE)).toList();
        var s = resultado(completo,new RecomendacionFundamentadaIADTO("2",ps));
        var r=generar(completo,s); assertTrue(r.contenidos().isEmpty()); assertFalse(r.publicable());
        assertEquals("SIN_PROPUESTAS_SIN_HTTP",r.traza().origen()); assertNull(r.traza().sha256Solicitud()); servidor.verify();
    }

    @Test void sinCandidatasSinSeleccionNoFabricaContenido() {
        var realizadas = catalogo.compatibles(completo,Set.of()).stream().map(c -> new AccionAmbientalAdmisible.Referencia(
                c.definicion().id(),c.definicion().version())).collect(java.util.stream.Collectors.toSet());
        var s = new ResultadoGeneracionFundamentadaIADTO(null,null,new ResultadoEvaluacionRecomendacionIADTO(PENDIENTE_REVISION,List.of(),List.of()));
        var r=cliente.generarParaPruebasSinteticas(completo,s,realizadas);
        assertNull(r.seleccionOriginal()); assertTrue(r.contenidos().isEmpty()); assertFalse(r.publicable()); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings={"titulo","descripcion","motivo"})
    void textoAusenteSeRechazaSinReparacion(String campo) {
        var s=seleccion(completo,false); var n=mapper.readTree(respuesta(s));
        ((ObjectNode)n.get("contenidos").get(0)).remove(campo);
        responder(mapper.writeValueAsString(n)); invalida(s); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings={"titulo","descripcion","motivo"})
    void textoNuloSeRechaza(String campo) {
        var s=seleccion(completo,false); var n=mapper.readTree(respuesta(s));
        ((ObjectNode)n.get("contenidos").get(0)).putNull(campo);
        responder(mapper.writeValueAsString(n)); invalida(s); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings={"titulo","descripcion","motivo"})
    void textoBlancoSeRechaza(String campo) {
        var s=seleccion(completo,false); var n=mapper.readTree(respuesta(s));
        ((ObjectNode)n.get("contenidos").get(0)).put(campo,"   ");
        responder(mapper.writeValueAsString(n)); invalida(s); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings={"titulo","descripcion","motivo"})
    void textoExcesivoSeRechaza(String campo) {
        var s=seleccion(completo,false); var n=mapper.readTree(respuesta(s));
        ((ObjectNode)n.get("contenidos").get(0)).put(campo,"a".repeat(campo.equals("titulo")?161:1001));
        responder(mapper.writeValueAsString(n)); invalida(s); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings={"id","version","categoria","parametros"})
    void identidadOParametrosAlteradosSeRechazan(String campo) {
        var s=seleccion(completo,false); var n=mapper.readTree(respuesta(s)); var primero=(ObjectNode)n.get("contenidos").get(0);
        if(campo.equals("categoria")) primero.put(campo,"Residuos");
        else if(campo.equals("parametros")) ((ObjectNode)primero.get("accion")).putObject("parametros").put("intruso","x");
        else ((ObjectNode)primero.get("accion")).put(campo,"ALTERADO");
        responder(mapper.writeValueAsString(n)); invalida(s); servidor.verify();
    }

    @Test void duplicadosRechazados() {
        var s=seleccion(completo,false); var n=mapper.readTree(respuesta(s));
        ((tools.jackson.databind.node.ArrayNode)n.get("contenidos")).set(1,n.get("contenidos").get(0));
        responder(mapper.writeValueAsString(n)); invalida(s); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings={"{}","null","[]","{mal","{\"contenidos\":[]}","{\"contenidos\":[],\"extra\":true}"})
    void respuestaIncompletaOInvalida(String json) { var s=seleccion(completo,false); responder(json); invalida(s); servidor.verify(); }

    @Test void seleccionInvalidaNoSeAceptaAunqueEtiquetaDigaAceptada() {
        var s=seleccion(completo,false); var ps=new ArrayList<>(s.propuesta().resultados()); var p=ps.get(0);
        ps.set(0,new Propuesta(p.categoria(),p.decision(),new Accion("NO_EXISTE","1",Map.of()),p.evidencias(),null,null,null,List.of(),null));
        var contrato=new RecomendacionFundamentadaIADTO("2",ps);
        var falsa=new ResultadoGeneracionFundamentadaIADTO(mapper.writeValueAsString(contrato),contrato,s.evaluacion());
        assertEquals(OllamaClientException.Tipo.CONTEXTO_INVALIDO,assertThrows(OllamaClientException.class,()->generar(completo,falsa)).getTipo()); servidor.verify();
    }

    @Test void evidenciaAlteradaRechazadaAntesDeHttp() {
        var s=seleccion(completo,false); var ps=new ArrayList<>(s.propuesta().resultados()); var p=ps.get(0);
        var ev=new ArrayList<>(p.evidencias()); var e=ev.get(0); ev.set(0,new Evidencia(e.ruta(),new Texto("inventado"),e.ambito()));
        ps.set(0,new Propuesta(p.categoria(),p.decision(),p.accion(),ev,null,null,null,List.of(),null));
        var contrato=new RecomendacionFundamentadaIADTO("2",ps);
        var falsa=new ResultadoGeneracionFundamentadaIADTO(mapper.writeValueAsString(contrato),contrato,s.evaluacion());
        assertThrows(OllamaClientException.class,()->generar(completo,falsa)); servidor.verify();
    }

    @Test void jsonOriginalDistintoNoEsIgnorado() {
        var s=seleccion(completo,false);
        assertThrows(OllamaClientException.class,()->generar(completo,new ResultadoGeneracionFundamentadaIADTO("{}",s.propuesta(),s.evaluacion()))); servidor.verify();
    }

    @ParameterizedTest @ValueSource(ints={400,429,500,503})
    void errorHttpExplicito(int status) {
        var s=seleccion(completo,false);
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andRespond(withStatus(HttpStatusCode.valueOf(status)));
        var e=assertThrows(OllamaClientException.class,()->generar(completo,s));
        assertEquals(OllamaClientException.Tipo.ERROR_HTTP,e.getTipo()); assertEquals(status,e.getEstadoHttp()); servidor.verify();
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void timeoutYConexionSinFallback(boolean timeout) {
        var s=seleccion(completo,false);
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andRespond(withException(
                timeout?new java.net.SocketTimeoutException("prueba"):new java.net.ConnectException("prueba")));
        var e=assertThrows(OllamaClientException.class,()->generar(completo,s));
        assertEquals(timeout?OllamaClientException.Tipo.TIEMPO_AGOTADO:OllamaClientException.Tipo.NO_DISPONIBLE,e.getTipo()); servidor.verify();
    }

    @Test void respuestaExcesivaRechazada() {
        var s=seleccion(completo,false);
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andRespond(withSuccess("a".repeat(65537),MediaType.APPLICATION_JSON));
        invalida(s); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings={"", "{}", "{\"done\":false}",
            "{\"model\":\"qwen3:4b-instruct\",\"done\":true,\"message\":{\"role\":\"assistant\",\"content\":\"\"}}"})
    void envolturaHttpIncompletaSeRechaza(String body) {
        var s=seleccion(completo,false);
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        invalida(s); servidor.verify();
    }

    @Test void limiteExactoDeLongitudesAdmitido() {
        var s=seleccion(completo,false); var n=mapper.readTree(respuesta(s)); var item=(ObjectNode)n.get("contenidos").get(0);
        item.put("titulo","a".repeat(160)); item.put("descripcion","a".repeat(1000)); item.put("motivo","a".repeat(1000));
        responder(mapper.writeValueAsString(n)); assertEquals(160,generar(completo,s).contenidos().get(0).titulo().length()); servidor.verify();
    }

    @Test void jsonConCamposDuplicadosNoSeRepara() {
        var s=seleccion(completo,false);
        responder(respuesta(s).replace("\"contenidos\":", "\"contenidos\":[],\"contenidos\":"));
        invalida(s); servidor.verify();
    }

    @Test void accionYaRealizadaNoSeRedacta() {
        var s=seleccion(completo,false); var a=s.propuesta().resultados().get(0).accion();
        assertThrows(OllamaClientException.class,()->cliente.generarParaPruebasSinteticas(completo,s,
                Set.of(new AccionAmbientalAdmisible.Referencia(a.id(),a.version())))); servidor.verify();
    }

    @Test void entradaExcesivaSeRechazaAntesDeHttp() {
        var s=seleccion(completo,false);
        var grande=new CatalogoAccionesAmbientalesService() {
            public List<Candidata> listarBorradores() { return catalogo.listarBorradores(); }
            public List<Candidata> compatibles(ContextoRecomendacionesIADTO c,Set<AccionAmbientalAdmisible.Referencia> realizadas) {
                return catalogo.compatibles(c,realizadas).stream().map(a -> new Candidata(a.definicion(),a.objetivo(),
                        "a".repeat(16385),a.restricciones(),a.estadoRevision())).toList();
            }
        };
        var b=RestClient.builder().baseUrl("http://localhost:11434"); var sinHttp=MockRestServiceServer.bindTo(b).build();
        var aislado=new OllamaContenidoRecomendacionesServiceImpl(b.build(),new OllamaConfiguration.Ajustes(
                URI.create("http://localhost:11434"),"qwen3:4b-instruct",Duration.ofSeconds(3),Duration.ofSeconds(120),65536),grande);
        assertEquals(OllamaClientException.Tipo.CONTEXTO_INVALIDO,assertThrows(OllamaClientException.class,
                ()->aislado.generarParaPruebasSinteticas(completo,s,Set.of())).getTipo()); sinHttp.verify();
    }

    @Test void contenidoContradictorioSiguePendienteNoCertificado() {
        var s=seleccion(completo,false); var n=mapper.readTree(respuesta(s));
        ((ObjectNode)n.get("contenidos").get(2)).put("descripcion","Debes comer carne obligatoriamente.");
        responder(mapper.writeValueAsString(n)); var r=generar(completo,s);
        assertEquals(PENDIENTE_REVISION,r.estado()); assertFalse(r.publicable());
        assertTrue(r.contenidos().get(2).descripcion().contains("carne")); servidor.verify();
    }

    private ResultadoContenidoRecomendacionesIADTO generar(ContextoRecomendacionesIADTO c,ResultadoGeneracionFundamentadaIADTO s) {
        return cliente.generarParaPruebasSinteticas(c,s,Set.of());
    }
    private void invalida(ResultadoGeneracionFundamentadaIADTO s) {
        assertEquals(OllamaClientException.Tipo.RESPUESTA_INVALIDA,assertThrows(OllamaClientException.class,()->generar(completo,s)).getTipo());
    }
    private ResultadoGeneracionFundamentadaIADTO seleccion(ContextoRecomendacionesIADTO c,boolean parametros) {
        var hechos=ContratoOllamaV2.hechos(ValidadorRecomendacionesIA.contexto(c));
        var disponibles=catalogo.compatibles(c,Set.of()); var ps=new ArrayList<Propuesta>();
        for(String categoria:ValidadorRecomendacionesIA.contexto(c).keySet()) {
            var a=disponibles.stream().filter(d->d.definicion().categoria().equals(categoria)).findFirst().orElseThrow().definicion();
            var evidencias=a.evidenciasRequeridas().stream().sorted().map(r->{
                var h=mapper.readTree(mapper.writeValueAsString(hechos.get(r)));
                return new Evidencia(r,mapper.readValue(mapper.writeValueAsString(h.get("valor")),Valor.class),Ambito.valueOf(h.get("ambito").asString()));
            }).toList();
            Map<String,Valor> params=parametros?Map.of("enfoque",a.parametros().get("enfoque").valoresPermitidos().iterator().next()):Map.of();
            ps.add(new Propuesta(categoria,Decision.PROPONER,new Accion(a.id(),a.version(),params),evidencias,null,null,null,List.of(),null));
        }
        return resultado(c,new RecomendacionFundamentadaIADTO("2",ps));
    }
    private ResultadoGeneracionFundamentadaIADTO resultado(ContextoRecomendacionesIADTO c,RecomendacionFundamentadaIADTO p) {
        String json=mapper.writeValueAsString(p);
        var eval=new ValidadorRecomendacionFundamentadaIA().validar(json,c,catalogo.compatibles(c,Set.of()).stream().map(CatalogoAccionesAmbientalesService.Candidata::definicion).toList(),Set.of());
        assertEquals(ACEPTADA_ESTRUCTURALMENTE,eval.estado()); return new ResultadoGeneracionFundamentadaIADTO(json,p,eval);
    }
    private String respuesta(ResultadoGeneracionFundamentadaIADTO s) {
        // Ficción exclusiva del transporte simulado; producción no incorpora estos textos.
        return mapper.writeValueAsString(Map.of("contenidos",s.propuesta().resultados().stream().map(p->Map.of(
                "categoria",p.categoria(),"accion",p.accion(),"titulo","Revisa una oportunidad en "+p.categoria(),
                "descripcion","Examina una mejora viable dentro de las restricciones de la acción seleccionada.",
                "motivo","La selección considera únicamente los hábitos registrados; faltan detalles para concretar más.")).toList()));
    }
    private String envoltura(String json) { return mapper.writeValueAsString(Map.of("model","qwen3:4b-instruct","done",true,"message",Map.of("role","assistant","content",json))); }
    private void responder(String json) { servidor.expect(requestTo("http://localhost:11434/api/chat")).andRespond(withSuccess(envoltura(json),MediaType.APPLICATION_JSON)); }
}
