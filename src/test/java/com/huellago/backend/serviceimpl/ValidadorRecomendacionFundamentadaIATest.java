package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ContextoRecomendacionesIADTO;
import com.huellago.backend.dtos.RecomendacionFundamentadaIADTO;
import com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO;
import com.huellago.backend.services.AccionAmbientalAdmisible;
import com.huellago.backend.services.AccionAmbientalAdmisible.Referencia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.*;

import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Motivo.*;
import static org.junit.jupiter.api.Assertions.*;

/** Únicamente datos sintéticos; no Spring, persistencia, inferencia ni catálogo ambiental de producción. */
class ValidadorRecomendacionFundamentadaIATest {
    final JsonMapper mapper = JsonMapper.builder().build();
    final ValidadorRecomendacionFundamentadaIA validador = new ValidadorRecomendacionFundamentadaIA();
    final ContextoRecomendacionesIADTO residuos = new ContextoRecomendacionesIADTO(null, null, null,
            new ContextoRecomendacionesIADTO.Residuos("Medio", true));
    final AccionAmbientalAdmisible revision = accion("TEST_REVISION", "Residuos", "/residuos/reciclas", new Booleano(true));

    @Test void propuestaSinTextoAceptadaSoloEstructuralmente() {
        var r = evaluar(propuesta(), residuos, List.of(revision));
        assertEquals(ACEPTADA_ESTRUCTURALMENTE, r.estado());
        assertTrue(r.motivos().isEmpty()); assertEquals(1, r.evaluaciones().size());
    }

    @Test void textoContradictorioNoSeCertificaNiSeFingeDetectarlo() {
        var p = propuesta();
        p.put("titulo", "Nunca reciclas"); p.put("descripcion", "Debes comenzar a reciclar.");
        p.put("motivo", "No has reciclado nunca.");
        var r = evaluar(p, residuos, List.of(revision));
        assertEquals(PENDIENTE_REVISION, r.estado());
        assertEquals(List.of(TEXTO_LIBRE_REQUIERE_REVISION), r.motivos());
        assertFalse(r.motivos().contains(ACCION_INCOMPATIBLE));
    }

    @Test void textoSinContradiccionesTambienRequiereRevision() {
        var p = propuesta(); p.put("titulo", "Acción ficticia"); p.put("descripcion", "Revisión ficticia.");
        p.put("motivo", "Registraste que reciclas.");
        assertEquals(PENDIENTE_REVISION, evaluar(p, residuos, List.of(revision)).estado());
    }

    @Test void vegetarianoRechazaObligacionDeCarnePorCompatibilidadDeclarada() {
        var contexto = new ContextoRecomendacionesIADTO(null, null, new ContextoRecomendacionesIADTO.Alimentacion("Vegetariana"), null);
        var incompatible = accion("TEST_CARNE", "Alimentación", "/alimentacion/tipo", new Texto("Mixta"));
        var p = base("Alimentación", "TEST_CARNE", "/alimentacion/tipo", "TEXTO", "Vegetariana", "NO_APLICA");
        rechaza(evaluar(p, contexto, List.of(incompatible)), ACCION_INCOMPATIBLE);
        // El validador no interpreta el nombre TEST_CARNE: la prohibición depende de condiciones revisadas.
    }

