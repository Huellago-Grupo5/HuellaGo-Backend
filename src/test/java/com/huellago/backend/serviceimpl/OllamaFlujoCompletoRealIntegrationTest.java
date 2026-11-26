package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.*;
import com.huellago.backend.exceptions.OllamaClientException;
import com.huellago.backend.repositories.HabitoRepository;
import com.huellago.backend.services.ContextoRecomendacionesIAService;
import com.huellago.backend.services.AccionAmbientalAdmisible.Referencia;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;
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
import java.util.stream.Collectors;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.Decision.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Dos fases HTTP REALES, opt-in; únicamente el repositorio de hábitos es Mockito.
 * No contexto Spring, DataSource, Hibernate, publicación ni escritura de datos de negocio. */
@EnabledIfEnvironmentVariable(named = "HUELLAGO_OLLAMA_REAL_TEST", matches = "true")
@Execution(ExecutionMode.SAME_THREAD)
class OllamaFlujoCompletoRealIntegrationTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final CatalogoAccionesAmbientalesServiceImpl catalogo = new CatalogoAccionesAmbientalesServiceImpl();
    private record Perfil(ContextoRecomendacionesIAService.Resultado resultado, HabitoRepository repositorio, List<Habito> habitos) { }

    @ParameterizedTest @ValueSource(strings = {"A", "B"})
    void flujoCompletoReal(String escenario) throws Exception {
        ejecutar(escenario, "paso95");
    }

    @Test void escenarioAConAmbitosRestringidosPaso96() throws Exception {
        ejecutar("A", "paso96");
    }

    private void ejecutar(String escenario, String paso) throws Exception {
        Path carpeta = Path.of("target", paso); Files.createDirectories(carpeta);
        assertFalse(Files.exists(carpeta.resolve(escenario + "-resultado.json")), "No sobrescribir ni repetir una ronda real");
        var perfil = construir(escenario);
        var contexto = perfil.resultado().contexto(); Set<Referencia> realizadas = Set.of();
        var candidatas = catalogo.compatibles(contexto, realizadas);
        String contextoAntes = mapper.writeValueAsString(perfil.resultado());
        String habitosAntes = instantanea(perfil.habitos());
        guardar(carpeta, escenario + "-contexto.json", perfil.resultado());
        guardar(carpeta, escenario + "-candidatas.json", candidatas);
        Set<String> categorias = escenario.equals("A") ? Set.of("Transporte", "Energía", "Alimentación", "Residuos")
                : Set.of("Transporte", "Residuos");
        assertEquals(categorias, ValidadorRecomendacionesIA.contexto(contexto).keySet());
        candidatas.forEach(c -> assertEquals("BORRADOR", c.estadoRevision().name()));
        Map<String,Object> registro = new LinkedHashMap<>();
        registro.put("escenario", escenario); registro.put("tipoEjecucion", "HTTP_REAL_OLLAMA_DATOS_SINTETICOS");
        registro.put("inicio", Instant.now().toString()); registro.put("cantidadCandidatas", candidatas.size());
        Map<String,Object> fase1 = new LinkedHashMap<>(), fase2 = new LinkedHashMap<>();
        registro.put("seleccion", fase1); registro.put("redaccion", fase2);
        String[] fase = {"seleccion"};
        var ajustes = new OllamaConfiguration.Ajustes(URI.create("http://localhost:11434"), "qwen3:4b-instruct",
                Duration.ofSeconds(3), Duration.ofSeconds(120), 65536);
        // Observa y conserva bytes; execute() utiliza el transporte JDK real, sin HTTP simulado.
        var http = new OllamaConfiguration().ollamaRestClient(ajustes).mutate().requestInterceptor((request, body, execution) -> {
            String nombre = fase[0]; Map<String,Object> r = nombre.equals("seleccion") ? fase1 : fase2;
            assertFalse(r.containsKey("bytesSolicitud"), "No se permiten reintentos ni una nueva selección");
            r.put("bytesSolicitud", body.length);
            Files.write(carpeta.resolve(escenario + "-" + nombre + "-solicitud.json"), body, StandardOpenOption.CREATE_NEW);
            assertTrue(body.length <= 16384, "Solicitud supera 16.384 bytes");
            assertEquals("http://localhost:11434/api/chat", request.getURI().toString());
            assertEquals(HttpMethod.POST, request.getMethod()); assertNull(request.getHeaders().getFirst("Authorization"));
            var json = mapper.readTree(body);
            assertEquals("qwen3:4b-instruct", json.get("model").asString()); assertFalse(json.get("stream").asBoolean());
            var entrada = mapper.readTree(json.get("messages").get(1).get("content").asString());
            if (nombre.equals("seleccion")) {
                assertEquals(mapper.readTree(mapper.writeValueAsString(ValidadorRecomendacionesIA.contexto(contexto))), entrada.get("contexto"));
                Set<Referencia> refs = new HashSet<>();
                for (var a : entrada.get("accionesCandidatas")) {
                    refs.add(new Referencia(a.get("id").asString(), a.get("version").asString()));
                    assertEquals("BORRADOR", a.get("estadoRevision").asString());
                }
                assertEquals(candidatas.stream().map(a -> new Referencia(a.definicion().id(),a.definicion().version())).collect(Collectors.toSet()),refs);
            } else {
                assertFalse(entrada.has("accionesCandidatas"), "La segunda fase no selecciona nuevas acciones");
                for (var a : entrada.get("selecciones")) {
                    assertTrue(categorias.contains(a.get("categoria").asString()));
                    assertEquals("BORRADOR", a.get("estadoRevision").asString());
                }
            }
            long inicioHttp = System.nanoTime(); r.put("httpRealIntentado", true);
            ClientHttpResponse response;
            try { response = execution.execute(request, body); }
            catch (IOException | RuntimeException e) { r.put("duracionHttpMs", ms(inicioHttp)); throw e; }
            try {
                r.put("httpStatus", response.getStatusCode().value());
                byte[] bytes = response.getBody().readNBytes(ajustes.maxRespuestaBytes() + 1);
                r.put("duracionHttpMs", ms(inicioHttp)); r.put("bytesRespuesta", bytes.length);
                r.put("capturaCompletaDentroDelLimite", bytes.length <= ajustes.maxRespuestaBytes());
                Files.write(carpeta.resolve(escenario + "-" + nombre + "-respuesta-http.json"), bytes, StandardOpenOption.CREATE_NEW);
                InputStream copia = new ByteArrayInputStream(bytes);
                return new ClientHttpResponse() {
                    public HttpStatusCode getStatusCode() throws IOException { return response.getStatusCode(); }
                    public String getStatusText() throws IOException { return response.getStatusText(); }
                    public HttpHeaders getHeaders() { return response.getHeaders(); }
                    public InputStream getBody() { return copia; }
                    public void close() { response.close(); }
                };
            } catch (IOException | RuntimeException | Error e) { response.close(); throw e; }
        }).build();
        long inicio = System.nanoTime();
        try {
            var selector = new OllamaRecomendacionesServiceImpl(http, ajustes, catalogo);
            long t = System.nanoTime(); ResultadoGeneracionFundamentadaIADTO seleccion;
            try { seleccion = selector.generarV2ParaPruebasSinteticas(perfil.resultado(), realizadas); }
            finally { fase1.put("duracionFaseMs", ms(t)); }
            guardar(carpeta, escenario + "-seleccion-procesada.json", seleccion);
            guardar(carpeta, escenario + "-validacion-v2.json", seleccion.evaluacion());
            fase1.put("evaluacion", seleccion.evaluacion()); assertFalse(seleccion.publicable());
            if (seleccion.evaluacion().estado() != ACEPTADA_ESTRUCTURALMENTE || seleccion.propuesta() == null) {
                fase2.put("noEjecutada", "SELECCION_NO_ACEPTADA_ESTRUCTURALMENTE");
                fail("Selección rechazada: " + seleccion.evaluacion());
            }
            assertEquals(200, fase1.get("httpStatus"));
            var decisiones = seleccion.propuesta().resultados();
            assertEquals(categorias.size(), decisiones.size());
            assertEquals(categorias, decisiones.stream().map(p -> p.categoria()).collect(Collectors.toSet()));
            for (var p : decisiones) {
                assertNull(p.titulo()); assertNull(p.descripcion()); assertNull(p.motivo());
                if (p.decision() == PROPONER) assertTrue(candidatas.stream().anyMatch(c ->
                        c.definicion().id().equals(p.accion().id()) && c.definicion().version().equals(p.accion().version())
                                && c.definicion().categoria().equals(p.categoria())));
                else { assertNull(p.accion()); assertTrue(p.evidencias().isEmpty()); }
            }
            String seleccionAntes = mapper.writeValueAsString(seleccion);
            var propuestas = decisiones.stream().filter(p -> p.decision() == PROPONER).toList();
            fase1.put("decisiones", decisiones); fase1.put("cantidadPropuestas", propuestas.size());
            fase1.put("cantidadAbstenciones", decisiones.size() - propuestas.size());
            fase[0] = "redaccion";
            var redactor = new OllamaContenidoRecomendacionesServiceImpl(http, ajustes, catalogo);
            t = System.nanoTime(); ResultadoContenidoRecomendacionesIADTO contenido;
            try { contenido = redactor.generarParaPruebasSinteticas(contexto, seleccion, realizadas); }
            finally { fase2.put("duracionFaseMs", ms(t)); }
            guardar(carpeta, escenario + "-contenido-procesado.json", contenido);
            fase2.put("resultado", contenido);
            assertEquals(PENDIENTE_REVISION, contenido.estado()); assertFalse(contenido.publicable());
            assertSame(seleccion.propuesta(), contenido.seleccionOriginal());
            assertEquals(seleccionAntes, mapper.writeValueAsString(seleccion));
            assertEquals(propuestas.size(), contenido.contenidos().size());
            if (propuestas.isEmpty()) assertFalse(fase2.containsKey("httpRealIntentado"));
            else assertEquals(200, fase2.get("httpStatus"));
            Set<String> vistas = new HashSet<>();
            for (var c : contenido.contenidos()) {
                assertTrue(vistas.add(c.categoria()));
                var p = propuestas.stream().filter(a -> a.categoria().equals(c.categoria())).findFirst().orElseThrow();
                assertEquals(p.accion(), c.accion());
                assertFalse(c.titulo().isBlank()); assertTrue(c.titulo().length() <= 160);
                assertFalse(c.descripcion().isBlank()); assertTrue(c.descripcion().length() <= 1000);
                assertFalse(c.motivo().isBlank()); assertTrue(c.motivo().length() <= 1000);
            }
            registro.put("resultadoTecnico", "APROBADO_NO_CERTIFICA_SEMANTICA");
            registro.put("estadoFinal", contenido.estado()); registro.put("publicable", contenido.publicable());
        } catch (RuntimeException | AssertionError e) {
            registro.put("resultadoTecnico", "ERROR_O_RECHAZO_SIN_REPARACION");
            registro.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            if (e instanceof OllamaClientException o) { registro.put("tipoErrorCliente",o.getTipo()); registro.put("estadoHttpError",o.getEstadoHttp()); }
            throw e;
        } finally {
            registro.put("duracionTotalMs",ms(inicio));
            boolean sinCambios = contextoAntes.equals(mapper.writeValueAsString(perfil.resultado())) && habitosAntes.equals(instantanea(perfil.habitos()));
            registro.put("habitosYContextoSinCambios",sinCambios);
            registro.put("catalogoSoloBorradores",catalogo.listarBorradores().stream().allMatch(a -> a.estadoRevision().name().equals("BORRADOR")));
            guardar(carpeta,escenario + "-resultado.json",registro);
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(registro));
            verify(perfil.repositorio()).findByUsuario_Id(9500L); verifyNoMoreInteractions(perfil.repositorio());
            assertTrue(sinCambios);
        }
    }

    private void guardar(Path carpeta,String nombre,Object objeto) throws IOException {
        Files.writeString(carpeta.resolve(nombre),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(objeto),
                StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    private static long ms(long inicio) { return Duration.ofNanos(System.nanoTime()-inicio).toMillis(); }
    private String instantanea(List<Habito> habitos) {
        return mapper.writeValueAsString(habitos.stream().map(h -> List.of(h.getId(),h.getUsuario().getId(),
                h.getCategoria().getNombre(),h.getNombre(),h.getValor(),h.getUnidad(),h.getFechaRegistro().toString(),h.getFechaActualizacion().toString())).toList());
    }
    private Perfil construir(String escenario) {
        boolean a=escenario.equals("A"); List<Habito> habitos=new ArrayList<>();
        agregar(habitos,"Transporte","medio","0",a?"Auto":"Bicicleta");
        agregar(habitos,"Transporte","kmSemana",a?"100":"20","km/semana");
        agregar(habitos,"Transporte","diasSemana",a?"5":"4","dias/semana");
        if(a) {
            agregar(habitos,"Energía","vivienda","0","Casa"); agregar(habitos,"Energía","personas","3","personas");
            agregar(habitos,"Energía","fuente","0","Electricidad"); agregar(habitos,"Energía","consumoKwhDiario","12.345","kWh/dia");
            agregar(habitos,"Alimentación","tipo","0","Vegetariana");
        }
        agregar(habitos,"Residuos","plasticos","0",a?"Medio":"Bajo");
        agregar(habitos,"Residuos","reciclas",a?"0":"1","boolean");
        var repo=mock(HabitoRepository.class); when(repo.findByUsuario_Id(9500L)).thenReturn(habitos);
        var resultado=new ContextoRecomendacionesIAServiceImpl(repo).construir(9500L);
        assertEquals(a?List.of():List.of("Energía","Alimentación"),resultado.categoriasIncompletas());
        return new Perfil(resultado,repo,habitos);
    }
    private void agregar(List<Habito> lista,String categoria,String nombre,String valor,String unidad) {
        var u=new Usuario(); u.setId(9500L); // Identificador ficticio, nunca consultado en PostgreSQL.
        var c=new CategoriaHabito(); c.setNombre(categoria);
        var h=new Habito(); h.setId((long)lista.size()+1); h.setUsuario(u); h.setCategoria(c); h.setNombre(nombre);
        h.setValor(new BigDecimal(valor)); h.setUnidad(unidad); h.setFechaRegistro(LocalDateTime.of(2026,11,8,12,0));
        h.setFechaActualizacion(h.getFechaRegistro()); lista.add(h);
    }
}
