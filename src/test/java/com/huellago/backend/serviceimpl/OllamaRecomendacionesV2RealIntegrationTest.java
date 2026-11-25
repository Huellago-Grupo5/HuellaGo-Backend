package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ContextoRecomendacionesIADTO;
import com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.Decision;
import com.huellago.backend.entities.CategoriaHabito;
import com.huellago.backend.entities.Habito;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.exceptions.OllamaClientException;
import com.huellago.backend.repositories.HabitoRepository;
import com.huellago.backend.services.AccionAmbientalAdmisible.Referencia;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.RECHAZADA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** HTTP real opt-in, constructor y catálogo reales, repositorio simulado; sin contexto Spring o DataSource. */
@EnabledIfEnvironmentVariable(named = "HUELLAGO_OLLAMA_REAL_TEST", matches = "true")
@Execution(ExecutionMode.SAME_THREAD)
class OllamaRecomendacionesV2RealIntegrationTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final CatalogoAccionesAmbientalesServiceImpl catalogo = new CatalogoAccionesAmbientalesServiceImpl();

    @ParameterizedTest @ValueSource(strings = {"A", "B", "C"})
    void inferenciaRealV2(String escenario) throws Exception {
        Path carpeta = Path.of("target", "paso93"); Files.createDirectories(carpeta);
        Path informe = carpeta.resolve(escenario + "-resultado.json");
        assertFalse(Files.exists(informe), "No sobrescribir resultados reales anteriores");
        var contexto = construirContexto(escenario);
        var candidatasIniciales = catalogo.compatibles(contexto.contexto(), Set.of());
        Set<Referencia> realizadas = escenario.equals("C") ? candidatasIniciales.stream()
                .map(a -> new Referencia(a.definicion().id(), a.definicion().version())).collect(Collectors.toUnmodifiableSet()) : Set.of();
        var candidatas = catalogo.compatibles(contexto.contexto(), realizadas);
        Set<String> categorias = escenario.equals("A") ? Set.of("Transporte", "Energía", "Alimentación", "Residuos")
                : Set.of("Transporte", "Residuos");
        var datos = ValidadorRecomendacionesIA.contexto(contexto.contexto());
        Map<String,Object> registro = new LinkedHashMap<>();
        registro.put("escenario", escenario); registro.put("contextoSintetico", contexto);
        registro.put("accionesRealizadas", realizadas); registro.put("candidatasCompatibles", candidatas);
        registro.put("cantidadCandidatas", candidatas.size());
        registro.put("modelo", "qwen3:4b-instruct");
        registro.put("inicio", java.time.Instant.now().toString());
        var configuracion = new OllamaConfiguration();
        var ajustes = new OllamaConfiguration.Ajustes(URI.create("http://localhost:11434"), "qwen3:4b-instruct",
                Duration.ofSeconds(3), Duration.ofSeconds(120), 65536);
        // Interceptor observador: execute realiza HTTP real. No MockRestServiceServer ni respuestas prefabricadas.
        var http = configuracion.ollamaRestClient(ajustes).mutate().requestInterceptor((peticion, cuerpo, ejecucion) -> {
            registro.put("bytesSolicitudReal", cuerpo.length);
            Files.write(carpeta.resolve(escenario + "-solicitud.json"), cuerpo, StandardOpenOption.CREATE_NEW);
            assertEquals("http://localhost:11434/api/chat", peticion.getURI().toString());
            assertEquals("POST", peticion.getMethod().name());
            assertNull(peticion.getHeaders().getFirst("Authorization"));
            assertTrue(cuerpo.length <= 16384);
            var enviada = mapper.readTree(cuerpo);
            assertEquals("qwen3:4b-instruct", enviada.get("model").asString());
            assertFalse(enviada.get("stream").asBoolean());
            var entrada = mapper.readTree(enviada.get("messages").get(1).get("content").asString());
            assertEquals(mapper.readTree(mapper.writeValueAsString(datos)), entrada.get("contexto"));
            Set<Referencia> referencias = new HashSet<>();
            for (var a : entrada.get("accionesCandidatas")) {
                referencias.add(new Referencia(a.get("id").asString(), a.get("version").asString()));
                assertEquals("BORRADOR", a.get("estadoRevision").asString());
            }
            assertEquals(candidatas.stream().map(a -> new Referencia(a.definicion().id(), a.definicion().version())).collect(Collectors.toSet()), referencias);
            assertTrue(Collections.disjoint(referencias, realizadas));
            if (escenario.equals("A")) {
                assertEquals("HOGAR", entrada.get("hechosDisponibles").get("/energia/consumoKwhDiario").get("ambito").asString());
                assertEquals("12.345", entrada.get("contexto").get("Energía").get("consumoKwhDiario").toString());
            }
            long inicioHttp = System.nanoTime();
            ClientHttpResponse respuesta = ejecucion.execute(peticion, cuerpo);
            try {
                registro.put("httpStatus", respuesta.getStatusCode().value());
                // Mismo máximo de lectura que el cliente de producción; no altera ni repara el contenido.
                byte[] bytes = respuesta.getBody().readNBytes(ajustes.maxRespuestaBytes() + 1);
                registro.put("duracionHttpMs", Duration.ofNanos(System.nanoTime() - inicioHttp).toMillis());
                registro.put("bytesRespuestaCapturados", bytes.length);
                registro.put("capturaCompletaDentroDelLimite", bytes.length <= ajustes.maxRespuestaBytes());
                Files.write(carpeta.resolve(escenario + "-respuesta-http.json"), bytes, StandardOpenOption.CREATE_NEW);
                InputStream body = new ByteArrayInputStream(bytes);
                return new ClientHttpResponse() {
                    @Override public HttpStatusCode getStatusCode() throws IOException { return respuesta.getStatusCode(); }
                    @Override public String getStatusText() throws IOException { return respuesta.getStatusText(); }
                    @Override public HttpHeaders getHeaders() { return respuesta.getHeaders(); }
                    @Override public InputStream getBody() { return body; }
                    @Override public void close() { respuesta.close(); }
                };
            } catch (IOException | RuntimeException | Error error) {
                respuesta.close(); throw error;
            }
        }).build();
        var cliente = new OllamaRecomendacionesServiceImpl(http, ajustes, catalogo);
        long inicio = System.nanoTime();
        try {
            // Calcula tamaño incluso si producción lo rechaza antes de llegar al interceptor.
            if (!candidatas.isEmpty()) {
            String prevista = mapper.writeValueAsString(Map.of("model", ajustes.modelo(), "stream", false,
                    "format", ContratoOllamaV2.esquema(datos, candidatas), "messages", List.of(
                            Map.of("role", "system", "content", ContratoOllamaV2.sistema()),
                            Map.of("role", "user", "content", mapper.writeValueAsString(ContratoOllamaV2.datos(datos, candidatas))))));
            int bytes = prevista.getBytes(StandardCharsets.UTF_8).length;
            registro.put("bytesSolicitudPrevista", bytes);
            assertTrue(bytes <= 16384, "Solicitud excede 16.384 bytes: " + bytes + "; no se modifica producción");
            } else registro.put("bytesSolicitudPrevista", 0);
            var resultado = cliente.generarV2ParaPruebasSinteticas(contexto, realizadas);
            registro.put("resultadoCliente", resultado);
            registro.put("publicable", resultado.publicable());
            assertFalse(resultado.publicable());
            if (candidatas.isEmpty()) {
                assertNull(resultado.propuesta()); assertNull(resultado.propuestaJson());
                assertFalse(registro.containsKey("bytesSolicitudReal"), "Sin candidatas no debe existir ninguna solicitud HTTP");
                assertTrue(resultado.evaluacion().evaluaciones().isEmpty());
                assertTrue(resultado.evaluacion().motivos().isEmpty());
                assertEquals(com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.PENDIENTE_REVISION,
                        resultado.evaluacion().estado());
                registro.put("origenResultado", "SIN_CANDIDATAS_SIN_INFERENCIA");
            } else {
            assertNotNull(resultado.propuesta(), "Consultar respuesta cruda y evaluación si el contrato resulta inválido");
            assertNotEquals(RECHAZADA, resultado.evaluacion().estado(), resultado.evaluacion().toString());
            var propuestas = resultado.propuesta().resultados();
            assertEquals(categorias.size(), propuestas.size());
            assertEquals(categorias, propuestas.stream().map(p -> p.categoria()).collect(Collectors.toSet()));
            for (var p : propuestas) {
                assertNull(p.titulo()); assertNull(p.descripcion()); assertNull(p.motivo());
                if (p.decision() == Decision.PROPONER) {
                    assertNotNull(p.accion());
                    var ref = new Referencia(p.accion().id(), p.accion().version());
                    assertFalse(realizadas.contains(ref));
                    assertTrue(candidatas.stream().anyMatch(a -> a.definicion().id().equals(ref.id())
                            && a.definicion().version().equals(ref.version()) && a.definicion().categoria().equals(p.categoria())));
                } else assertNull(p.accion());
                if (escenario.equals("C")) assertEquals(Decision.ABSTENERSE, p.decision());
            }
            registro.put("cantidadPropuestasConParametros", propuestas.stream()
                    .filter(p -> p.decision() == Decision.PROPONER && !p.accion().parametros().isEmpty()).count());
            }
            registro.put("verificacionesAutomaticas", "APROBADAS; calidad semántica requiere evaluación separada");
        } catch (RuntimeException | AssertionError error) {
            registro.put("error", error.getClass().getSimpleName() + ": " + error.getMessage());
            if (error instanceof OllamaClientException e) { registro.put("tipoErrorCliente", e.getTipo()); registro.put("estadoHttpError", e.getEstadoHttp()); }
            throw error;
        } finally {
            registro.put("solicitudHttpEnviada", registro.containsKey("bytesSolicitudReal"));
            registro.put("duracionTotalMs", Duration.ofNanos(System.nanoTime() - inicio).toMillis());
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(registro);
            Files.writeString(informe, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            System.out.println(json);
        }
    }

    private com.huellago.backend.services.ContextoRecomendacionesIAService.Resultado construirContexto(String escenario) {
        List<Habito> datos = new ArrayList<>(); boolean completo = escenario.equals("A");
        agregar(datos, "Transporte", "medio", "0", completo ? "Auto" : "Bicicleta");
        agregar(datos, "Transporte", "kmSemana", completo ? "100" : "20", "km/semana");
        agregar(datos, "Transporte", "diasSemana", completo ? "5" : "4", "dias/semana");
        if (completo) {
            agregar(datos, "Energía", "vivienda", "0", "Casa"); agregar(datos, "Energía", "personas", "3", "personas");
            agregar(datos, "Energía", "fuente", "0", "Electricidad"); agregar(datos, "Energía", "consumoKwhDiario", "12.345", "kWh/dia");
            agregar(datos, "Alimentación", "tipo", "0", "Vegetariana");
        }
        agregar(datos, "Residuos", "plasticos", "0", completo ? "Medio" : "Bajo");
        agregar(datos, "Residuos", "reciclas", completo ? "0" : "1", "boolean");
        HabitoRepository repositorio = mock(HabitoRepository.class);
        when(repositorio.findByUsuario_Id(9100L)).thenReturn(datos);
        var resultado = new ContextoRecomendacionesIAServiceImpl(repositorio).construir(9100L);
        verify(repositorio).findByUsuario_Id(9100L); verifyNoMoreInteractions(repositorio);
        assertEquals(completo ? List.of() : List.of("Energía", "Alimentación"), resultado.categoriasIncompletas());
        return resultado;
    }

    private void agregar(List<Habito> datos, String categoria, String nombre, String valor, String unidad) {
        Usuario u = new Usuario(); u.setId(9100L); // ID sintético: nunca se consulta una base de datos.
        CategoriaHabito c = new CategoriaHabito(); c.setNombre(categoria);
        Habito h = new Habito(); h.setId((long) datos.size() + 1); h.setUsuario(u); h.setCategoria(c);
        h.setNombre(nombre); h.setValor(new BigDecimal(valor)); h.setUnidad(unidad);
        h.setFechaRegistro(LocalDateTime.of(2026, 11, 8, 12, 0)); h.setFechaActualizacion(h.getFechaRegistro()); datos.add(h);
    }
}
