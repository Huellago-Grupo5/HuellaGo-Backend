package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ContextoRecomendacionesIADTO;
import com.huellago.backend.exceptions.OllamaClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import static com.huellago.backend.exceptions.OllamaClientException.Tipo.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Todo el HTTP está interceptado por MockRestServiceServer: no invoca Ollama ni PostgreSQL. */
class OllamaRecomendacionesServiceImplTest {
    static final String MODELO = "qwen3:4b-instruct";
    static final List<String> CATEGORIAS = List.of("Transporte", "Energía", "Alimentación", "Residuos");
    final JsonMapper mapper = JsonMapper.builder().build();
    MockRestServiceServer servidor;
    OllamaRecomendacionesServiceImpl cliente;

    @BeforeEach void preparar() {
        var builder = RestClient.builder().baseUrl("http://localhost:11434");
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new OllamaRecomendacionesServiceImpl(builder.build(), new OllamaConfiguration.Ajustes(
                URI.create("http://localhost:11434"), MODELO, Duration.ofSeconds(3), Duration.ofSeconds(120), 65536));
    }

    @Test void solicitudCorrectaLeeMessageContentYNoEnviaIdentidadNiCredenciales() {
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header("Accept", "application/json"))
                .andExpect(request -> {
                    assertNull(request.getHeaders().getFirst("Authorization"));
                    var body = mapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertEquals(MODELO, body.get("model").asString());
                    assertFalse(body.get("stream").asBoolean());
                    assertEquals("object", body.get("format").get("type").asString());
                    assertFalse(body.get("format").get("additionalProperties").asBoolean());
                    var esquema = body.get("format").get("properties").get("recomendaciones");
                    assertEquals(4, esquema.get("minItems").asInt());
                    assertEquals(4, esquema.get("maxItems").asInt());
                    assertEquals(4, esquema.get("items").get("properties").get("categoria").get("enum").size());
                    assertEquals("system", body.get("messages").get(0).get("role").asString());
                    assertEquals("user", body.get("messages").get(1).get("role").asString());
                    var datos = mapper.readTree(body.get("messages").get(1).get("content").asString());
                    assertEquals(4, datos.size());
                    assertEquals("Bus", datos.get("Transporte").get("medio").asString());
                    assertEquals(80, datos.get("Transporte").get("kmSemana").asInt());
                    assertEquals(12, datos.get("Energía").get("consumoKwhDiario").asInt());
                    assertFalse(datos.get("Residuos").get("reciclas").asBoolean());
                    String serializado = mapper.writeValueAsString(datos).toLowerCase(Locale.ROOT);
                    for (String secreto : List.of("usuarioid", "correo", "nombre", "password", "token", "jwt", "sesion"))
                        assertFalse(serializado.contains(secreto));
                    assertFalse(body.has("tools"));
                }).andRespond(withSuccess(envoltura(contenido(CATEGORIAS)), MediaType.APPLICATION_JSON));

