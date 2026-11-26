package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ContextoRecomendacionesIADTO;
import com.huellago.backend.dtos.AlcanceSeleccionIADTO;
import com.huellago.backend.services.CatalogoAccionesAmbientalesService.Candidata;
import com.huellago.backend.dtos.RecomendacionFundamentadaIADTO;
import com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO;
import com.huellago.backend.services.AccionAmbientalAdmisible;
import com.huellago.backend.services.AccionAmbientalAdmisible.Referencia;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Motivo.*;

/**
 * Evaluador aislado sin Spring, red, persistencia ni generación de consejos. Rechazo global si falla cualquier categoría.
 * La aceptación estructural NO autoriza publicación ni certifica ciencia. Todo texto libre válido requiere revisión;
 * no se intenta detectar contradicciones mediante filtros léxicos. Compatibilidad y suficiencia dependen también
 * de la calidad de las definiciones suministradas por el llamador. No interpreta predicados ni rutas arbitrarias.
 */
public final class ValidadorRecomendacionFundamentadaIA {
    private record Campo(String categoria, TipoValor tipo, Ambito ambito) { }
    private static final Map<String, Campo> CAMPOS = campos();
    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(15)
                    .maxStringLength(4000).maxNumberLength(100).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    public ResultadoEvaluacionRecomendacionIADTO validar(String json, ContextoRecomendacionesIADTO contexto,
            List<AccionAmbientalAdmisible> acciones, Set<Referencia> realizadas) {
        return validarInterno(json, contexto, acciones, realizadas, null);
    }

    public AlcanceSeleccionIADTO construirAlcanceExperimental(ContextoRecomendacionesIADTO contexto,
            List<Candidata> permitidas, Set<Referencia> realizadas) {
        var datos = ValidadorRecomendacionesIA.contexto(contexto);
        if (permitidas == null || realizadas == null || permitidas.stream().anyMatch(Objects::isNull)
                || realizadas.stream().anyMatch(Objects::isNull)) throw ValidadorRecomendacionesIA.entrada();
        var copia = List.copyOf(permitidas);
        var canonicas = new CatalogoAccionesAmbientalesServiceImpl().compatibles(contexto, Set.copyOf(realizadas));
        Set<Referencia> claves = new HashSet<>();
        for (var c : copia) {
            if (!canonicas.contains(c) || !claves.add(new Referencia(c.definicion().id(), c.definicion().version())))
                throw ValidadorRecomendacionesIA.entrada();
        }
        Set<String> seleccionables = new HashSet<>();
        copia.forEach(c -> seleccionables.add(c.definicion().categoria()));
        Set<String> excluidas = new HashSet<>(datos.keySet()); excluidas.removeAll(seleccionables);
        Set<String> incompletas = new HashSet<>(Set.of("Transporte", "Energía", "Alimentación", "Residuos"));
        incompletas.removeAll(datos.keySet());
        return new AlcanceSeleccionIADTO(datos.keySet(), canonicas, copia, seleccionables, excluidas, incompletas);
    }

    public ResultadoEvaluacionRecomendacionIADTO validarConAlcanceExperimental(String json,
            ContextoRecomendacionesIADTO contexto, AlcanceSeleccionIADTO alcance, Set<Referencia> realizadas) {
        try {
            if (alcance == null || !alcance.equals(construirAlcanceExperimental(contexto, alcance.permitidas(), realizadas))
                    || alcance.seleccionables().isEmpty()) return rechazo(DEFINICIONES_INVALIDAS);
        } catch (com.huellago.backend.exceptions.OllamaClientException | IllegalArgumentException e) {
            return rechazo(DEFINICIONES_INVALIDAS);
        }
        return validarInterno(json, contexto, alcance.permitidas().stream().map(Candidata::definicion).toList(),
                realizadas, alcance.seleccionables());
    }

