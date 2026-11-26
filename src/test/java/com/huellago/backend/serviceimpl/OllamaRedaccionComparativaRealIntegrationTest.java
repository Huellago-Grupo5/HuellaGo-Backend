package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.exceptions.OllamaClientException;
import com.huellago.backend.services.ContextoRecomendacionesIAService;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.net.URI;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.*;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.Decision.*;
import static org.junit.jupiter.api.Assertions.*;

/** PASO 97: una llamada real de REDACCIÓN por escenario. Sin selección, mocks HTTP, Spring o BD.
 * Reutiliza exactamente las selecciones y contextos originales conservados en target/paso95 y paso96.
 * El éxito técnico no implica calidad semántica, científica ni publicación. */
@EnabledIfEnvironmentVariable(named="HUELLAGO_OLLAMA_REAL_TEST",matches="true")
@Execution(ExecutionMode.SAME_THREAD)
class OllamaRedaccionComparativaRealIntegrationTest {
    private final JsonMapper mapper=JsonMapper.builder().build();
    private final CatalogoAccionesAmbientalesServiceImpl catalogo=new CatalogoAccionesAmbientalesServiceImpl();

    @ParameterizedTest @ValueSource(strings={"A","B"})
    void redactarSeleccionOriginalUnaSolaVez(String escenario) throws Exception {
        Path origen=Path.of("target",escenario.equals("A")?"paso96":"paso95");
        Path carpeta=Path.of("target/paso97/reales"); Files.createDirectories(carpeta);
        Path informe=carpeta.resolve(escenario+"-resultado.json");
        assertFalse(Files.exists(informe),"No sobrescribir ni repetir inferencias reales");
        byte[] bytesContexto=Files.readAllBytes(origen.resolve(escenario+"-contexto.json"));
        byte[] bytesSeleccion=Files.readAllBytes(origen.resolve(escenario+"-seleccion-procesada.json"));
        var resultadoContexto=mapper.readValue(bytesContexto,ContextoRecomendacionesIAService.Resultado.class);
        var contexto=resultadoContexto.contexto();
        var seleccion=mapper.readValue(bytesSeleccion,ResultadoGeneracionFundamentadaIADTO.class);
        assertEquals(ACEPTADA_ESTRUCTURALMENTE,seleccion.evaluacion().estado());
        var respuestaSeleccion=mapper.readTree(Files.readString(origen.resolve(escenario+"-seleccion-respuesta-http.json"),StandardCharsets.UTF_8));
        assertEquals(seleccion.propuestaJson(),respuestaSeleccion.get("message").get("content").asString());
        assertEquals(mapper.readTree(seleccion.propuestaJson()),mapper.readTree(mapper.writeValueAsString(seleccion.propuesta())));
        var candidatas=catalogo.compatibles(contexto,Set.of());
        assertEquals(ACEPTADA_ESTRUCTURALMENTE,new ValidadorRecomendacionFundamentadaIA().validar(seleccion.propuestaJson(),contexto,
                candidatas.stream().map(a->a.definicion()).toList(),Set.of()).estado());
        assertEquals(escenario.equals("A")?"Auto":"Bicicleta",contexto.transporte().medio());
        assertEquals(escenario.equals("A")?100:20,contexto.transporte().kmSemana().intValueExact());
        assertEquals(escenario.equals("A")?5:4,contexto.transporte().diasSemana());
        if(escenario.equals("A")) {
            assertEquals("Vegetariana",contexto.alimentacion().tipo());
            assertEquals(0,new BigDecimal("12.345").compareTo(contexto.energia().consumoKwhDiario()));
            assertFalse(contexto.residuos().reciclas());
        } else { assertNull(contexto.energia()); assertNull(contexto.alimentacion()); assertTrue(contexto.residuos().reciclas()); }
        var propuestas=seleccion.propuesta().resultados().stream().filter(p->p.decision()==PROPONER).toList();
        assertEquals(escenario.equals("A")?4:1,propuestas.size());
        String seleccionAntes=mapper.writeValueAsString(seleccion),contextoAntes=mapper.writeValueAsString(resultadoContexto);
        Map<String,Object> registro=new LinkedHashMap<>();
        registro.put("escenario",escenario); registro.put("tipoEjecucion","SOLO_REDACCION_HTTP_REAL_OLLAMA");
        registro.put("seleccionReutilizadaSinNuevaInferencia",origen.toString());
        registro.put("sha256ArchivoContextoOriginal",sha(bytesContexto)); registro.put("sha256ArchivoSeleccionOriginal",sha(bytesSeleccion));
        registro.put("contextoOriginal",resultadoContexto); registro.put("seleccionOriginal",seleccion);
        registro.put("inicio",Instant.now().toString()); registro.put("llamadasHttp",0);
        var ajustes=new OllamaConfiguration.Ajustes(URI.create("http://localhost:11434"),"qwen3:4b-instruct",Duration.ofSeconds(3),Duration.ofSeconds(120),65536);
        var http=new OllamaConfiguration().ollamaRestClient(ajustes).mutate().requestInterceptor((request,body,execution)->{
            assertEquals(0,registro.get("llamadasHttp"),"Prohibidos reintentos y selección adicional");
            registro.put("bytesSolicitud",body.length);
            Files.write(carpeta.resolve(escenario+"-solicitud.json"),body,StandardOpenOption.CREATE_NEW);
            assertTrue(body.length<=16384); assertEquals(HttpMethod.POST,request.getMethod());
            assertEquals("http://localhost:11434/api/chat",request.getURI().toString());
            assertNull(request.getHeaders().getFirst("Authorization"));
            var enviada=mapper.readTree(body); assertEquals("qwen3:4b-instruct",enviada.get("model").asString()); assertFalse(enviada.get("stream").asBoolean());
            var entrada=mapper.readTree(enviada.get("messages").get(1).get("content").asString());
            assertFalse(entrada.has("accionesCandidatas")); assertEquals(propuestas.size(),entrada.get("selecciones").size());
            for(var a:entrada.get("selecciones")) {
                var p=propuestas.stream().filter(x->x.categoria().equals(a.get("categoria").asString())).findFirst().orElseThrow();
                assertEquals(mapper.readTree(mapper.writeValueAsString(p.accion())),a.get("accion"));
                assertEquals(mapper.readTree(mapper.writeValueAsString(p.evidencias())),a.get("evidencias"));
                // Observador compatible con la solicitud original y la candidata evaluada y descartada.
                var marco=a.has("marcoInterno")?a.get("marcoInterno"):a;
                assertEquals("BORRADOR",marco.get("estadoRevision").asString());
                if(a.has("marcoInterno")) assertFalse(a.has("descripcionCandidata"));
            }
            registro.put("llamadasHttp",1); long t=System.nanoTime();
            ClientHttpResponse respuesta;
            try { respuesta=execution.execute(request,body); }
            catch(IOException|RuntimeException e) { registro.put("duracionHttpMs",ms(t)); throw e; }
            try {
                registro.put("httpStatus",respuesta.getStatusCode().value());
                byte[] bytes=respuesta.getBody().readNBytes(ajustes.maxRespuestaBytes()+1);
                registro.put("duracionHttpMs",ms(t)); registro.put("bytesRespuesta",bytes.length);
                Files.write(carpeta.resolve(escenario+"-respuesta-http.json"),bytes,StandardOpenOption.CREATE_NEW);
                InputStream copia=new ByteArrayInputStream(bytes);
                return new ClientHttpResponse() {
                    public HttpStatusCode getStatusCode() throws IOException { return respuesta.getStatusCode(); }
                    public String getStatusText() throws IOException { return respuesta.getStatusText(); }
                    public HttpHeaders getHeaders() { return respuesta.getHeaders(); }
                    public InputStream getBody() { return copia; }
                    public void close() { respuesta.close(); }
                };
            } catch(IOException|RuntimeException|Error e) { respuesta.close(); throw e; }
        }).build();
        long inicio=System.nanoTime();
        try {
            var r=new OllamaContenidoRecomendacionesServiceImpl(http,ajustes,catalogo).generarParaPruebasSinteticas(contexto,seleccion,Set.of());
            registro.put("resultadoProcesado",r);
            assertEquals(200,registro.get("httpStatus")); assertEquals(1,registro.get("llamadasHttp"));
            assertEquals(PENDIENTE_REVISION,r.estado()); assertFalse(r.publicable()); assertSame(seleccion.propuesta(),r.seleccionOriginal());
            assertEquals(propuestas.size(),r.contenidos().size()); Set<String> vistas=new HashSet<>();
            for(var c:r.contenidos()) {
                assertTrue(vistas.add(c.categoria()));
                var p=propuestas.stream().filter(x->x.categoria().equals(c.categoria())).findFirst().orElseThrow();
                assertEquals(p.accion(),c.accion()); assertFalse(c.titulo().isBlank()); assertTrue(c.titulo().length()<=160);
                assertFalse(c.descripcion().isBlank()); assertTrue(c.descripcion().length()<=1000);
                assertFalse(c.motivo().isBlank()); assertTrue(c.motivo().length()<=1000);
            }
            registro.put("validacionAutomatica","APROBADA_NO_CERTIFICA_SEMANTICA");
        } catch(RuntimeException|AssertionError e) {
            registro.put("error",e.getClass().getSimpleName()+": "+e.getMessage());
            if(e instanceof OllamaClientException o) { registro.put("tipoErrorCliente",o.getTipo()); registro.put("estadoHttpError",o.getEstadoHttp()); }
            throw e;
        } finally {
            registro.put("duracionFaseMs",ms(inicio));
            boolean sinCambios=seleccionAntes.equals(mapper.writeValueAsString(seleccion))&&contextoAntes.equals(mapper.writeValueAsString(resultadoContexto));
            registro.put("seleccionYContextoSinCambios",sinCambios);
            registro.put("catalogoSoloBorradores",catalogo.listarBorradores().stream().allMatch(a->a.estadoRevision().name().equals("BORRADOR")));
            Files.writeString(informe,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(registro),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(registro));
            assertTrue(sinCambios);
        }
    }
    private static long ms(long inicio) { return Duration.ofNanos(System.nanoTime()-inicio).toMillis(); }
    private static String sha(byte[] datos) throws java.security.NoSuchAlgorithmException { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos)); }
}
