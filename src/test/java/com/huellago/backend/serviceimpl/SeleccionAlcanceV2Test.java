package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.services.*;
import com.huellago.backend.exceptions.OllamaClientException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.*;
import java.time.Duration;
import java.util.*;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Motivo.*;
import static com.huellago.backend.dtos.ResultadoPresentacionRecomendacionesIADTO.Estado.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Contextos y respuestas SINTÉTICOS; todo HTTP queda interceptado, sin red ni PostgreSQL. */
class SeleccionAlcanceV2Test {
    final JsonMapper mapper = JsonMapper.builder().build();
    final CatalogoAccionesAmbientalesServiceImpl catalogo = new CatalogoAccionesAmbientalesServiceImpl();
    final ValidadorRecomendacionFundamentadaIA validador = new ValidadorRecomendacionFundamentadaIA();
    final ContextoRecomendacionesIADTO contexto = new ContextoRecomendacionesIADTO(
            new ContextoRecomendacionesIADTO.Transporte("Auto", new BigDecimal("100"), 5),
            new ContextoRecomendacionesIADTO.Energia("Casa", 3, "Electricidad", new BigDecimal("12.345")), null, null);
    final ContextoRecomendacionesIAService.Resultado entrada = new ContextoRecomendacionesIAService.Resultado(
            contexto, List.of("Alimentación", "Residuos"), List.of(), ReglasInterpretacionAmbiental.REGLAS);
    MockRestServiceServer servidor;
    OllamaRecomendacionesServiceImpl cliente;
    OrquestadorRecomendacionesIAServiceImpl orquestador;
    @BeforeEach void preparar() {
        var builder = RestClient.builder().baseUrl("http://localhost:11434");
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new OllamaRecomendacionesServiceImpl(builder.build(), new OllamaConfiguration.Ajustes(
                URI.create("http://localhost:11434"), "qwen3:4b-instruct", Duration.ofSeconds(3), Duration.ofSeconds(120), 65536));
        orquestador = new OrquestadorRecomendacionesIAServiceImpl(cliente, catalogo, new PresentacionRecomendacionesIAServiceImpl());
    }
    List<CatalogoAccionesAmbientalesService.Candidata> permitidas(String... ids) {
        return catalogo.compatibles(contexto, Set.of()).stream().filter(a -> Set.of(ids).contains(a.definicion().id())).toList();
    }
    Propuesta proponer(String id) {
        var a = permitidas(id).getFirst().definicion();
        var hechos = ContratoOllamaV2.hechos(ValidadorRecomendacionesIA.contexto(contexto));
        var ev = a.evidenciasRequeridas().stream().sorted().map(r -> {
            var h = mapper.readTree(mapper.writeValueAsString(hechos.get(r)));
            return new Evidencia(r, mapper.readValue(mapper.writeValueAsString(h.get("valor")), Valor.class), Ambito.valueOf(h.get("ambito").asString()));
        }).toList();
        return new Propuesta(a.categoria(), Decision.PROPONER, new Accion(id, "1", Map.of()), ev, null, null, null, List.of(), null);
    }
    String json(Propuesta... p) { return mapper.writeValueAsString(new RecomendacionFundamentadaIADTO("2", List.of(p))); }
    String envoltura(String contenido) {
        return mapper.writeValueAsString(Map.of("model", "qwen3:4b-instruct", "done", true,
                "message", Map.of("role", "assistant", "content", contenido)));
    }
    void responder(String contenido) {
        servidor.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withSuccess(envoltura(contenido), MediaType.APPLICATION_JSON));
    }
    @Test void soloTransporteConservaEnergiaEnContextoPeroNoEnDecisiones() throws Exception {
        var lista = permitidas("TR_PLANIFICAR_DESPLAZAMIENTOS", "TR_EXPLORAR_ALTERNATIVAS_AUTO");
        String respuesta = json(proponer("TR_PLANIFICAR_DESPLAZAMIENTOS"));
        String antes = mapper.writeValueAsString(entrada);
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andExpect(method(HttpMethod.POST)).andExpect(r -> {
            String cuerpo = ((MockClientHttpRequest)r).getBodyAsString();
            int bytes = cuerpo.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            System.out.println("Alcance Transporte (2 candidatas), contexto T+E: " + bytes + " bytes");
            assertTrue(bytes <= 16384);
            var n = mapper.readTree(cuerpo);
            assertEquals("qwen3:4b-instruct", n.get("model").asString()); assertFalse(n.get("stream").asBoolean());
            var datos = mapper.readTree(n.get("messages").get(1).get("content").asString());
            assertEquals(2, datos.get("contexto").size()); assertTrue(datos.get("contexto").has("Energía"));
            assertEquals("HOGAR", datos.get("hechosDisponibles").get("/energia/consumoKwhDiario").get("ambito").asString());
            assertEquals(2, datos.get("accionesCandidatas").size());
            assertFalse(datos.get("accionesCandidatas").toString().contains("EN_REVISAR"));
            var esquema = n.get("format");
            assertEquals(1, esquema.get("properties").get("resultados").get("minItems").asInt());
            assertEquals(1, esquema.get("properties").get("resultados").get("maxItems").asInt());
            var categorias = esquema.get("$defs").get("resultado").get("oneOf").get(0).get("properties").get("categoria").get("enum");
            assertEquals(1, categorias.size()); assertEquals("Transporte", categorias.get(0).asString());
            assertFalse(esquema.get("$defs").get("accion").toString().contains("EN_REVISAR"));
        }).andRespond(withSuccess(envoltura(respuesta), MediaType.APPLICATION_JSON));
        var r = orquestador.orquestarParaPruebasSinteticas(entrada, lista, Set.of());
        assertEquals(SELECCION_ACEPTADA, r.presentacion().estado()); assertEquals(respuesta, r.seleccion().propuestaJson());
        assertEquals(Set.of("Transporte", "Energía"), r.alcance().completas());
        assertEquals(Set.of("Transporte"), r.alcance().seleccionables());
        assertEquals(Set.of("Energía"), r.alcance().excluidasSinPermitidas());
        assertEquals(Set.of("Alimentación", "Residuos"), r.alcance().incompletas());
        assertEquals(Set.of("Transporte"), r.categoriasRespondidas());
        assertEquals("SIN_ENFOQUE", r.presentacion().resultados().getFirst().presentacion().enfoque());
        assertFalse(r.publicable()); assertEquals(antes, mapper.writeValueAsString(entrada)); servidor.verify();
    }
    @Test void dosCategoriasPermitidasRequierenAmbasConHogarExacto() {
        var lista = permitidas("TR_PLANIFICAR_DESPLAZAMIENTOS", "EN_REVISAR_REGISTRO_HOGAR");
        responder(json(proponer("TR_PLANIFICAR_DESPLAZAMIENTOS"), proponer("EN_REVISAR_REGISTRO_HOGAR")));
        var r = orquestador.orquestarParaPruebasSinteticas(entrada, lista, Set.of());
        assertEquals(ACEPTADA_ESTRUCTURALMENTE, r.seleccion().evaluacion().estado());
        assertTrue(r.alcance().excluidasSinPermitidas().isEmpty()); assertEquals(2, r.categoriasRespondidas().size());
        assertEquals(2, r.presentacion().resultados().size()); servidor.verify();
    }
    @Test void catalogoCompletoConAlcanceRespetaTamanoEnHttpSerializado() {
        var completo = new ContextoRecomendacionesIADTO(contexto.transporte(), contexto.energia(),
                new ContextoRecomendacionesIADTO.Alimentacion("Vegetariana"), new ContextoRecomendacionesIADTO.Residuos("Medio", false));
        var resultado = new ContextoRecomendacionesIAService.Resultado(completo, List.of(), List.of(), ReglasInterpretacionAmbiental.REGLAS);
        var candidatas = catalogo.compatibles(completo, Set.of());
        assertEquals(8, candidatas.size());
        List<Propuesta> decisiones = new ArrayList<>();
        for (String categoria : List.of("Transporte", "Energía", "Alimentación", "Residuos"))
            decisiones.add(new Propuesta(categoria, Decision.ABSTENERSE, null, List.of(), null, null, null, List.of(), MotivoAbstencion.NO_VERIFICABLE));
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andExpect(r -> {
            String cuerpo = ((MockClientHttpRequest)r).getBodyAsString();
            int bytes = cuerpo.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            System.out.println("Alcance completo (8 candidatas): " + bytes + " bytes"); assertTrue(bytes <= 16384);
            var n = mapper.readTree(cuerpo);
            assertEquals(4, n.get("format").get("properties").get("resultados").get("minItems").asInt());
            assertEquals(8, mapper.readTree(n.get("messages").get(1).get("content").asString()).get("accionesCandidatas").size());
        }).andRespond(withSuccess(envoltura(mapper.writeValueAsString(new RecomendacionFundamentadaIADTO("2", decisiones))), MediaType.APPLICATION_JSON));
        assertEquals(ACEPTADA_ESTRUCTURALMENTE,
                cliente.generarV2ConAlcanceParaPruebasSinteticas(resultado, candidatas, Set.of()).evaluacion().estado());
        servidor.verify();
    }
    @ParameterizedTest @ValueSource(strings={"extra", "omitida", "fuera", "duplicada", "version", "hogar", "evidencia"})
    void respuestasIncorrectasSeRechazanSinReparacion(String caso) {
        var lista = permitidas("TR_PLANIFICAR_DESPLAZAMIENTOS");
        String contenido = switch(caso) {
            case "extra" -> json(proponer("TR_PLANIFICAR_DESPLAZAMIENTOS"), proponer("EN_REVISAR_USOS"));
            case "omitida" -> "{\"versionContrato\":\"2\",\"resultados\":[]}";
            case "fuera" -> json(proponer("TR_EXPLORAR_ALTERNATIVAS_AUTO"));
            case "duplicada" -> json(proponer("TR_PLANIFICAR_DESPLAZAMIENTOS"), proponer("TR_PLANIFICAR_DESPLAZAMIENTOS"));
            case "version" -> json(proponer("TR_PLANIFICAR_DESPLAZAMIENTOS")).replace("\"version\":\"1\"", "\"version\":\"999\"");
            case "hogar" -> json(proponer("EN_REVISAR_REGISTRO_HOGAR")).replace("HOGAR", "NO_APLICA");
            default -> json(proponer("TR_PLANIFICAR_DESPLAZAMIENTOS")).replace("100", "101");
        };
        if (caso.equals("hogar")) lista = permitidas("EN_REVISAR_REGISTRO_HOGAR");
        responder(contenido);
        var r = orquestador.orquestarParaPruebasSinteticas(entrada, lista, Set.of());
        assertEquals(contenido, r.seleccion().propuestaJson()); assertEquals(SELECCION_RECHAZADA, r.presentacion().estado());
        assertTrue(r.presentacion().resultados().isEmpty()); assertFalse(r.publicable()); servidor.verify();
    }
    @Test void omitirCategoriaSeleccionableSigueSiendoErrorEstricto() {
        var lista = permitidas("TR_PLANIFICAR_DESPLAZAMIENTOS", "EN_REVISAR_USOS");
        var alcance = validador.construirAlcanceExperimental(contexto, lista, Set.of());
        var r = validador.validarConAlcanceExperimental(json(proponer("TR_PLANIFICAR_DESPLAZAMIENTOS")), contexto, alcance, Set.of());
        assertEquals(RECHAZADA, r.estado()); assertTrue(r.motivos().contains(CATEGORIAS_INCOMPLETAS));
    }
    @Test void antiguoValidadorNoAceptaRespuestaParcialConContextoCompleto() {
        var lista = permitidas("TR_PLANIFICAR_DESPLAZAMIENTOS");
        var r = validador.validar(json(proponer("TR_PLANIFICAR_DESPLAZAMIENTOS")), contexto,
                lista.stream().map(CatalogoAccionesAmbientalesService.Candidata::definicion).toList(), Set.of());
        assertTrue(r.motivos().contains(CATEGORIAS_INCOMPLETAS));
    }
    @Test void listaVaciaAunqueCatalogoTengaAccionesNoHaceHttpNiInventaAbstencion() {
        var r = orquestador.orquestarParaPruebasSinteticas(entrada, List.of(), Set.of());
        assertEquals(SIN_PROPUESTA, r.presentacion().estado()); assertNull(r.seleccion().propuesta());
        assertNull(r.seleccion().propuestaJson()); assertTrue(r.categoriasRespondidas().isEmpty());
        assertEquals(r.alcance().completas(), r.alcance().excluidasSinPermitidas());
        assertFalse(r.publicable()); servidor.verify();
        assertNull(cliente.generarV2ConAlcanceParaPruebasSinteticas(entrada, List.of(), Set.of()).propuestaJson());
        servidor.verify();
    }
    @Test void abstencionRecibidaNoSeConfundeConEnergiaExcluida() {
        responder(json(new Propuesta("Transporte", Decision.ABSTENERSE, null, List.of(), null, null, null, List.of(), MotivoAbstencion.NO_VERIFICABLE)));
        var r = orquestador.orquestarParaPruebasSinteticas(entrada, permitidas("TR_PLANIFICAR_DESPLAZAMIENTOS"), Set.of());
        assertEquals(SELECCION_ACEPTADA, r.presentacion().estado());
        assertEquals(ResultadoPresentacionRecomendacionesIADTO.EstadoDetalle.ABSTENCION_V2, r.presentacion().resultados().getFirst().estado());
        assertEquals(Set.of("Energía"), r.alcance().excluidasSinPermitidas()); servidor.verify();
    }
    @ParameterizedTest @ValueSource(strings={"id", "version", "categoria", "parametros", "condiciones", "evidencias", "metadatos", "duplicadas", "realizada", "incompatible"})
    void entradaNoCanonicaSeRechazaAntesDeHttp(String caso) {
        var original = permitidas("TR_PLANIFICAR_DESPLAZAMIENTOS").getFirst(); var a = original.definicion();
        Set<AccionAmbientalAdmisible.Referencia> realizadas = caso.equals("realizada") ? Set.of(new AccionAmbientalAdmisible.Referencia(a.id(), a.version())) : Set.of();
        var manipulada = new AccionAmbientalAdmisible(caso.equals("id") ? "DESCONOCIDA" : a.id(), caso.equals("version") ? "999" : a.version(),
                caso.equals("categoria") ? "Energía" : a.categoria(), caso.equals("condiciones") ? Map.of() : a.condiciones(),
                caso.equals("evidencias") ? Set.of() : a.evidenciasRequeridas(), caso.equals("parametros") ? Map.of() : a.parametros(), a.informacionAdicionalPermitida());
        var candidata = new CatalogoAccionesAmbientalesService.Candidata(manipulada, original.objetivo(),
                caso.equals("metadatos") ? "Alterada" : original.descripcion(), original.restricciones(), original.estadoRevision());
        if (caso.equals("incompatible")) {
            var bici = new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte("Bicicleta", BigDecimal.TEN, 2), null, null, null);
            candidata = catalogo.compatibles(bici, Set.of()).getFirst();
        }
        var lista = caso.equals("duplicadas") ? List.of(original, original) : List.of(candidata);
        assertThrows(OllamaClientException.class, () -> cliente.generarV2ConAlcanceParaPruebasSinteticas(entrada, lista, realizadas));
        servidor.verify();
    }
    @Test void alcanceManipuladoNoPuedeAmpliarCategorias() {
        var lista = permitidas("TR_PLANIFICAR_DESPLAZAMIENTOS"); var a = validador.construirAlcanceExperimental(contexto, lista, Set.of());
        var falso = new AlcanceSeleccionIADTO(a.completas(), a.canonicasCompatibles(), a.permitidas(), a.completas(), Set.of(), a.incompletas());
        assertEquals(RECHAZADA, validador.validarConAlcanceExperimental(json(proponer("TR_PLANIFICAR_DESPLAZAMIENTOS")), contexto, falso, Set.of()).estado());
    }
    @ParameterizedTest @ValueSource(strings={"http", "timeout", "conexion", "json"})
    void erroresNoSonAbstenciones(String caso) {
        var expect = servidor.expect(requestTo("http://localhost:11434/api/chat"));
        if (caso.equals("http")) expect.andRespond(withServerError());
        else if (caso.equals("timeout")) expect.andRespond(r -> { throw new java.net.SocketTimeoutException("sintético"); });
        else if (caso.equals("conexion")) expect.andRespond(r -> { throw new java.net.ConnectException("sintético"); });
        else expect.andRespond(withSuccess(envoltura("{malformado"), MediaType.APPLICATION_JSON));
        var e = assertThrows(OllamaClientException.class, () -> orquestador.orquestarParaPruebasSinteticas(entrada, permitidas("TR_PLANIFICAR_DESPLAZAMIENTOS"), Set.of()));
        assertEquals(switch(caso) { case "http" -> OllamaClientException.Tipo.ERROR_HTTP;
            case "timeout" -> OllamaClientException.Tipo.TIEMPO_AGOTADO; case "conexion" -> OllamaClientException.Tipo.NO_DISPONIBLE;
            default -> OllamaClientException.Tipo.RESPUESTA_INVALIDA; }, e.getTipo()); servidor.verify();
    }
}