        var resultado = cliente.generar(completo());
        assertEquals(CATEGORIAS, resultado.stream().map(r -> r.categoria()).toList());
        assertEquals("Acción sintética Transporte", resultado.getFirst().titulo());
        assertEquals("Respuesta simulada para verificar el adaptador.", resultado.getFirst().descripcion());
        assertThrows(UnsupportedOperationException.class, resultado::clear);
        servidor.verify();
    }

    @Test void categoriasParcialesSoloSolicitaYDevuelveLasPresentes() {
        var contexto = new ContextoRecomendacionesIADTO(null, completo().energia(), null, completo().residuos());
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andExpect(request -> {
            var body = mapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
            var esquema = body.get("format").get("properties").get("recomendaciones");
            assertEquals(2, esquema.get("minItems").asInt());
            var datos = mapper.readTree(body.get("messages").get(1).get("content").asString());
            assertEquals(2, datos.size()); assertFalse(datos.has("Transporte")); assertFalse(datos.has("Alimentación"));
        }).andRespond(withSuccess(envoltura(contenido(List.of("Residuos", "Energía"))), MediaType.APPLICATION_JSON));
        assertEquals(List.of("Energía", "Residuos"), cliente.generar(contexto).stream().map(r -> r.categoria()).toList());
        servidor.verify();
    }

    @Test void consumoElectricoEsOpcional() {
        var contexto = new ContextoRecomendacionesIADTO(null,
                new ContextoRecomendacionesIADTO.Energia("Casa", 2, "Gas", null), null, null);
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andExpect(request -> {
            var body = mapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
            var datos = mapper.readTree(body.get("messages").get(1).get("content").asString());
            assertFalse(datos.get("Energía").has("consumoKwhDiario"));
        }).andRespond(withSuccess(envoltura(contenido(List.of("Energía"))), MediaType.APPLICATION_JSON));
        assertEquals(1, cliente.generar(contexto).size()); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"", "texto", "null", "true", "[]", "{}",
            "{\"recomendaciones\":[]}", "{\"recomendaciones\":null}", "```json\n{}\n```"})
    void jsonInternoInvalidoOVacioSeRechaza(String contenido) {
        responder(envoltura(contenido)); error(RESPUESTA_INVALIDA); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"duplicada", "desconocida", "categoriaAusente", "faltante",
            "campoExtra", "tipoIncorrecto", "blanco", "tituloLargo", "descripcionLarga", "motivoLargo"})
    void rechazaContenidoQueNoCumpleElEsquema(String caso) {
        var filas = filas(CATEGORIAS);
        switch (caso) {
            case "duplicada" -> filas.get(1).put("categoria", "Transporte");
            case "desconocida" -> filas.get(1).put("categoria", "Agua");
            case "categoriaAusente" -> filas.get(0).remove("categoria");
            case "faltante" -> filas.removeLast();
            case "campoExtra" -> filas.get(0).put("usuarioId", 99);
            case "tipoIncorrecto" -> filas.get(0).put("motivo", 123);
            case "blanco" -> filas.get(0).put("descripcion", "   ");
            case "tituloLargo" -> filas.get(0).put("titulo", "a".repeat(161));
            case "descripcionLarga" -> filas.get(0).put("descripcion", "a".repeat(1001));
            case "motivoLargo" -> filas.get(0).put("motivo", "a".repeat(1001));
        }
        responder(envoltura(mapper.writeValueAsString(Map.of("recomendaciones", filas))));
        error(RESPUESTA_INVALIDA); servidor.verify();
    }

    @Test void noAceptaCategoriasQueNoSeSolicitaronAunqueLaCantidadCoincida() {
        responder(envoltura(contenido(List.of("Energía"))));
        var contexto = new ContextoRecomendacionesIADTO(completo().transporte(), null, null, null);
        assertEquals(RESPUESTA_INVALIDA, assertThrows(OllamaClientException.class, () -> cliente.generar(contexto)).getTipo());
        servidor.verify();
    }

    @Test void rechazaClavesDuplicadasYJsonAdicional() {
        String contenido = contenido(CATEGORIAS);
        responder(envoltura(contenido + " {}")); error(RESPUESTA_INVALIDA); servidor.verify();
        servidor.reset();
        responder(envoltura("{\"recomendaciones\":[],\"recomendaciones\":"
                + mapper.writeValueAsString(filas(CATEGORIAS)) + "}"));
        error(RESPUESTA_INVALIDA); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"sinMensaje", "sinContenido", "contenidoNoTexto", "rolIncorrecto",
            "sinModelo", "modeloIncorrecto", "sinDone", "doneFalse", "doneNoBooleano", "truncada", "error"})
    void validaLaEnvolturaDeOllamaAntesDeLeerElContenido(String caso) {
        var mensaje = new LinkedHashMap<String, Object>(Map.of("role", "assistant", "content", contenido(CATEGORIAS)));
        var respuesta = new LinkedHashMap<String, Object>(Map.of("model", MODELO, "done", true, "message", mensaje));
        switch (caso) {
            case "sinMensaje" -> respuesta.remove("message");
            case "sinContenido" -> mensaje.remove("content");
            case "contenidoNoTexto" -> mensaje.put("content", Map.of("recomendaciones", filas(CATEGORIAS)));
            case "rolIncorrecto" -> mensaje.put("role", "user");
            case "sinModelo" -> respuesta.remove("model");
            case "modeloIncorrecto" -> respuesta.put("model", "otro-modelo");
            case "sinDone" -> respuesta.remove("done");
            case "doneFalse" -> respuesta.put("done", false);
            case "doneNoBooleano" -> respuesta.put("done", "true");
            case "truncada" -> respuesta.put("done_reason", "length");
            case "error" -> respuesta.put("error", "detalle privado");
        }
        responder(mapper.writeValueAsString(respuesta)); error(RESPUESTA_INVALIDA); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"", "no-json", "null", "[]", "{}"})
    void cuerpoHttpInvalidoSeRechaza(String cuerpo) {
        responder(cuerpo); error(RESPUESTA_INVALIDA); servidor.verify();
    }

    @Test void limitaBytesDeRespuestaYNoSoloCaracteres() {
        String cuerpo = envoltura(contenido(CATEGORIAS));
        int faltan = 65536 - cuerpo.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        responder(cuerpo + " ".repeat(faltan)); assertEquals(4, cliente.generar(completo()).size()); servidor.verify();
        servidor.reset(); responder(cuerpo + " ".repeat(faltan + 1)); error(RESPUESTA_INVALIDA); servidor.verify();
    }

    @ParameterizedTest @ValueSource(ints = {302, 400, 404, 429, 500, 503})
    void erroresHttpNoExponenCuerpoNiSeSustituyenPorRecomendaciones(int estado) {
        servidor.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withStatus(HttpStatus.valueOf(estado)).body("secreto del proveedor"));
        var error = error(ERROR_HTTP);
        assertEquals(estado, error.getEstadoHttp()); assertFalse(error.getMessage().contains("secreto"));
        servidor.verify();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void timeoutSimuladoEsExplicitoYSinReintentos(boolean timeoutJdk) {
        servidor.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withException(timeoutJdk ? new java.net.http.HttpTimeoutException("detalle privado")
                        : new SocketTimeoutException("detalle privado")));
        var error = error(TIEMPO_AGOTADO); assertNull(error.getCause()); servidor.verify();
    }

    @Test void conexionRechazadaEsExplicita() {
        servidor.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withException(new ConnectException("detalle privado")));
        var error = error(NO_DISPONIBLE); assertNull(error.getCause()); servidor.verify();
    }

    @Test void contextoVacioNoEmiteHttp() {
        assertEquals(CONTEXTO_INVALIDO, assertThrows(OllamaClientException.class, () -> cliente.generar(null)).getTipo());
        assertEquals(CONTEXTO_INVALIDO, assertThrows(OllamaClientException.class,
                () -> cliente.generar(new ContextoRecomendacionesIADTO(null, null, null, null))).getTipo());
        servidor.verify();
    }

    @Test void urlYModeloConfiguradosSeUtilizanEnLaSolicitud() {
        var builder = RestClient.builder().baseUrl("http://127.0.0.1:11435");
        var otroServidor = MockRestServiceServer.bindTo(builder).build();
        var otroCliente = new OllamaRecomendacionesServiceImpl(builder.build(), new OllamaConfiguration.Ajustes(
                URI.create("http://127.0.0.1:11435"), "modelo-prueba:local", Duration.ofSeconds(1), Duration.ofSeconds(5), 65536));
        otroServidor.expect(requestTo("http://127.0.0.1:11435/api/chat")).andExpect(request -> {
            var body = mapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
            assertEquals("modelo-prueba:local", body.get("model").asString());
        }).andRespond(withSuccess(mapper.writeValueAsString(Map.of("model", "modelo-prueba:local", "done", true,
                "message", Map.of("role", "assistant", "content", contenido(CATEGORIAS)))), MediaType.APPLICATION_JSON));
        assertEquals(4, otroCliente.generar(completo()).size()); otroServidor.verify(); servidor.verify();
    }

    @Test void utf8MalFormadoSeRechazaSinReemplazarCaracteres() {
        byte[] bytes = envoltura(contenido(CATEGORIAS)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        boolean alterado = false;
        for (int i = 0; i < bytes.length - 1; i++) {
            if ((bytes[i] & 0xff) == 0xc3) { bytes[i + 1] = 0x28; alterado = true; break; }
        }
        assertTrue(alterado);
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON).body(bytes));
        error(RESPUESTA_INVALIDA); servidor.verify();
    }

    @Test void metadatosYThinkingNoSeConfundenConElContenido() {
        responder(mapper.writeValueAsString(Map.of("model", MODELO, "done", true, "eval_count", 50,
                "message", Map.of("role", "assistant", "thinking", "razonamiento privado",
                        "content", contenido(CATEGORIAS)))));
        var resultado = cliente.generar(completo());
        assertEquals(4, resultado.size());
        assertFalse(mapper.writeValueAsString(resultado).contains("privado")); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"medio", "dias", "km", "personas", "fuente", "consumo", "dieta", "reciclas", "plasticos"})
    void contextoInvalidoNoEnviaDatosNiHaceInferencia(String caso) {
        var invalido = switch (caso) {
            case "medio" -> new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte("persona@example.invalid", BigDecimal.ONE, 5), null, null, null);
            case "dias" -> new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte("Bus", BigDecimal.ONE, 8), null, null, null);
            case "km" -> new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte("Bus", BigDecimal.ONE.negate(), 5), null, null, null);
            case "personas" -> new ContextoRecomendacionesIADTO(null, new ContextoRecomendacionesIADTO.Energia("Casa", 0, "Gas", null), null, null);
            case "fuente" -> new ContextoRecomendacionesIADTO(null, new ContextoRecomendacionesIADTO.Energia("Casa", 2, "Gas", BigDecimal.ONE), null, null);
            case "consumo" -> new ContextoRecomendacionesIADTO(null, new ContextoRecomendacionesIADTO.Energia("Casa", 2, "Electricidad", BigDecimal.ONE.negate()), null, null);
            case "dieta" -> new ContextoRecomendacionesIADTO(null, null, new ContextoRecomendacionesIADTO.Alimentacion("desconocida"), null);
            case "reciclas" -> new ContextoRecomendacionesIADTO(null, null, null, new ContextoRecomendacionesIADTO.Residuos("Medio", null));
            default -> new ContextoRecomendacionesIADTO(null, null, null, new ContextoRecomendacionesIADTO.Residuos("desconocido", true));
        };
        assertEquals(CONTEXTO_INVALIDO, assertThrows(OllamaClientException.class, () -> cliente.generar(invalido)).getTipo());
        servidor.verify();
    }

    private void responder(String cuerpo) {
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andRespond(withSuccess(cuerpo, MediaType.APPLICATION_JSON));
    }
    private OllamaClientException error(OllamaClientException.Tipo tipo) {
        var error = assertThrows(OllamaClientException.class, () -> cliente.generar(completo()));
        assertEquals(tipo, error.getTipo()); return error;
    }
    private String envoltura(String contenido) {
        return mapper.writeValueAsString(Map.of("model", MODELO, "done", true, "done_reason", "stop",
                "message", Map.of("role", "assistant", "content", contenido)));
    }
    private String contenido(List<String> categorias) {
        return mapper.writeValueAsString(Map.of("recomendaciones", filas(categorias)));
    }
    private List<Map<String, Object>> filas(List<String> categorias) {
        return new ArrayList<>(categorias.stream().map(c -> {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("categoria", c); fila.put("titulo", "Acción sintética " + c);
            fila.put("descripcion", "Respuesta simulada para verificar el adaptador.");
            fila.put("motivo", "Motivo ficticio exclusivo del test HTTP."); return fila;
        }).toList());
    }
    private ContextoRecomendacionesIADTO completo() {
        return new ContextoRecomendacionesIADTO(
                new ContextoRecomendacionesIADTO.Transporte("Bus", new BigDecimal("80"), 5),
                new ContextoRecomendacionesIADTO.Energia("Casa", 3, "Electricidad", new BigDecimal("12")),
                new ContextoRecomendacionesIADTO.Alimentacion("Mixta"),
                new ContextoRecomendacionesIADTO.Residuos("Medio", false));
    }
}
