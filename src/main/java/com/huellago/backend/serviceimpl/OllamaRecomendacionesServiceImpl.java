package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ContextoRecomendacionesIADTO;
import com.huellago.backend.dtos.RecomendacionGeneradaIADTO;
import com.huellago.backend.dtos.RecomendacionFundamentadaIADTO;
import com.huellago.backend.dtos.ResultadoGeneracionFundamentadaIADTO;
import com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Motivo;
import com.huellago.backend.exceptions.OllamaClientException;
import com.huellago.backend.services.GeneradorRecomendacionesIAService;
import com.huellago.backend.services.ContextoRecomendacionesIAService;
import com.huellago.backend.services.CatalogoAccionesAmbientalesService;
import com.huellago.backend.services.AccionAmbientalAdmisible;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.huellago.backend.exceptions.OllamaClientException.Tipo.*;

/** Adaptador sin repositorios, transacciones, listeners ni llamadas automáticas. */
public class OllamaRecomendacionesServiceImpl implements GeneradorRecomendacionesIAService {
    private final RestClient cliente;
    private final OllamaConfiguration.Ajustes ajustes;
    private final JsonMapper mapper;
    private final CatalogoAccionesAmbientalesService catalogo;
    private final ValidadorRecomendacionFundamentadaIA validadorV2 = new ValidadorRecomendacionFundamentadaIA();

    public OllamaRecomendacionesServiceImpl(RestClient cliente, OllamaConfiguration.Ajustes ajustes) {
        this(cliente, ajustes, new CatalogoAccionesAmbientalesServiceImpl());
    }

