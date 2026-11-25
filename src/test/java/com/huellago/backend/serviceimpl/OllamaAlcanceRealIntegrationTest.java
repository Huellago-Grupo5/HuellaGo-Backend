package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.HabitoRepository;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.*;
import static com.huellago.backend.dtos.ResultadoPresentacionRecomendacionesIADTO.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** PASO 111: A/B HTTP REAL, C local. Datos ficticios y repositorio Mockito.
 * No usa redacción libre, Spring Boot, Hibernate, SQL ni persistencia.
 * Nunca reintentar una ronda que haya comenzado: las evidencias usan CREATE_NEW. */
@EnabledIfEnvironmentVariable(named = "HUELLAGO_OLLAMA_REAL_TEST", matches = "true")
@Execution(ExecutionMode.SAME_THREAD)
class OllamaAlcanceRealIntegrationTest {
    private static final AtomicInteger TOTAL_HTTP = new AtomicInteger();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final CatalogoAccionesAmbientalesServiceImpl catalogo = new CatalogoAccionesAmbientalesServiceImpl();
    private final Path carpeta = Path.of("target/paso111");

    @ParameterizedTest @ValueSource(strings = {"A", "B", "C"})
    void seleccionConAlcanceRealYVistaPreviaLocal(String escenario) throws Exception {
        Files.createDirectories(carpeta);
        guardar(escenario + "-inicio.json", Map.of("inicio", Instant.now().toString(),
                "origen", escenario.equals("C") ? "LOCAL_SIN_INFERENCIA" : "HTTP_REAL_DATOS_SINTETICOS"));
        List<Habito> habitos = habitos(escenario);
        HabitoRepository repo = mock(HabitoRepository.class);
        when(repo.findByUsuario_Id(11100L)).thenReturn(habitos);
        var preparado = new ContextoRecomendacionesIAServiceImpl(repo).construir(11100L);
        var contexto = preparado.contexto();
        String antes = mapper.writeValueAsString(preparado);
        var canonicas = catalogo.compatibles(contexto, Set.of());
        Set<String> ids = switch (escenario) {
            case "A" -> Set.of("TR_PLANIFICAR_DESPLAZAMIENTOS", "TR_EXPLORAR_ALTERNATIVAS_AUTO");
            case "B" -> Set.of("EN_REVISAR_REGISTRO_HOGAR");
            default -> Set.of();
        };
        var permitidas = canonicas.stream().filter(c -> ids.contains(c.definicion().id())).toList();
        assertEquals(ids, permitidas.stream().map(c -> c.definicion().id()).collect(Collectors.toSet()));
        var alcance = new ValidadorRecomendacionFundamentadaIA().construirAlcanceExperimental(contexto, permitidas, Set.of());
        guardar(escenario + "-contexto.json", preparado);
        guardar(escenario + "-canonicas.json", canonicas);
        guardar(escenario + "-permitidas.json", permitidas);
        guardar(escenario + "-alcance.json", alcance);
        Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("escenario", escenario); registro.put("llamadasHttp", 0);
        registro.put("revisionSemantica", "PENDIENTE_EVALUACION_HUMANA_NO_CERTIFICA_CIENCIA");
        var ajustes = new OllamaConfiguration.Ajustes(URI.create("http://localhost:11434"), "qwen3:4b-instruct",
                Duration.ofSeconds(3), Duration.ofSeconds(120), 65536);
        var http = new OllamaConfiguration().ollamaRestClient(ajustes).mutate().requestInterceptor((req, body, execution) -> {
            assertNotEquals("C", escenario, "C no debe realizar HTTP");
            assertEquals(0, registro.get("llamadasHttp"), "No reintentar");
            assertTrue(body.length <= 16384, "Solicitud fuera del límite");
            assertEquals(HttpMethod.POST, req.getMethod());
            assertEquals("http://localhost:11434/api/chat", req.getURI().toString());
            assertNull(req.getHeaders().getFirst("Authorization"));
            Files.write(carpeta.resolve(escenario + "-solicitud-http.json"), body, StandardOpenOption.CREATE_NEW);
            registro.put("bytesSolicitud", body.length);
            var solicitud = mapper.readTree(body);
            assertEquals("qwen3:4b-instruct", solicitud.get("model").asString()); assertFalse(solicitud.get("stream").asBoolean());
            var datos = mapper.readTree(solicitud.get("messages").get(1).get("content").asString());
            assertEquals(mapper.readTree(mapper.writeValueAsString(ValidadorRecomendacionesIA.contexto(contexto))), datos.get("contexto"));
            assertEquals(mapper.readTree(mapper.writeValueAsString(ContratoOllamaV2.datos(
                    ValidadorRecomendacionesIA.contexto(contexto), permitidas))), datos);
            assertEquals(alcance.seleccionables().size(), solicitud.get("format").get("properties").get("resultados").get("minItems").asInt());
            assertTrue(TOTAL_HTTP.incrementAndGet() <= 2, "Máximo dos llamadas reales");
            registro.put("llamadasHttp", 1);
            long t = System.nanoTime();
            ClientHttpResponse respuesta;
            try { respuesta = execution.execute(req, body); }
            catch (IOException | RuntimeException e) { registro.put("duracionHttpMs", ms(t)); throw e; }
            try {
                registro.put("httpStatus", respuesta.getStatusCode().value());
                byte[] bytes = respuesta.getBody().readNBytes(ajustes.maxRespuestaBytes() + 1);
                registro.put("duracionHttpMs", ms(t)); registro.put("bytesRespuesta", bytes.length);
                registro.put("capturaCompletaDentroDelLimite", bytes.length <= ajustes.maxRespuestaBytes());
                Files.write(carpeta.resolve(escenario + "-respuesta-http.json"), bytes, StandardOpenOption.CREATE_NEW);
                InputStream copia = new ByteArrayInputStream(bytes);
                return new ClientHttpResponse() {
                    public HttpStatusCode getStatusCode() throws IOException { return respuesta.getStatusCode(); }
                    public String getStatusText() throws IOException { return respuesta.getStatusText(); }
                    public HttpHeaders getHeaders() { return respuesta.getHeaders(); }
                    public InputStream getBody() { return copia; }
                    public void close() { respuesta.close(); }
                };
            } catch (IOException | RuntimeException | Error e) { respuesta.close(); throw e; }
        }).build();
        long inicio = System.nanoTime();
        try {
            var selector = new OllamaRecomendacionesServiceImpl(http, ajustes, catalogo);
            var orquestador = new OrquestadorRecomendacionesIAServiceImpl(selector, catalogo, new PresentacionRecomendacionesIAServiceImpl());
            var resultado = orquestador.orquestarParaPruebasSinteticas(preparado, permitidas, Set.of());
            guardar(escenario + "-seleccion.json", resultado.seleccion());
            guardar(escenario + "-validacion.json", resultado.seleccion().evaluacion());
            guardar(escenario + "-presentacion.json", resultado.presentacion());
            guardar(escenario + "-resultado-orquestador.json", resultado);
            if (resultado.seleccion().propuestaJson() != null)
                Files.writeString(carpeta.resolve(escenario + "-json-original-qwen3.json"), resultado.seleccion().propuestaJson(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            assertEquals(alcance, resultado.alcance()); assertFalse(resultado.publicable());
            assertFalse(resultado.seleccion().publicable()); assertFalse(resultado.presentacion().publicable());
            registro.put("estadoPresentacion", resultado.presentacion().estado());
            registro.put("estadoValidador", resultado.seleccion().evaluacion().estado());
            registro.put("motivosValidador", resultado.seleccion().evaluacion().motivos());
            registro.put("publicable", false);
            if (escenario.equals("C")) {
                assertEquals(0, registro.get("llamadasHttp"));
                assertEquals(Estado.SIN_PROPUESTA, resultado.presentacion().estado());
                assertNull(resultado.seleccion().propuesta()); assertNull(resultado.seleccion().propuestaJson());
                assertTrue(resultado.categoriasRespondidas().isEmpty());
                assertEquals(alcance.completas(), alcance.excluidasSinPermitidas());
                assertTrue(resultado.presentacion().resultados().isEmpty());
                registro.put("origen", "SIN_PROPUESTA_SIN_DECISION_DE_QWEN3");
            } else {
                assertEquals(1, registro.get("llamadasHttp")); assertEquals(200, registro.get("httpStatus"));
                assertEquals(ACEPTADA_ESTRUCTURALMENTE, resultado.seleccion().evaluacion().estado(),
                        () -> "Selección rechazada: " + resultado.seleccion().evaluacion());
                assertEquals(Estado.SELECCION_ACEPTADA, resultado.presentacion().estado());
                assertEquals(alcance.seleccionables(), resultado.categoriasRespondidas());
                assertEquals(1, resultado.seleccion().propuesta().resultados().size());
                var p = resultado.seleccion().propuesta().resultados().getFirst();
                var d = resultado.presentacion().resultados().getFirst();
                assertSame(p, d.seleccionOriginal());
                registro.put("decisionReal", p); registro.put("hogarComprobadoEnPropuestaReal", false);
                if (p.decision() == Decision.ABSTENERSE) {
                    assertNull(p.accion()); assertNull(d.presentacion()); assertEquals(EstadoDetalle.ABSTENCION_V2, d.estado());
                } else {
                    assertTrue(permitidas.stream().anyMatch(c -> c.definicion().id().equals(p.accion().id()) && c.definicion().version().equals(p.accion().version())));
                    assertEquals(EstadoDetalle.VISTA_PREVIA_BORRADOR, d.estado());
                    var v = d.presentacion(); assertFalse(v.publicable()); assertEquals(Revision.BORRADOR, v.estado());
                    assertEquals(PENDIENTE_REVISION, v.revisionHumana());
                    var enfoque = p.accion().parametros().get("enfoque");
                    assertEquals(enfoque == null ? "SIN_ENFOQUE" : ((Texto)enfoque).valor(), v.enfoque());
                    assertEquals(enfoque != null, v.enfoqueSeleccionado());
                    if (escenario.equals("B")) {
                        var evidencia = p.evidencias().stream().filter(e -> e.ruta().equals("/energia/consumoKwhDiario")).findFirst().orElseThrow();
                        assertEquals(Ambito.HOGAR, evidencia.ambito());
                        assertEquals(0, new BigDecimal("12.345").compareTo(((Numero)evidencia.valor()).valor()));
                        registro.put("hogarComprobadoEnPropuestaReal", true);
                    }
                    for (var e : p.evidencias()) assertEquals(e.ruta().equals("/energia/consumoKwhDiario") ? Ambito.HOGAR : Ambito.NO_APLICA, e.ambito());
                }
            }
            registro.put("resultadoTecnico", "APROBADO_NO_CERTIFICA_CALIDAD_AMBIENTAL");
        } catch (RuntimeException | AssertionError e) {
            registro.put("resultadoTecnico", "FALLO_SIN_REINTENTO_NI_REPARACION");
            registro.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        } finally {
            registro.put("duracionOrquestacionMs", ms(inicio));
            registro.put("contextoSinCambios", antes.equals(mapper.writeValueAsString(preparado)));
            registro.put("catalogoSoloBorradores", catalogo.listarBorradores().stream().allMatch(c -> c.estadoRevision().name().equals("BORRADOR")));
            guardar(escenario + "-resumen.json", registro);
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(registro));
            verify(repo).findByUsuario_Id(11100L); verifyNoMoreInteractions(repo);
            assertEquals(antes, mapper.writeValueAsString(preparado));
        }
    }
    private long ms(long t) { return Duration.ofNanos(System.nanoTime() - t).toMillis(); }
    private void guardar(String nombre, Object valor) throws IOException {
        Files.writeString(carpeta.resolve(nombre), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(valor), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
    private List<Habito> habitos(String escenario) {
        List<Habito> h = new ArrayList<>();
        if (!escenario.equals("B")) {
            agregar(h, "Transporte", "medio", "0", "Auto"); agregar(h, "Transporte", "kmSemana", "100", "km/semana");
            agregar(h, "Transporte", "diasSemana", "5", "dias/semana");
        }
        agregar(h, "Energía", "vivienda", "0", "Casa"); agregar(h, "Energía", "personas", "3", "personas");
        agregar(h, "Energía", "fuente", "0", "Electricidad"); agregar(h, "Energía", "consumoKwhDiario", "12.345", "kWh/dia");
        if (escenario.equals("B")) agregar(h, "Alimentación", "tipo", "0", "Vegetariana");
        return h;
    }
    private void agregar(List<Habito> lista, String categoria, String nombre, String valor, String unidad) {
        var usuario = new Usuario(); usuario.setId(11100L); // Identidad ficticia, solo repositorio Mockito.
        var cat = new CategoriaHabito(); cat.setNombre(categoria);
        var h = new Habito(); h.setId((long)lista.size() + 1); h.setUsuario(usuario); h.setCategoria(cat);
        h.setNombre(nombre); h.setValor(new BigDecimal(valor)); h.setUnidad(unidad);
        h.setFechaRegistro(LocalDateTime.of(2026, 11, 8, 12, 0)); h.setFechaActualizacion(h.getFechaRegistro()); lista.add(h);
    }
}