    @Test void bicicletaNoPermiteEvidenciaAuto() {
        var c = new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte("Bicicleta", BigDecimal.TEN, 4), null, null, null);
        var a = accion("TEST_BICI", "Transporte", "/transporte/medio", new Texto("Bicicleta"));
        rechaza(evaluar(base("Transporte", "TEST_BICI", "/transporte/medio", "TEXTO", "Auto", "NO_APLICA"), c, List.of(a)), EVIDENCIA_NO_COINCIDE);
    }

    @Test void yaReciclaNoPermiteAccionDeIniciar() {
        var a = accion("TEST_INICIAR", "Residuos", "/residuos/reciclas", new Booleano(false));
        var p = propuesta(); seleccion(p).put("id", "TEST_INICIAR");
        rechaza(evaluar(p, residuos, List.of(a)), ACCION_INCOMPATIBLE);
    }

    @Test void accionNoAdmisibleNoSeSustituyePorFallback() {
        rechaza(evaluar(propuesta(), residuos, List.of()), ACCION_NO_AUTORIZADA);
    }

    @Test void consumoAusenteNoAdmiteEvidenciaInventada() {
        var c = energia(null);
        var a = accion("TEST_CONSUMO", "Energía", "/energia/consumoKwhDiario", new Numero(BigDecimal.TEN));
        var p = base("Energía", "TEST_CONSUMO", "/energia/consumoKwhDiario", "NUMERO", BigDecimal.TEN, "HOGAR");
        var r = evaluar(p, c, List.of(a));
        rechaza(r, CAMPO_AUSENTE); assertTrue(r.motivos().contains(ACCION_INCOMPATIBLE));
    }

    @ParameterizedTest @ValueSource(strings = {"12.345", "12.3450", "1.2345E+1"})
    void consumoHogarBigDecimalSinPerderPrecision(String numero) {
        var c = energia(new BigDecimal("12.345"));
        var a = accion("TEST_CONSUMO", "Energía", "/energia/consumoKwhDiario", new Numero(new BigDecimal("12.345")));
        var p = base("Energía", "TEST_CONSUMO", "/energia/consumoKwhDiario", "NUMERO", new BigDecimal(numero), "HOGAR");
        assertEquals(ACEPTADA_ESTRUCTURALMENTE, evaluar(p, c, List.of(a)).estado());
        assertEquals(new BigDecimal("12.345"), c.energia().consumoKwhDiario());
    }

    @ParameterizedTest @ValueSource(strings = {"4.115", "37.035", "12.345000000000000000000000001"})
    void noSeDivideMultiplicaNiToleraAlteracionNumerica(String numero) {
        var c = energia(new BigDecimal("12.345"));
        var a = accion("TEST_CONSUMO", "Energía", "/energia/consumoKwhDiario", new Numero(new BigDecimal("12.345")));
        rechaza(evaluar(base("Energía", "TEST_CONSUMO", "/energia/consumoKwhDiario", "NUMERO", new BigDecimal(numero), "HOGAR"), c, List.of(a)), EVIDENCIA_NO_COINCIDE);
    }

    @Test void consumoCeroEsRealNoAusente() {
        var a = accion("TEST_CERO", "Energía", "/energia/consumoKwhDiario", new Numero(BigDecimal.ZERO));
        var p = base("Energía", "TEST_CERO", "/energia/consumoKwhDiario", "NUMERO", BigDecimal.ZERO, "HOGAR");
        assertEquals(ACEPTADA_ESTRUCTURALMENTE, evaluar(p, energia(BigDecimal.ZERO), List.of(a)).estado());
    }

    @Test void hogarNoAceptaNoAplica() {
        var a = accion("TEST_CONSUMO", "Energía", "/energia/consumoKwhDiario", new Numero(BigDecimal.TEN));
        rechaza(evaluar(base("Energía", "TEST_CONSUMO", "/energia/consumoKwhDiario", "NUMERO", BigDecimal.TEN, "NO_APLICA"), energia(BigDecimal.TEN), List.of(a)), AMBITO_INVALIDO);
    }

    @Test void ambitoIndividualNoExisteEnContrato() {
        var p = propuesta(); evidencias(p).getFirst().put("ambito", "INDIVIDUAL");
        rechaza(evaluar(p, residuos, List.of(revision)), CONTRATO_INVALIDO);
    }

    @ParameterizedTest @ValueSource(strings = {"/usuario/correo", "/residuos/__proto__", "/residuos/nivelReciclaje", "/residuos/reciclas/extra", "/energia/origen", "/Residuos/reciclas"})
    void rutasCerradasSinAccesoArbitrario(String ruta) {
        var p = propuesta(); evidencias(p).getFirst().put("ruta", ruta);
        rechaza(evaluar(p, residuos, List.of(revision)), RUTA_NO_AUTORIZADA);
    }

    @Test void rutaDeOtraCategoriaNoSirveAunqueExista() {
        var p = propuesta(); evidencias(p).getFirst().put("ruta", "/transporte/medio");
        rechaza(evaluar(p, residuos, List.of(revision)), RUTA_NO_AUTORIZADA);
    }

    @ParameterizedTest @ValueSource(strings = {"TEXTO", "NUMERO"})
    void booleanoNoSeConvierteDesdeTextoONumero(String tipo) {
        var p = propuesta(); evidencias(p).getFirst().put("valor", valor(tipo, tipo.equals("TEXTO") ? "true" : 1));
        rechaza(evaluar(p, residuos, List.of(revision)), EVIDENCIA_NO_COINCIDE);
    }

    @Test void booleanoAlteradoSeRechaza() {
        var p = propuesta(); evidencias(p).getFirst().put("valor", valor("BOOLEANO", false));
        rechaza(evaluar(p, residuos, List.of(revision)), EVIDENCIA_NO_COINCIDE);
    }

    @Test void categoriasAusentesYDuplicadas() {
        var p = propuesta(); p.put("categoria", "Transporte");
        rechaza(evaluar(p, residuos, List.of(revision)), CATEGORIA_AUSENTE);
        var r = validador.validar(json(List.of(propuesta(), propuesta())), residuos, List.of(revision), Set.of());
        rechaza(r, CATEGORIA_DUPLICADA);
        var c = new ContextoRecomendacionesIADTO(null, null, new ContextoRecomendacionesIADTO.Alimentacion("Mixta"), residuos.residuos());
        rechaza(evaluar(propuesta(), c, List.of(revision)), CATEGORIAS_INCOMPLETAS);
    }

    @Test void contextoInvalidoNoProduceAprobacion() {
        var c = new ContextoRecomendacionesIADTO(null, new ContextoRecomendacionesIADTO.Energia("Casa", null, "Electricidad", null), null, null);
        rechaza(evaluar(propuesta(), c, List.of(revision)), CONTEXTO_INVALIDO);
        rechaza(evaluar(propuesta(), null, List.of(revision)), CONTEXTO_INVALIDO);
    }

    @ParameterizedTest @ValueSource(strings = {"id", "version"})
    void accionInexistenteOVersionIncorrecta(String campo) {
        var p = propuesta(); seleccion(p).put(campo, "INEXISTENTE");
        rechaza(evaluar(p, residuos, List.of(revision)), campo.equals("id") ? ACCION_NO_AUTORIZADA : VERSION_ACCION_INVALIDA);
    }

    @Test void accionRealizadaSeRechazaSinModificarHistorial() {
        Set<Referencia> realizadas = Set.of(new Referencia("TEST_REVISION", "1"));
        var r = validador.validar(json(List.of(propuesta())), residuos, List.of(revision), realizadas);
        rechaza(r, ACCION_REPETIDA); assertEquals(1, realizadas.size());
    }

    @Test void evidenciasInsuficientesODuplicadas() {
        var p = propuesta(); p.put("evidencias", List.of());
        rechaza(evaluar(p, residuos, List.of(revision)), EVIDENCIAS_INSUFICIENTES);
        p = propuesta(); evidencias(p).add(new LinkedHashMap<>(evidencias(p).getFirst()));
        rechaza(evaluar(p, residuos, List.of(revision)), EVIDENCIA_DUPLICADA);
    }

    @Test void parametroExtraNoAutorizado() {
        var p = propuesta(); seleccion(p).put("parametros", Map.of("correo", valor("TEXTO", "privado@example.invalid")));
        var r = evaluar(p, residuos, List.of(revision));
        rechaza(r, PARAMETRO_NO_AUTORIZADO);
        assertFalse(mapper.writeValueAsString(r).contains("privado@example.invalid"));
    }

    @Test void parametroObligatorioYValoresPermitidosTipados() {
        var a = new AccionAmbientalAdmisible(revision.id(), revision.version(), revision.categoria(), revision.condiciones(),
                revision.evidenciasRequeridas(), Map.of("paso", new AccionAmbientalAdmisible.ParametroPermitido(TipoValor.NUMERO,
                Set.of(new Numero(new BigDecimal("1.00"))), true)), Set.of());
        rechaza(evaluar(propuesta(), residuos, List.of(a)), PARAMETRO_INVALIDO);
        var p = propuesta(); seleccion(p).put("parametros", Map.of("paso", valor("NUMERO", 1)));
        assertEquals(ACEPTADA_ESTRUCTURALMENTE, evaluar(p, residuos, List.of(a)).estado());
        seleccion(p).put("parametros", Map.of("paso", valor("NUMERO", 2)));
        rechaza(evaluar(p, residuos, List.of(a)), PARAMETRO_INVALIDO);
        seleccion(p).put("parametros", Map.of("paso", valor("TEXTO", "1")));
        rechaza(evaluar(p, residuos, List.of(a)), PARAMETRO_INVALIDO);
    }

    @Test void informacionAdicionalSoloIdsAutorizados() {
        var a = new AccionAmbientalAdmisible(revision.id(), revision.version(), revision.categoria(), revision.condiciones(),
                revision.evidenciasRequeridas(), Map.of(), Set.of("TEST_DATO"));
        var p = propuesta(); p.put("informacionAdicional", List.of("TEST_DATO"));
        assertEquals(ACEPTADA_ESTRUCTURALMENTE, evaluar(p, residuos, List.of(a)).estado());
        p.put("informacionAdicional", List.of("NO_AUTORIZADO"));
        rechaza(evaluar(p, residuos, List.of(a)), INFORMACION_ADICIONAL_NO_AUTORIZADA);
    }

    @Test void abstencionValidaNoContieneContenidoPublicable() {
        var p = abstencion();
        assertEquals(ACEPTADA_ESTRUCTURALMENTE, evaluar(p, residuos, List.of()).estado());
    }

    @ParameterizedTest @ValueSource(strings = {"accion", "evidencias", "texto", "sinMotivo"})
    void abstencionNoPuedeIncluirPropuestaDisfrazada(String campo) {
        var p = abstencion();
        switch (campo) {
            case "accion" -> p.put("accion", seleccion(propuesta()));
            case "evidencias" -> p.put("evidencias", evidencias(propuesta()));
            case "texto" -> { p.put("titulo", "Consejo"); p.put("descripcion", "Acción"); p.put("motivo", "Motivo"); }
            default -> p.put("motivoAbstencion", null);
        }
        rechaza(evaluar(p, residuos, List.of(revision)), CONTRATO_INVALIDO);
    }

    @ParameterizedTest @ValueSource(strings = {"sinAccion", "conAbstencion", "textoIncompleto", "categoria", "decision", "extra", "ausente"})
    void contratoEstrictoPorPropuesta(String caso) {
        var p = propuesta();
        switch (caso) {
            case "sinAccion" -> p.put("accion", null);
            case "conAbstencion" -> p.put("motivoAbstencion", "NO_VERIFICABLE");
            case "textoIncompleto" -> p.put("titulo", "Título sin otros textos");
            case "categoria" -> p.put("categoria", "Agua");
            case "decision" -> p.put("decision", "PUBLICAR");
            case "extra" -> p.put("usuarioId", 68);
            default -> p.remove("evidencias");
        }
        rechaza(evaluar(p, residuos, List.of(revision)), CONTRATO_INVALIDO);
    }

    @ParameterizedTest @ValueSource(strings = {"", "null", "[]", "{}", "true", "{", "{} {}", "{\"versionContrato\":\"2\",\"versionContrato\":\"2\",\"resultados\":[]}"})
    void jsonMalFormadoOIncompatibleSiempreControlado(String json) {
        assertEquals(RECHAZADA, validador.validar(json, residuos, List.of(revision), Set.of()).estado());
    }

    @Test void versionCamposRaizYLimitesSeVerifican() {
        var r = new LinkedHashMap<String,Object>(); r.put("versionContrato", "1"); r.put("resultados", List.of(propuesta()));
        rechaza(validador.validar(mapper.writeValueAsString(r), residuos, List.of(revision), Set.of()), CONTRATO_INVALIDO);
        r.put("versionContrato", "2"); r.put("extra", true);
        rechaza(validador.validar(mapper.writeValueAsString(r), residuos, List.of(revision), Set.of()), CONTRATO_INVALIDO);
        rechaza(validador.validar(" ".repeat(65537), residuos, List.of(revision), Set.of()), JSON_INVALIDO);
        var p = propuesta(); p.put("titulo", "x".repeat(161)); p.put("descripcion", "Texto"); p.put("motivo", "Texto");
        rechaza(evaluar(p, residuos, List.of(revision)), CONTRATO_INVALIDO);
    }

    @Test void definicionesInvalidasNoSeConviertenEnAprobacion() {
        var a = new AccionAmbientalAdmisible("TEST", "1", "Residuos", Map.of(), Set.of("/usuario/correo"), Map.of(), Set.of());
        rechaza(evaluar(propuesta(), residuos, List.of(a)), DEFINICIONES_INVALIDAS);
        rechaza(evaluar(propuesta(), residuos, List.of(revision, revision)), DEFINICIONES_INVALIDAS);
        rechaza(validador.validar(json(List.of(propuesta())), residuos, null, Set.of()), DEFINICIONES_INVALIDAS);
    }

    @Test void cuatroCategoriasYOrdenIndependienteConAbstenciones() {
        var c = new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte("Bus", BigDecimal.TEN, 5),
                new ContextoRecomendacionesIADTO.Energia("Casa", 3, "Electricidad", null),
                new ContextoRecomendacionesIADTO.Alimentacion("Mixta"), residuos.residuos());
        List<Map<String,Object>> ps = new ArrayList<>();
        for (String categoria : List.of("Residuos", "Alimentación", "Energía", "Transporte")) {
            var p = abstencion(); p.put("categoria", categoria); ps.add(p);
        }
        var r = validador.validar(json(ps), c, List.of(), Set.of());
        assertEquals(ACEPTADA_ESTRUCTURALMENTE, r.estado()); assertEquals(4, r.evaluaciones().size());
    }

    @Test void dtoV2SerializaValoresTipadosYEsInmutable() {
        var p = new RecomendacionFundamentadaIADTO.Propuesta("Residuos", Decision.PROPONER,
                new Accion("TEST_REVISION", "1", Map.of()), List.of(new Evidencia("/residuos/reciclas", new Booleano(true), Ambito.NO_APLICA)),
                null, null, null, List.of(), null);
        var dto = new RecomendacionFundamentadaIADTO("2", List.of(p));
        var r = validador.validar(mapper.writeValueAsString(dto), residuos, List.of(revision), Set.of());
        assertEquals(ACEPTADA_ESTRUCTURALMENTE, r.estado());
        assertThrows(UnsupportedOperationException.class, () -> dto.resultados().clear());
        assertThrows(UnsupportedOperationException.class, () -> p.accion().parametros().clear());
    }

    private ContextoRecomendacionesIADTO energia(BigDecimal consumo) {
        return new ContextoRecomendacionesIADTO(null, new ContextoRecomendacionesIADTO.Energia("Casa", 3, "Electricidad", consumo), null, null);
    }
    private AccionAmbientalAdmisible accion(String id, String categoria, String ruta, Valor valor) {
        return new AccionAmbientalAdmisible(id, "1", categoria, Map.of(ruta, Set.of(valor)), Set.of(ruta), Map.of(), Set.of());
    }
    private Map<String,Object> propuesta() { return base("Residuos", "TEST_REVISION", "/residuos/reciclas", "BOOLEANO", true, "NO_APLICA"); }
    private Map<String,Object> base(String categoria, String accion, String ruta, String tipo, Object valor, String ambito) {
        Map<String,Object> p = new LinkedHashMap<>(); p.put("categoria", categoria); p.put("decision", "PROPONER");
        p.put("accion", new LinkedHashMap<>(Map.of("id", accion, "version", "1", "parametros", Map.of())));
        p.put("evidencias", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("ruta", ruta, "valor", valor(tipo, valor), "ambito", ambito)))));
        p.put("titulo", null); p.put("descripcion", null); p.put("motivo", null); p.put("informacionAdicional", List.of()); p.put("motivoAbstencion", null);
        return p;
    }
    private Map<String,Object> abstencion() {
        var p = propuesta(); p.put("decision", "ABSTENERSE"); p.put("accion", null); p.put("evidencias", List.of());
        p.put("motivoAbstencion", "NO_VERIFICABLE"); return p;
    }
    private Map<String,Object> valor(String tipo, Object valor) { return Map.of("tipo", tipo, "valor", valor); }
    @SuppressWarnings("unchecked") private Map<String,Object> seleccion(Map<String,Object> p) { return (Map<String,Object>) p.get("accion"); }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> evidencias(Map<String,Object> p) { return (List<Map<String,Object>>) p.get("evidencias"); }
    private String json(List<Map<String,Object>> ps) { return mapper.writeValueAsString(Map.of("versionContrato", "2", "resultados", ps)); }
    private ResultadoEvaluacionRecomendacionIADTO evaluar(Map<String,Object> p, ContextoRecomendacionesIADTO c, List<AccionAmbientalAdmisible> acciones) {
        return validador.validar(json(List.of(p)), c, acciones, Set.of());
    }
    private void rechaza(ResultadoEvaluacionRecomendacionIADTO r, ResultadoEvaluacionRecomendacionIADTO.Motivo motivo) {
        assertEquals(RECHAZADA, r.estado()); assertTrue(r.motivos().contains(motivo), () -> r.toString());
    }
}