    private ResultadoEvaluacionRecomendacionIADTO validarInterno(String json, ContextoRecomendacionesIADTO contexto,
            List<AccionAmbientalAdmisible> acciones, Set<Referencia> realizadas, Set<String> esperadas) {
        Map<String, Valor> hechos;
        Set<String> categorias;
        try {
            var datos = ValidadorRecomendacionesIA.contexto(contexto);
            categorias = esperadas == null ? datos.keySet() : esperadas;
            hechos = hechos(datos);
        } catch (com.huellago.backend.exceptions.OllamaClientException error) {
            return rechazo(CONTEXTO_INVALIDO);
        }
        if (acciones == null || realizadas == null || acciones.size() > 100 || !definicionesValidas(acciones))
            return rechazo(DEFINICIONES_INVALIDAS);
        RecomendacionFundamentadaIADTO contrato;
        try {
            if (json == null || json.length() > 65536 || json.getBytes(StandardCharsets.UTF_8).length > 65536)
                return rechazo(JSON_INVALIDO);
            contrato = leer(mapper.readTree(json));
        } catch (JacksonException error) {
            return rechazo(JSON_INVALIDO);
        } catch (IllegalArgumentException error) {
            return rechazo(CONTRATO_INVALIDO);
        }
        List<ResultadoEvaluacionRecomendacionIADTO.Detalle> detalles = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        Set<Referencia> seleccionadas = new HashSet<>();
        LinkedHashSet<ResultadoEvaluacionRecomendacionIADTO.Motivo> globales = new LinkedHashSet<>();
        for (Propuesta p : contrato.resultados()) {
            LinkedHashSet<ResultadoEvaluacionRecomendacionIADTO.Motivo> errores = new LinkedHashSet<>();
            if (!categorias.contains(p.categoria())) errores.add(CATEGORIA_AUSENTE);
            if (!vistas.add(p.categoria())) errores.add(CATEGORIA_DUPLICADA);
            AccionAmbientalAdmisible accion = null;
            Set<String> rutas = new HashSet<>();
            for (Evidencia e : p.evidencias()) {
                Campo campo = CAMPOS.get(e.ruta());
                if (campo == null || !campo.categoria().equals(p.categoria())) { errores.add(RUTA_NO_AUTORIZADA); continue; }
                if (!rutas.add(e.ruta())) errores.add(EVIDENCIA_DUPLICADA);
                if (e.ambito() != campo.ambito()) errores.add(AMBITO_INVALIDO);
                Valor hecho = hechos.get(e.ruta());
                if (hecho == null) errores.add(CAMPO_AUSENTE);
                else if (!iguales(hecho, e.valor())) errores.add(EVIDENCIA_NO_COINCIDE);
            }
            if (p.decision() == Decision.PROPONER) {
                accion = acciones.stream().filter(a -> a.id().equals(p.accion().id())
                        && a.version().equals(p.accion().version()) && a.categoria().equals(p.categoria())).findFirst().orElse(null);
                if (accion == null) errores.add(acciones.stream().anyMatch(a -> a.id().equals(p.accion().id())
                        && a.categoria().equals(p.categoria())) ? VERSION_ACCION_INVALIDA : ACCION_NO_AUTORIZADA);
                Referencia ref = new Referencia(p.accion().id(), p.accion().version());
                if (realizadas.contains(ref) || !seleccionadas.add(ref)) errores.add(ACCION_REPETIDA);
                if (accion != null) {
                    for (var condicion : accion.condiciones().entrySet()) {
                        Valor actual = hechos.get(condicion.getKey());
                        if (actual == null || condicion.getValue().stream().noneMatch(v -> iguales(v, actual)))
                            errores.add(ACCION_INCOMPATIBLE);
                    }
                    if (rutas.isEmpty() || !rutas.containsAll(accion.evidenciasRequeridas())) errores.add(EVIDENCIAS_INSUFICIENTES);
                    validarParametros(p.accion(), accion, errores);
                }
            }
            Set<String> informacionPermitida = new HashSet<>();
            if (accion != null) informacionPermitida.addAll(accion.informacionAdicionalPermitida());
            // La abstención no prescribe una acción; puede solicitar información definida para su categoría.
            if (p.decision() == Decision.ABSTENERSE) acciones.stream().filter(a -> a.categoria().equals(p.categoria()))
                    .forEach(a -> informacionPermitida.addAll(a.informacionAdicionalPermitida()));
            if (!informacionPermitida.containsAll(p.informacionAdicional())) errores.add(INFORMACION_ADICIONAL_NO_AUTORIZADA);
            var estado = errores.isEmpty() ? (p.titulo() == null ? ACEPTADA_ESTRUCTURALMENTE : PENDIENTE_REVISION) : RECHAZADA;
            if (estado == PENDIENTE_REVISION) errores.add(TEXTO_LIBRE_REQUIERE_REVISION);
            detalles.add(new ResultadoEvaluacionRecomendacionIADTO.Detalle(p.categoria(), estado, List.copyOf(errores)));
            if (estado == RECHAZADA) globales.addAll(errores);
        }
        if (!vistas.equals(categorias)) globales.add(CATEGORIAS_INCOMPLETAS);
        var estado = !globales.isEmpty() ? RECHAZADA
                : detalles.stream().anyMatch(d -> d.estado() == PENDIENTE_REVISION) ? PENDIENTE_REVISION : ACEPTADA_ESTRUCTURALMENTE;
        if (estado == PENDIENTE_REVISION) globales.add(TEXTO_LIBRE_REQUIERE_REVISION);
        return new ResultadoEvaluacionRecomendacionIADTO(estado, detalles, List.copyOf(globales));
    }

