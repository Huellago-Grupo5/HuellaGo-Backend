package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.exceptions.OllamaClientException;
import com.huellago.backend.services.AccionAmbientalAdmisible.Referencia;
import com.huellago.backend.services.CatalogoAccionesAmbientalesService.Candidata;
import com.huellago.backend.services.ContextoRecomendacionesIAService.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Motivo.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Solo HTTP simulado, sin Spring Boot, repositorios, inferencia real ni publicación. */
class OllamaRecomendacionesV2Test {
    final JsonMapper mapper = JsonMapper.builder().build();
    final CatalogoAccionesAmbientalesServiceImpl catalogo = new CatalogoAccionesAmbientalesServiceImpl();
    MockRestServiceServer servidor;
    OllamaRecomendacionesServiceImpl cliente;

    @BeforeEach void preparar() {
        var builder = RestClient.builder().baseUrl("http://localhost:11434");
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new OllamaRecomendacionesServiceImpl(builder.build(), new OllamaConfiguration.Ajustes(
                URI.create("http://localhost:11434"), "qwen3:4b-instruct", Duration.ofSeconds(3), Duration.ofSeconds(120), 65536));
    }

    @Test void solicitudMasAmpliaRespetaLimiteExistenteSinEliminarCandidatas() {
        var base = completo();
        var c = new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte("Auto", BigDecimal.TEN, 5),
                base.energia(), base.alimentacion(), base.residuos());
        var datos = ValidadorRecomendacionesIA.contexto(c); var acciones = catalogo.compatibles(c, Set.of());
        String contenido = mapper.writeValueAsString(ContratoOllamaV2.datos(datos, acciones));
        String solicitud = mapper.writeValueAsString(Map.of("model", "qwen3:4b-instruct", "stream", false,
                "format", ContratoOllamaV2.esquema(datos, acciones), "messages", List.of(
                        Map.of("role", "system", "content", ContratoOllamaV2.sistema()), Map.of("role", "user", "content", contenido))));
        int bytes = solicitud.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        System.out.println("Solicitud V2 con " + acciones.size() + " candidatas: " + bytes + " bytes");
        assertEquals(8, acciones.size()); assertTrue(bytes <= 16384, "Tamaño: " + bytes);
    }

    @Test void cuatroCategoriasContextoEvidenciasRestriccionesYEsquemaV2() throws Exception {
        var c = completo();
        var contenido = contrato(List.of(propuesta(c, "TR_PLANIFICAR_DESPLAZAMIENTOS"), propuesta(c, "EN_REVISAR_REGISTRO_HOGAR"),
                propuesta(c, "AL_PLANIFICAR_APROVECHAMIENTO"), propuesta(c, "RE_INICIAR_SEPARACION")));
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(request -> {
                    assertNull(request.getHeaders().getFirst("Authorization"));
                    String cuerpo = ((MockClientHttpRequest) request).getBodyAsString();
                    assertTrue(cuerpo.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 16384);
                    var body = mapper.readTree(cuerpo);
                    assertEquals("qwen3:4b-instruct", body.get("model").asString()); assertFalse(body.get("stream").asBoolean());
                    var datos = mapper.readTree(body.get("messages").get(1).get("content").asString());
                    assertEquals(4, datos.get("contexto").size());
                    assertEquals("Bus", datos.get("contexto").get("Transporte").get("medio").asString());
                    assertEquals("12.345", datos.get("contexto").get("Energía").get("consumoKwhDiario").toString());
                    assertEquals("HOGAR", datos.get("hechosDisponibles").get("/energia/consumoKwhDiario").get("ambito").asString());
                    Set<String> esperadas = catalogo.compatibles(c, Set.of()).stream().map(a -> a.definicion().id()).collect(Collectors.toSet());
                    Set<String> recibidas = new HashSet<>();
                    for (var a : datos.get("accionesCandidatas")) {
                        recibidas.add(a.get("id").asString()); assertEquals("BORRADOR", a.get("estadoRevision").asString());
                        assertFalse(a.get("restricciones").isEmpty()); assertFalse(a.get("evidenciasRequeridas").isEmpty());
                        assertTrue(a.has("parametros"));
                    }
                    assertEquals(esperadas, recibidas);
                    assertFalse(recibidas.contains("TR_EXPLORAR_ALTERNATIVAS_AUTO"));
                    assertFalse(recibidas.contains("RE_REVISAR_ENTREGA"));
                    var esquema = body.get("format");
                    assertEquals("2", esquema.get("properties").get("versionContrato").get("enum").get(0).asString());
                    assertEquals(4, esquema.get("properties").get("resultados").get("minItems").asInt());
                    assertTrue(esquema.get("$defs").has("accion")); assertTrue(esquema.get("$defs").has("evidencia"));
                    assertFalse(esquema.get("additionalProperties").asBoolean());
                    assertEquals(esperadas.size(), esquema.get("$defs").get("accion").get("oneOf").size());
                    String sistema = body.get("messages").get(0).get("content").asString();
                    assertTrue(sistema.contains("Selecciona tú")); assertTrue(sistema.contains("ABSTENERSE"));
                    assertTrue(sistema.contains("HOGAR")); assertTrue(sistema.contains("son obligatorios y siempre null"));
                    assertTrue(sistema.contains("tipo alimentario y reciclas"));
                    assertFalse(cuerpo.contains("privado@example.invalid")); assertFalse(cuerpo.contains("JWT_PRIVADO"));
                    assertFalse(cuerpo.contains("usuarioId")); assertFalse(cuerpo.contains("observaciones"));
                    java.nio.file.Files.writeString(java.nio.file.Path.of("target/paso90-solicitud-simulada.json"), cuerpo,
                            java.nio.charset.StandardCharsets.UTF_8);
                }).andRespond(withSuccess(envoltura(contenido), MediaType.APPLICATION_JSON));
        var r = cliente.generarV2ParaPruebasSinteticas(resultado(c), Set.of());
        assertEquals(ACEPTADA_ESTRUCTURALMENTE, r.evaluacion().estado());
        assertEquals(4, r.propuesta().resultados().size()); assertEquals(contenido, r.propuestaJson()); assertFalse(r.publicable());
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/paso90-respuesta-simulada.json"), contenido,
                java.nio.charset.StandardCharsets.UTF_8);
        servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"Vegetariana", "Vegana"})
    void contextoCompletoConAutoYReciclajeConservaTodasLasCandidatas(String tipo) {
        var base = completo();
        var c = new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte("Auto", new BigDecimal("100.125"), 7),
                base.energia(), new ContextoRecomendacionesIADTO.Alimentacion(tipo), residuos(true).residuos());
        assertEquals(8, catalogo.compatibles(c, Set.of()).size());
        responder(contrato(List.of(abstencion("Transporte"), abstencion("Energía"), abstencion("Alimentación"), abstencion("Residuos"))));
        var r = cliente.generarV2ParaPruebasSinteticas(resultado(c), Set.of());
        assertEquals(ACEPTADA_ESTRUCTURALMENTE, r.evaluacion().estado()); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"TR_PLANIFICAR_DESPLAZAMIENTOS", "TR_EXPLORAR_ALTERNATIVAS_AUTO"})
    void conservaSeleccionDelModeloEntreDosAccionesYPersonalizacion(String id) {
        var c = new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte("Auto", BigDecimal.TEN, 5), null, null, null);
        var p = propuesta(c, id); var a = candidata(id);
        Valor valor = a.definicion().parametros().get("enfoque").valoresPermitidos().stream().sorted(Comparator.comparing(Object::toString)).toList().getLast();
        p = new Propuesta(p.categoria(), p.decision(), new Accion(id, "1", Map.of("enfoque", valor)), p.evidencias(), null, null, null, List.of(), null);
        responder(contrato(List.of(p)));
        var r = cliente.generarV2ParaPruebasSinteticas(resultado(c), Set.of());
        assertEquals(ACEPTADA_ESTRUCTURALMENTE, r.evaluacion().estado());
        assertEquals(id, r.propuesta().resultados().getFirst().accion().id());
        assertEquals(valor, r.propuesta().resultados().getFirst().accion().parametros().get("enfoque")); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"accionInexistente", "version", "categoria", "evidencia", "parametro", "duplicada", "ambito", "reciclajeIncompatible", "carneVegetariano"})
    void rechazosVerificablesSeConservanSinRecomendacionesAlternativas(String caso) {
        var c = residuos(true); var p = propuesta(c, "RE_REVISAR_ENTREGA");
        var motivo = ACCION_NO_AUTORIZADA;
        List<Propuesta> propuestas = new ArrayList<>();
        switch (caso) {
            case "accionInexistente", "carneVegetariano" -> {
                if (caso.equals("carneVegetariano")) {
                    c = new ContextoRecomendacionesIADTO(null, null, new ContextoRecomendacionesIADTO.Alimentacion("Vegetariana"), null);
                    p = propuesta(c, "AL_PLANIFICAR_APROVECHAMIENTO");
                }
                p = cambiar(p, new Accion(caso.equals("carneVegetariano") ? "CARNE_OBLIGATORIA" : "INVENTADA", "1", Map.of()), p.evidencias());
            }
            case "version" -> { p = cambiar(p, new Accion(p.accion().id(), "99", Map.of()), p.evidencias()); motivo = VERSION_ACCION_INVALIDA; }
            case "categoria" -> { p = new Propuesta("Energía", p.decision(), p.accion(), p.evidencias(), null, null, null, List.of(), null); motivo = CATEGORIA_AUSENTE; }
            case "evidencia" -> { p = cambiar(p, p.accion(), List.of(new Evidencia("/residuos/reciclas", new Booleano(false), Ambito.NO_APLICA),
                    new Evidencia("/residuos/plasticos", new Texto("Medio"), Ambito.NO_APLICA))); motivo = EVIDENCIA_NO_COINCIDE; }
            case "parametro" -> { p = cambiar(p, new Accion(p.accion().id(), "1", Map.of("ahorro", new Numero(BigDecimal.TEN))), p.evidencias()); motivo = PARAMETRO_NO_AUTORIZADO; }
            case "duplicada" -> { propuestas.add(p); motivo = CATEGORIA_DUPLICADA; }
            case "ambito" -> {
                c = new ContextoRecomendacionesIADTO(null, completo().energia(), null, null); p = propuesta(c, "EN_REVISAR_REGISTRO_HOGAR");
                var ev = p.evidencias().stream().map(e -> e.ruta().contains("consumo") ? new Evidencia(e.ruta(), e.valor(), Ambito.NO_APLICA) : e).toList();
                p = cambiar(p, p.accion(), ev); motivo = AMBITO_INVALIDO;
            }
            case "reciclajeIncompatible" -> p = cambiar(p, new Accion("RE_INICIAR_SEPARACION", "1", Map.of()), p.evidencias());
        }
        propuestas.add(p); responder(contrato(propuestas));
        var r = cliente.generarV2ParaPruebasSinteticas(resultado(c), Set.of());
        assertEquals(RECHAZADA, r.evaluacion().estado()); assertTrue(r.evaluacion().motivos().contains(motivo), r.evaluacion().toString());
        assertEquals(propuestas.size(), r.propuesta().resultados().size()); assertFalse(r.publicable()); servidor.verify();
    }

    @Test void textoLibreSeConservaPendienteSinCertificarNiPublicar() {
        var c = residuos(true); var p = propuesta(c, "RE_REVISAR_ENTREGA");
        p = new Propuesta(p.categoria(), p.decision(), p.accion(), p.evidencias(), "Nunca reciclas", "Inicia el reciclaje", "No reciclas", List.of(), null);
        responder(contrato(List.of(p)));
        var r = cliente.generarV2ParaPruebasSinteticas(resultado(c), Set.of());
        assertEquals(PENDIENTE_REVISION, r.evaluacion().estado()); assertEquals("Nunca reciclas", r.propuesta().resultados().getFirst().titulo());
        assertTrue(r.evaluacion().motivos().contains(TEXTO_LIBRE_REQUIERE_REVISION)); assertFalse(r.publicable()); servidor.verify();
    }

    @Test void abstencionAunqueExistanAccionesNoSeReemplaza() {
        var c = residuos(false); responder(contrato(List.of(abstencion("Residuos"))));
        var r = cliente.generarV2ParaPruebasSinteticas(resultado(c), Set.of());
        assertEquals(ACEPTADA_ESTRUCTURALMENTE, r.evaluacion().estado());
        assertEquals(Decision.ABSTENERSE, r.propuesta().resultados().getFirst().decision());
        assertNull(r.propuesta().resultados().getFirst().accion()); assertFalse(r.publicable()); servidor.verify();
    }

    @Test void sinCandidatasPorHistorialNoHaceHttpNiFabricaAbstencion() {
        var c = residuos(true);
        var realizadas = catalogo.compatibles(c, Set.of()).stream().map(a -> new Referencia(a.definicion().id(), "1")).collect(Collectors.toSet());
        var r = cliente.generarV2ParaPruebasSinteticas(resultado(c), realizadas);
        assertEquals(PENDIENTE_REVISION, r.evaluacion().estado());
        assertNull(r.propuesta()); assertNull(r.propuestaJson()); assertFalse(r.publicable());
        assertTrue(r.evaluacion().evaluaciones().isEmpty()); assertTrue(r.evaluacion().motivos().isEmpty()); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"consumoPresente", "consumoAusente", "parcial"})
    void esquemaRestringeAmbitoSegunRutaSinInventarCampos(String caso) {
        var base = completo();
        var c = caso.equals("parcial") ? residuos(true) : caso.equals("consumoAusente")
                ? new ContextoRecomendacionesIADTO(base.transporte(), new ContextoRecomendacionesIADTO.Energia(
                        base.energia().vivienda(), base.energia().personas(), base.energia().fuente(), null), base.alimentacion(), base.residuos())
                : base;
        var datos = ValidadorRecomendacionesIA.contexto(c);
        var esquema = mapper.readTree(mapper.writeValueAsString(ContratoOllamaV2.esquema(datos, catalogo.compatibles(c, Set.of()))));
        var evidencia = esquema.get("$defs").get("evidencia");
        var ramas = evidencia.has("oneOf") ? evidencia.get("oneOf") : mapper.readTree("[" + mapper.writeValueAsString(evidencia) + "]");
        Set<String> vistas = new HashSet<>();
        for (var rama : ramas) {
            assertEquals("object", rama.get("type").asString());
            assertFalse(rama.get("additionalProperties").asBoolean());
            assertEquals(mapper.readTree("[\"ruta\",\"valor\",\"ambito\"]"), rama.get("required"));
            var propiedades = rama.get("properties");
            assertEquals(1, propiedades.get("ambito").get("enum").size());
            String ambito = propiedades.get("ambito").get("enum").get(0).asString();
            for (var ruta : propiedades.get("ruta").get("enum")) {
                assertTrue(vistas.add(ruta.asString()), "Las ramas deben ser disjuntas");
                assertEquals(ruta.asString().equals("/energia/consumoKwhDiario") ? "HOGAR" : "NO_APLICA", ambito);
                assertNotEquals(ruta.asString().equals("/energia/consumoKwhDiario") ? "NO_APLICA" : "HOGAR", ambito);
            }
            assertEquals("#/$defs/valor", propiedades.get("valor").get("$ref").asString());
        }
        assertEquals(ContratoOllamaV2.hechos(datos).keySet(), vistas);
        assertEquals(caso.equals("consumoPresente"), vistas.contains("/energia/consumoKwhDiario"));
        assertEquals(caso.equals("consumoPresente") ? 2 : 1, ramas.size());
    }

    @Test void esquemaTieneRamasCompletasSinInterseccionesImplicitas() {
        var c = completo(); var datos = ValidadorRecomendacionesIA.contexto(c);
        var acciones = catalogo.compatibles(c, Set.of());
        var esquema = mapper.readTree(mapper.writeValueAsString(ContratoOllamaV2.esquema(datos, acciones)));
        var accion = esquema.get("$defs").get("accion");
        assertEquals(1, accion.size());
        for (var rama : accion.get("oneOf")) {
            assertEquals("object", rama.get("type").asString());
            assertEquals(mapper.readTree("[\"id\",\"version\",\"parametros\"]"), rama.get("required"));
            assertFalse(rama.get("additionalProperties").asBoolean());
            assertTrue(rama.get("properties").get("parametros").get("required").isEmpty(), "Los parámetros del catálogo actual son opcionales: {} es válido");
            assertFalse(rama.has("oneOf"));
        }
        var resultado = esquema.get("$defs").get("resultado");
        assertEquals(1, resultado.size()); assertEquals(2, resultado.get("oneOf").size());
        var propuesta = resultado.get("oneOf").get(0).get("properties");
        var abstencion = resultado.get("oneOf").get(1).get("properties");
        assertEquals("PROPONER", propuesta.get("decision").get("enum").get(0).asString());
        assertEquals("#/$defs/accion", propuesta.get("accion").get("$ref").asString());
        assertEquals("ABSTENERSE", abstencion.get("decision").get("enum").get(0).asString());
        assertEquals("null", abstencion.get("accion").get("type").asString());
        assertEquals(0, abstencion.get("evidencias").get("maxItems").asInt());
        for (var rama : resultado.get("oneOf")) {
            for (String campo : List.of("titulo", "descripcion", "motivo")) {
                assertEquals(mapper.readTree("{\"type\":\"null\"}"), rama.get("properties").get(campo));
                assertTrue(mapper.writeValueAsString(rama.get("required")).contains("\"" + campo + "\""));
            }
        }
        assertFalse(mapper.writeValueAsString(esquema).contains("\"allOf\""));
        assertThrows(IllegalArgumentException.class, () -> ContratoOllamaV2.esquema(datos, List.of()));
    }

    @ParameterizedTest @ValueSource(strings = {"titulo", "descripcion", "motivo"})
    void textoObligatorioAusenteNoSeCompletaConNull(String campo) {
        var c = residuos(true);
        var arbol = mapper.readTree(contrato(List.of(propuesta(c, "RE_REVISAR_ENTREGA"))));
        ((tools.jackson.databind.node.ObjectNode) arbol.get("resultados").get(0)).remove(campo);
        String json = mapper.writeValueAsString(arbol); responder(json);
        var r = cliente.generarV2ParaPruebasSinteticas(resultado(c), Set.of());
        assertEquals(RECHAZADA, r.evaluacion().estado());
        assertTrue(r.evaluacion().motivos().contains(CONTRATO_INVALIDO));
        assertNull(r.propuesta()); assertEquals(json, r.propuestaJson()); servidor.verify();
    }

    @Test void parametrosAusentesNoSeCompletanConObjetoVacio() {
        var c = residuos(true);
        var arbol = mapper.readTree(contrato(List.of(propuesta(c, "RE_REVISAR_ENTREGA"))));
        ((tools.jackson.databind.node.ObjectNode) arbol.get("resultados").get(0).get("accion")).remove("parametros");
        String json = mapper.writeValueAsString(arbol); responder(json);
        var r = cliente.generarV2ParaPruebasSinteticas(resultado(c), Set.of());
        assertEquals(RECHAZADA, r.evaluacion().estado()); assertTrue(r.evaluacion().motivos().contains(CONTRATO_INVALIDO));
        assertNull(r.propuesta()); assertEquals(json, r.propuestaJson()); servidor.verify();
    }

    @Test void abstencionConAccionNoSeReparaNiAcepta() {
        var c = residuos(true); var p = abstencion("Residuos");
        var contradictoria = new Propuesta(p.categoria(), p.decision(), propuesta(c, "RE_REVISAR_ENTREGA").accion(),
                p.evidencias(), null, null, null, p.informacionAdicional(), p.motivoAbstencion());
        responder(contrato(List.of(contradictoria)));
        var r = cliente.generarV2ParaPruebasSinteticas(resultado(c), Set.of());
        assertEquals(RECHAZADA, r.evaluacion().estado()); assertTrue(r.evaluacion().motivos().contains(CONTRATO_INVALIDO));
        assertNull(r.propuesta()); assertTrue(r.propuestaJson().contains("RE_REVISAR_ENTREGA")); servidor.verify();
    }

    @Test void seleccionYaRealizadaSeRechaza() {
        var c = residuos(true); responder(contrato(List.of(propuesta(c, "RE_REVISAR_ENTREGA"))));
        var r = cliente.generarV2ParaPruebasSinteticas(resultado(c), Set.of(new Referencia("RE_REVISAR_ENTREGA", "1")));
        assertEquals(RECHAZADA, r.evaluacion().estado()); assertTrue(r.evaluacion().motivos().contains(ACCION_REPETIDA)); servidor.verify();
    }

    @Test void categoriasAusentesNoSeEnvianNiSeInventanConsumos() {
        var c = new ContextoRecomendacionesIADTO(null, new ContextoRecomendacionesIADTO.Energia("Casa", 3, "Electricidad", null), null, null);
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andExpect(request -> {
            var body = mapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
            var datos = mapper.readTree(body.get("messages").get(1).get("content").asString());
            assertEquals(1, datos.get("contexto").size());
            assertFalse(datos.get("hechosDisponibles").has("/energia/consumoKwhDiario"));
            assertEquals(1, datos.get("accionesCandidatas").size());
            assertEquals("EN_REVISAR_USOS", datos.get("accionesCandidatas").get(0).get("id").asString());
        }).andRespond(withSuccess(envoltura(contrato(List.of(abstencion("Energía")))), MediaType.APPLICATION_JSON));
        assertEquals(ACEPTADA_ESTRUCTURALMENTE, cliente.generarV2ParaPruebasSinteticas(resultado(c), Set.of()).evaluacion().estado()); servidor.verify();
    }

    @ParameterizedTest @ValueSource(ints = {302, 429, 500, 503})
    void falloHttpNoTieneFallbackNiExponeCuerpo(int codigo) {
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andRespond(withStatus(HttpStatus.valueOf(codigo)).body("privado"));
        var e = assertThrows(OllamaClientException.class, () -> cliente.generarV2ParaPruebasSinteticas(resultado(residuos(true)), Set.of()));
        assertEquals(OllamaClientException.Tipo.ERROR_HTTP, e.getTipo()); assertEquals(codigo, e.getEstadoHttp());
        assertFalse(e.getMessage().contains("privado")); servidor.verify();
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void timeoutOConexionCaidaSonExplicitos(boolean timeout) {
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andRespond(withException(timeout ? new SocketTimeoutException("privado") : new ConnectException("privado")));
        var e = assertThrows(OllamaClientException.class, () -> cliente.generarV2ParaPruebasSinteticas(resultado(residuos(true)), Set.of()));
        assertEquals(timeout ? OllamaClientException.Tipo.TIEMPO_AGOTADO : OllamaClientException.Tipo.NO_DISPONIBLE, e.getTipo()); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"{", "{} {}", "{\"versionContrato\":\"2\",\"versionContrato\":\"2\"}"})
    void jsonMalFormadoEsErrorTipado(String contenido) {
        responder(contenido);
        assertEquals(OllamaClientException.Tipo.RESPUESTA_INVALIDA, assertThrows(OllamaClientException.class,
                () -> cliente.generarV2ParaPruebasSinteticas(resultado(residuos(true)), Set.of())).getTipo()); servidor.verify();
    }

    @Test void estructuraInvalidaConservaJsonYMotivoSinFabricarDto() {
        responder("{}"); var r = cliente.generarV2ParaPruebasSinteticas(resultado(residuos(true)), Set.of());
        assertEquals(RECHAZADA, r.evaluacion().estado()); assertTrue(r.evaluacion().motivos().contains(CONTRATO_INVALIDO));
        assertNull(r.propuesta()); assertEquals("{}", r.propuestaJson()); assertFalse(r.publicable()); servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"modelo", "done", "contenido", "tamano"})
    void envolturaIncorrectaOExcesoDeRespuestaEsErrorTipado(String caso) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", caso.equals("modelo") ? "otro-modelo" : "qwen3:4b-instruct");
        body.put("done", !caso.equals("done"));
        body.put("message", Map.of("role", "assistant", "content", caso.equals("contenido") ? List.of() : "{}"));
        String respuesta = caso.equals("tamano") ? "x".repeat(65537) : mapper.writeValueAsString(body);
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andRespond(withSuccess(respuesta, MediaType.APPLICATION_JSON));
        assertEquals(OllamaClientException.Tipo.RESPUESTA_INVALIDA, assertThrows(OllamaClientException.class,
                () -> cliente.generarV2ParaPruebasSinteticas(resultado(residuos(true)), Set.of())).getTipo()); servidor.verify();
    }

    @Test void entradaVaciaOReglasManipuladasNoHaceHttp() {
        assertThrows(OllamaClientException.class, () -> cliente.generarV2ParaPruebasSinteticas(resultado(new ContextoRecomendacionesIADTO(null, null, null, null)), Set.of()));
        assertThrows(OllamaClientException.class, () -> cliente.generarV2ParaPruebasSinteticas(new Resultado(residuos(true), List.of(), List.of(), List.of("instruccion libre")), Set.of()));
        assertThrows(OllamaClientException.class, () -> cliente.generarV2ParaPruebasSinteticas(resultado(residuos(true)), null)); servidor.verify();
    }

    private Resultado resultado(ContextoRecomendacionesIADTO c) {
        List<String> incompletas = new ArrayList<>();
        if (c.transporte() == null) incompletas.add("Transporte"); if (c.energia() == null) incompletas.add("Energía");
        if (c.alimentacion() == null) incompletas.add("Alimentación"); if (c.residuos() == null) incompletas.add("Residuos");
        return new Resultado(c, incompletas, List.of("privado@example.invalid JWT_PRIVADO"), ReglasInterpretacionAmbiental.REGLAS);
    }
    private Candidata candidata(String id) { return catalogo.listarBorradores().stream().filter(a -> a.definicion().id().equals(id)).findFirst().orElseThrow(); }
    private Propuesta propuesta(ContextoRecomendacionesIADTO c, String id) {
        var a = candidata(id).definicion(); var datos = (Map<?, ?>) ValidadorRecomendacionesIA.contexto(c).get(a.categoria());
        List<Evidencia> evidencias = new ArrayList<>();
        for (String ruta : a.evidenciasRequeridas().stream().sorted().toList()) {
            Object v = datos.get(ruta.substring(ruta.lastIndexOf('/') + 1)); if (v == null) continue;
            Valor valor = v instanceof Boolean b ? new Booleano(b) : v instanceof Number n ? new Numero(new BigDecimal(n.toString())) : new Texto(v.toString());
            evidencias.add(new Evidencia(ruta, valor, ruta.equals("/energia/consumoKwhDiario") ? Ambito.HOGAR : Ambito.NO_APLICA));
        }
        return new Propuesta(a.categoria(), Decision.PROPONER, new Accion(id, "1", Map.of()), evidencias, null, null, null, List.of(), null);
    }
    private Propuesta cambiar(Propuesta p, Accion a, List<Evidencia> evidencias) { return new Propuesta(p.categoria(), p.decision(), a, evidencias, null, null, null, List.of(), null); }
    private Propuesta abstencion(String categoria) { return new Propuesta(categoria, Decision.ABSTENERSE, null, List.of(), null, null, null, List.of(), MotivoAbstencion.NO_VERIFICABLE); }
    private String contrato(List<Propuesta> ps) { return mapper.writeValueAsString(new RecomendacionFundamentadaIADTO("2", ps)); }
    private String envoltura(String contenido) { return mapper.writeValueAsString(Map.of("model", "qwen3:4b-instruct", "done", true, "message", Map.of("role", "assistant", "content", contenido))); }
    private void responder(String contenido) { servidor.expect(requestTo("http://localhost:11434/api/chat")).andRespond(withSuccess(envoltura(contenido), MediaType.APPLICATION_JSON)); }
    private ContextoRecomendacionesIADTO residuos(boolean reciclas) { return new ContextoRecomendacionesIADTO(null, null, null, new ContextoRecomendacionesIADTO.Residuos("Medio", reciclas)); }
    private ContextoRecomendacionesIADTO completo() {
        return new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte("Bus", new BigDecimal("80"), 5),
                new ContextoRecomendacionesIADTO.Energia("Casa", 3, "Electricidad", new BigDecimal("12.345")),
                new ContextoRecomendacionesIADTO.Alimentacion("Mixta"), residuos(false).residuos());
    }
}