    public OllamaRecomendacionesServiceImpl(RestClient cliente, OllamaConfiguration.Ajustes ajustes,
            CatalogoAccionesAmbientalesService catalogo) {
        this.cliente = java.util.Objects.requireNonNull(cliente);
        this.ajustes = java.util.Objects.requireNonNull(ajustes);
        this.catalogo = java.util.Objects.requireNonNull(catalogo);
        this.mapper = JsonMapper.builder(JsonFactory.builder()
                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(20)
                                .maxStringLength(65_536).maxNumberLength(100).build()).build())
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    }

    @Override
    public List<RecomendacionGeneradaIADTO> generar(ContextoRecomendacionesIADTO contexto) {
        return generarConReglas(contexto, ReglasInterpretacionAmbiental.REGLAS);
    }

    @Override
    public List<RecomendacionGeneradaIADTO> generarDesdeContexto(ContextoRecomendacionesIAService.Resultado resultado) {
        return generarConReglas(contextoDeResultado(resultado).contexto(), resultado.reglasInterpretacion());
    }

    private ContextoRecomendacionesIAService.Resultado contextoDeResultado(ContextoRecomendacionesIAService.Resultado resultado) {
        // Solo instrucciones de la política interna, nunca texto libre promovido a rol system.
        if (resultado == null || resultado.contexto() == null
                || !ReglasInterpretacionAmbiental.REGLAS.equals(resultado.reglasInterpretacion()))
            throw ValidadorRecomendacionesIA.entrada();
        var contexto = resultado.contexto();
        var incompletas = resultado.categoriasIncompletas();
        if ((incompletas.contains("Transporte") && contexto.transporte() != null)
                || (incompletas.contains("Energía") && contexto.energia() != null)
                || (incompletas.contains("Alimentación") && contexto.alimentacion() != null)
                || (incompletas.contains("Residuos") && contexto.residuos() != null))
            throw ValidadorRecomendacionesIA.entrada();
        return resultado;
    }

    @Override
    public ResultadoGeneracionFundamentadaIADTO generarV2ParaPruebasSinteticas(
            ContextoRecomendacionesIAService.Resultado resultado, Set<AccionAmbientalAdmisible.Referencia> realizadas) {
        var verificado = contextoDeResultado(resultado);
        var contexto = verificado.contexto();
        if (realizadas == null || realizadas.stream().anyMatch(java.util.Objects::isNull))
            throw ValidadorRecomendacionesIA.entrada();
        Set<AccionAmbientalAdmisible.Referencia> historial = Set.copyOf(realizadas);
        var datos = ValidadorRecomendacionesIA.contexto(contexto);
        var candidatas = List.copyOf(catalogo.compatibles(contexto, historial));
        return seleccionarV2(verificado, candidatas, historial, null);
    }

    @Override
    public ResultadoGeneracionFundamentadaIADTO generarV2ConAlcanceParaPruebasSinteticas(
            ContextoRecomendacionesIAService.Resultado resultado,
            List<CatalogoAccionesAmbientalesService.Candidata> permitidas,
            Set<AccionAmbientalAdmisible.Referencia> realizadas) {
        return generarConAlcance(resultado, permitidas, realizadas);
    }

    @Override
    public ResultadoGeneracionFundamentadaIADTO generarV2ConAlcanceInterno(
            ContextoRecomendacionesIAService.Resultado resultado,
            List<CatalogoAccionesAmbientalesService.Candidata> permitidas,
            Set<AccionAmbientalAdmisible.Referencia> realizadas) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("La seleccion IA requiere ejecutarse fuera de una transaccion activa");
        return generarConAlcance(resultado, permitidas, realizadas);
    }

    private ResultadoGeneracionFundamentadaIADTO generarConAlcance(
            ContextoRecomendacionesIAService.Resultado resultado,
            List<CatalogoAccionesAmbientalesService.Candidata> permitidas,
            Set<AccionAmbientalAdmisible.Referencia> realizadas) {
        var verificado = contextoDeResultado(resultado);
        var contexto = verificado.contexto();
        var alcance = validadorV2.construirAlcanceExperimental(contexto, permitidas, realizadas);
        return seleccionarV2(verificado, alcance.permitidas(), Set.copyOf(realizadas), alcance);
    }

    private ResultadoGeneracionFundamentadaIADTO seleccionarV2(ContextoRecomendacionesIAService.Resultado resultado,
            List<CatalogoAccionesAmbientalesService.Candidata> candidatas,
            Set<AccionAmbientalAdmisible.Referencia> historial, com.huellago.backend.dtos.AlcanceSeleccionIADTO alcance) {
        var contexto = resultado.contexto();
        var datos = ValidadorRecomendacionesIA.contexto(contexto);
        if (candidatas.isEmpty()) {
            // Ausencia de propuesta, no abstención inferida: sin HTTP, sin JSON de modelo ni decisión fabricada.
            // Estado de flujo pendiente; no es aceptación estructural de una respuesta inexistente.
            return new ResultadoGeneracionFundamentadaIADTO(null, null,
                    new com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO(
                            com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.PENDIENTE_REVISION,
                            List.of(), List.of()));
        }
        String solicitud = solicitudV2(resultado, candidatas, alcance);
        if (solicitud.getBytes(StandardCharsets.UTF_8).length > 16_384 && alcance != null
                && resultado.resumenHuella() != null && resultado.resumenHuella().focos().isEmpty())
            solicitud = solicitudV2(resultado,candidatas,alcance,true);
        String contenido = conversar(solicitud);
        try {
            // Sintaxis estricta antes de validar semántica verificable; no se reparan respuestas del modelo.
            mapper.readTree(contenido);
            var evaluacion = alcance == null ? validadorV2.validar(contenido, contexto,
                    candidatas.stream().map(CatalogoAccionesAmbientalesService.Candidata::definicion).toList(), historial)
                    : validadorV2.validarConAlcanceExperimental(contenido, contexto, alcance, historial);
            if (evaluacion.motivos().contains(Motivo.JSON_INVALIDO)) throw ValidadorRecomendacionesIA.respuestaInvalida();
            var propuesta = evaluacion.motivos().contains(Motivo.CONTRATO_INVALIDO) ? null
                    : mapper.readValue(contenido, RecomendacionFundamentadaIADTO.class);
            return new ResultadoGeneracionFundamentadaIADTO(contenido, propuesta, evaluacion);
        } catch (JacksonException error) {
            throw ValidadorRecomendacionesIA.respuestaInvalida();
        }
    }

    /** Construccion compartida con pruebas de bytes; conversar impone el limite antes de transmitir. */
    String solicitudV2(ContextoRecomendacionesIAService.Resultado resultado,
            List<CatalogoAccionesAmbientalesService.Candidata> candidatas, com.huellago.backend.dtos.AlcanceSeleccionIADTO alcance) {
        return solicitudV2(resultado,candidatas,alcance,false);
    }
    private String solicitudV2(ContextoRecomendacionesIAService.Resultado resultado,
            List<CatalogoAccionesAmbientalesService.Candidata> candidatas, com.huellago.backend.dtos.AlcanceSeleccionIADTO alcance,boolean compacta) {
        var datos = ValidadorRecomendacionesIA.contexto(resultado.contexto());
        String sistema = alcance == null ? ContratoOllamaV2.sistema() : ContratoOllamaV2.sistemaConAlcance();
        if (resultado.resumenHuella() != null) sistema += compacta
                ? " referenciasNumericas: huella semanal orientativa de alcance mixto personal/hogar, no impacto personal comparable, evidencia ni ahorro. Úsala solo como contexto de acciones permitidas; no atribuyas energía del hogar a la persona ni inventes prioridades o CO2 evitado."
                : ContratoOllamaV2.usoResumen();
        return mapper.writeValueAsString(Map.of("model", ajustes.modelo(), "stream", false,
                "format", alcance == null ? ContratoOllamaV2.esquema(datos, candidatas) : ContratoOllamaV2.esquemaConAlcance(datos, candidatas),
                "messages", List.of(Map.of("role", "system", "content", sistema),
                        Map.of("role", "user", "content", mapper.writeValueAsString(compacta
                                ? ContratoOllamaV2.datosCompactos(datos,candidatas,resultado.resumenHuella())
                                : ContratoOllamaV2.datos(datos, candidatas, resultado.resumenHuella()))))));
    }

    private List<RecomendacionGeneradaIADTO> generarConReglas(ContextoRecomendacionesIADTO contexto, List<String> reglas) {
        Map<String, Object> datos = ValidadorRecomendacionesIA.contexto(contexto);
        String solicitud;
        try {
            solicitud = mapper.writeValueAsString(Map.of("model", ajustes.modelo(), "stream", false,
                    "format", ValidadorRecomendacionesIA.esquema(datos.keySet()),
                    "messages", List.of(Map.of("role", "system", "content",
                            ReglasInterpretacionAmbiental.mensajeSistema(reglas)),
                            Map.of("role", "user", "content", mapper.writeValueAsString(datos)))));
        } catch (JacksonException error) {
            throw ValidadorRecomendacionesIA.entrada();
        }
        try {
            return ValidadorRecomendacionesIA.respuesta(mapper.readTree(conversar(solicitud)), datos.keySet());
        } catch (JacksonException error) {
            throw ValidadorRecomendacionesIA.respuestaInvalida();
        }
    }

    private String conversar(String solicitud) {
        if (solicitud.getBytes(StandardCharsets.UTF_8).length > 16_384) throw ValidadorRecomendacionesIA.entrada();
        try {
            String cuerpo = cliente.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON).body(solicitud).exchange((peticion, respuesta) -> {
                        if (!respuesta.getStatusCode().is2xxSuccessful())
                            throw new OllamaClientException(ERROR_HTTP, "Ollama devolvió un error HTTP", respuesta.getStatusCode().value());
                        byte[] bytes = respuesta.getBody().readNBytes(ajustes.maxRespuestaBytes() + 1);
                        if (bytes.length == 0 || bytes.length > ajustes.maxRespuestaBytes())
                            throw ValidadorRecomendacionesIA.respuestaInvalida();
                        try {
                            return StandardCharsets.UTF_8.newDecoder()
                                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                        } catch (java.nio.charset.CharacterCodingException error) {
                            throw ValidadorRecomendacionesIA.respuestaInvalida();
                        }
                    });
            var respuesta = mapper.readTree(cuerpo);
            if (respuesta == null || !respuesta.isObject() || respuesta.has("error")
                    || !respuesta.path("done").isBoolean() || !respuesta.path("done").asBoolean()
                    || !ajustes.modelo().equals(ValidadorRecomendacionesIA.texto(respuesta.get("model"), 128))
                    || (respuesta.has("done_reason") && !"stop".equals(respuesta.path("done_reason").asString())))
                throw ValidadorRecomendacionesIA.respuestaInvalida();
            var mensaje = respuesta.path("message");
            if (!mensaje.isObject() || !"assistant".equals(mensaje.path("role").asString()))
                throw ValidadorRecomendacionesIA.respuestaInvalida();
            String contenido = ValidadorRecomendacionesIA.texto(mensaje.get("content"), ajustes.maxRespuestaBytes());
            return contenido;
        } catch (JacksonException error) {
            throw ValidadorRecomendacionesIA.respuestaInvalida();
        } catch (ResourceAccessException error) {
            for (Throwable causa = error; causa != null; causa = causa.getCause()) {
                if (causa instanceof HttpTimeoutException || causa instanceof SocketTimeoutException)
                    throw new OllamaClientException(TIEMPO_AGOTADO, "Se agotó el tiempo de espera de Ollama");
            }
            throw new OllamaClientException(NO_DISPONIBLE, "No se pudo conectar con Ollama");
        } catch (RestClientException error) {
            throw new OllamaClientException(NO_DISPONIBLE, "No se pudo completar la comunicación con Ollama");
        }
    }
}