    private RecomendacionFundamentadaIADTO leer(JsonNode raiz) {
        objeto(raiz, "versionContrato", "resultados");
        exigir("2".equals(texto(raiz.get("versionContrato"), 1)));
        JsonNode resultados = raiz.get("resultados"); lista(resultados, 4); exigir(!resultados.isEmpty());
        List<Propuesta> propuestas = new ArrayList<>();
        for (JsonNode n : resultados) {
            objeto(n, "categoria", "decision", "accion", "evidencias", "titulo", "descripcion", "motivo", "informacionAdicional", "motivoAbstencion");
            String categoria = texto(n.get("categoria"), 32);
            exigir(Set.of("Transporte", "Energía", "Alimentación", "Residuos").contains(categoria));
            Decision decision = Decision.valueOf(texto(n.get("decision"), 16));
            Accion accion = null;
            if (!n.get("accion").isNull()) {
                JsonNode a = n.get("accion"); objeto(a, "id", "version", "parametros");
                JsonNode parametros = a.get("parametros"); exigir(parametros.isObject() && parametros.size() <= 12);
                Map<String, Valor> valores = new LinkedHashMap<>();
                for (var propiedad : parametros.properties()) valores.put(identificador(propiedad.getKey()), valor(propiedad.getValue()));
                accion = new Accion(identificador(texto(a.get("id"), 64)), identificador(texto(a.get("version"), 64)), valores);
            }
            lista(n.get("evidencias"), 13);
            List<Evidencia> evidencias = new ArrayList<>();
            for (JsonNode e : n.get("evidencias")) {
                objeto(e, "ruta", "valor", "ambito");
                evidencias.add(new Evidencia(texto(e.get("ruta"), 100), valor(e.get("valor")), Ambito.valueOf(texto(e.get("ambito"), 16))));
            }
            String titulo = textoOpcional(n.get("titulo"), 160);
            String descripcion = textoOpcional(n.get("descripcion"), 1000);
            String motivo = textoOpcional(n.get("motivo"), 1000);
            exigir((titulo == null && descripcion == null && motivo == null) || (titulo != null && descripcion != null && motivo != null));
            lista(n.get("informacionAdicional"), 12);
            List<String> informacion = new ArrayList<>();
            for (JsonNode i : n.get("informacionAdicional")) informacion.add(identificador(texto(i, 64)));
            exigir(new HashSet<>(informacion).size() == informacion.size());
            MotivoAbstencion abstencion = n.get("motivoAbstencion").isNull() ? null
                    : MotivoAbstencion.valueOf(texto(n.get("motivoAbstencion"), 32));
            if (decision == Decision.PROPONER) exigir(accion != null && abstencion == null);
            else exigir(accion == null && evidencias.isEmpty() && titulo == null && abstencion != null);
            propuestas.add(new Propuesta(categoria, decision, accion, evidencias, titulo, descripcion, motivo, informacion, abstencion));
        }
        return new RecomendacionFundamentadaIADTO("2", propuestas);
    }

    private Valor valor(JsonNode n) {
        objeto(n, "tipo", "valor"); JsonNode v = n.get("valor");
        return switch (TipoValor.valueOf(texto(n.get("tipo"), 16))) {
            case TEXTO -> new Texto(texto(v, 160));
            case NUMERO -> { exigir(v.isNumber()); yield new Numero(new BigDecimal(v.toString())); }
            case BOOLEANO -> { exigir(v.isBoolean()); yield new Booleano(v.asBoolean()); }
        };
    }

    private void validarParametros(Accion propuesta, AccionAmbientalAdmisible definicion,
            Set<ResultadoEvaluacionRecomendacionIADTO.Motivo> errores) {
        propuesta.parametros().forEach((nombre, valor) -> {
            var regla = definicion.parametros().get(nombre);
            if (regla == null) errores.add(PARAMETRO_NO_AUTORIZADO);
            else if (tipo(valor) != regla.tipo() || regla.valoresPermitidos().stream().noneMatch(v -> iguales(v, valor))) errores.add(PARAMETRO_INVALIDO);
        });
        definicion.parametros().forEach((nombre, regla) -> {
            if (regla.obligatorio() && !propuesta.parametros().containsKey(nombre)) errores.add(PARAMETRO_INVALIDO);
        });
    }

    private boolean definicionesValidas(List<AccionAmbientalAdmisible> acciones) {
        Set<Referencia> claves = new HashSet<>();
        for (var a : acciones) {
            if (a == null || !idValido(a.id()) || !idValido(a.version())
                    || !claves.add(new Referencia(a.id(), a.version())) || a.evidenciasRequeridas().isEmpty()
                    || !a.evidenciasRequeridas().containsAll(a.condiciones().keySet())) return false;
            for (String ruta : a.evidenciasRequeridas()) {
                Campo c = CAMPOS.get(ruta);
                if (c == null || !c.categoria().equals(a.categoria())) return false;
            }
            for (var c : a.condiciones().entrySet()) {
                if (c.getValue().isEmpty() || c.getValue().stream().anyMatch(v -> !valorValido(v) || tipo(v) != CAMPOS.get(c.getKey()).tipo())) return false;
            }
            for (var p : a.parametros().entrySet()) {
                if (!idValido(p.getKey()) || p.getValue().tipo() == null || p.getValue().valoresPermitidos().isEmpty()
                        || p.getValue().valoresPermitidos().stream().anyMatch(v -> !valorValido(v) || tipo(v) != p.getValue().tipo())) return false;
            }
            if (a.informacionAdicionalPermitida().stream().anyMatch(s -> !idValido(s))) return false;
        }
        return true;
    }

    private static Map<String, Valor> hechos(Map<String, Object> datos) {
        Map<String, Valor> hechos = new HashMap<>();
        for (var e : CAMPOS.entrySet()) {
            Object grupo = datos.get(e.getValue().categoria());
            if (grupo instanceof Map<?, ?> campos) {
                Object valor = campos.get(e.getKey().substring(e.getKey().lastIndexOf('/') + 1));
                if (valor instanceof String s) hechos.put(e.getKey(), new Texto(s));
                else if (valor instanceof Boolean b) hechos.put(e.getKey(), new Booleano(b));
                else if (valor instanceof Number n) hechos.put(e.getKey(), new Numero(new BigDecimal(n.toString())));
            }
        }
        return hechos;
    }

    private static Map<String, Campo> campos() {
        Map<String, Campo> c = new LinkedHashMap<>();
        for (String s : List.of("medio", "kmSemana", "diasSemana")) c.put("/transporte/" + s,
                new Campo("Transporte", s.equals("medio") ? TipoValor.TEXTO : TipoValor.NUMERO, Ambito.NO_APLICA));
        for (String s : List.of("vivienda", "personas", "fuente", "consumoKwhDiario")) c.put("/energia/" + s,
                new Campo("Energía", Set.of("personas", "consumoKwhDiario").contains(s) ? TipoValor.NUMERO : TipoValor.TEXTO,
                        s.equals("consumoKwhDiario") ? Ambito.HOGAR : Ambito.NO_APLICA));
        c.put("/alimentacion/tipo", new Campo("Alimentación", TipoValor.TEXTO, Ambito.NO_APLICA));
        c.put("/residuos/plasticos", new Campo("Residuos", TipoValor.TEXTO, Ambito.NO_APLICA));
        c.put("/residuos/reciclas", new Campo("Residuos", TipoValor.BOOLEANO, Ambito.NO_APLICA));
        return Map.copyOf(c);
    }

    private static boolean iguales(Valor a, Valor b) {
        if (a instanceof Numero x && b instanceof Numero y) return x.valor().compareTo(y.valor()) == 0;
        return a.equals(b);
    }
    private static TipoValor tipo(Valor v) {
        return v instanceof Numero ? TipoValor.NUMERO : v instanceof Booleano ? TipoValor.BOOLEANO : TipoValor.TEXTO;
    }
    private static boolean valorValido(Valor v) {
        return v instanceof Numero n && n.valor() != null || v instanceof Booleano b && b.valor() != null
                || v instanceof Texto t && t.valor() != null && !t.valor().isBlank() && t.valor().length() <= 160;
    }
    private static String texto(JsonNode n, int max) {
        exigir(n != null && n.isString()); String s = n.asString();
        exigir(!s.isBlank() && s.length() <= max && s.codePoints().noneMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'));
        return s;
    }
    private static String textoOpcional(JsonNode n, int max) { return n.isNull() ? null : texto(n, max); }
    private static void lista(JsonNode n, int max) { exigir(n != null && n.isArray() && n.size() <= max); }
    private static void objeto(JsonNode n, String... campos) {
        exigir(n != null && n.isObject() && n.size() == campos.length);
        for (String c : campos) exigir(n.has(c));
    }
    private static boolean idValido(String s) { return s != null && s.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}"); }
    private static String identificador(String s) { exigir(idValido(s)); return s; }
    private static void exigir(boolean condicion) { if (!condicion) throw new IllegalArgumentException("Contrato V2 inválido"); }
    private static ResultadoEvaluacionRecomendacionIADTO rechazo(ResultadoEvaluacionRecomendacionIADTO.Motivo motivo) {
        return new ResultadoEvaluacionRecomendacionIADTO(RECHAZADA, List.of(), List.of(motivo));
    }
}
