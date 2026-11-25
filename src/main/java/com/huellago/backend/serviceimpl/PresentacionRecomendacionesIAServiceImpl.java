package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.services.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Motivo.*;
import static com.huellago.backend.dtos.ResultadoPresentacionRecomendacionesIADTO.*;

/** Prototipo aislado, sin registro Spring ni dependencias de red/repositorios.
 * Contenido BORRADOR preparado para revisión, no recomendaciones elegidas por Java ni fallback.
 * Se rechaza globalmente una selección inválida, respetando el validador existente. */
public final class PresentacionRecomendacionesIAServiceImpl implements PresentacionRecomendacionesIAService {
    private static final String SIN_ENFOQUE = "SIN_ENFOQUE";
    private static final String VERSION_PRESENTACION = "1";
    private record Clave(String accion, String version, String enfoque) { }
    private record Definicion(String titulo, String paso, String motivo, Set<String> campos) {
        private Definicion { campos = Set.copyOf(campos); }
    }
    private static final Map<Clave, Definicion> PRESENTACIONES = crear();

    @Override public List<DefinicionPresentacionIADTO> definicionesParaRevision() {
        return PRESENTACIONES.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<Clave, Definicion> e) -> e.getKey().accion())
                        .thenComparing(e -> e.getKey().version()).thenComparing(e -> e.getKey().enfoque()))
                .map(e -> new DefinicionPresentacionIADTO(e.getKey().accion(), e.getKey().version(), e.getKey().enfoque(),
                        VERSION_PRESENTACION, e.getValue().titulo(), e.getValue().paso(), e.getValue().motivo(), e.getValue().campos()))
                .toList();
    }
    private final CatalogoAccionesAmbientalesService catalogo = new CatalogoAccionesAmbientalesServiceImpl();
    private final ValidadorRecomendacionFundamentadaIA validador = new ValidadorRecomendacionFundamentadaIA();
    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(20)
                    .maxStringLength(65536).maxNumberLength(100).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    @Override public ResultadoPresentacionRecomendacionesIADTO presentarParaPruebasSinteticas(
            ContextoRecomendacionesIADTO contexto, ResultadoGeneracionFundamentadaIADTO seleccion,
            List<CatalogoAccionesAmbientalesService.Candidata> compatibles, Set<AccionAmbientalAdmisible.Referencia> realizadas) {
        return presentar(contexto, seleccion, compatibles, realizadas, null);
    }

    @Override public ResultadoPresentacionRecomendacionesIADTO presentarConAlcanceParaPruebasSinteticas(
            ContextoRecomendacionesIADTO contexto, ResultadoGeneracionFundamentadaIADTO seleccion,
            AlcanceSeleccionIADTO alcance, Set<AccionAmbientalAdmisible.Referencia> realizadas) {
        return presentarConAlcanceInterno(contexto, seleccion, alcance, realizadas);
    }

    @Override public ResultadoPresentacionRecomendacionesIADTO presentarConAlcanceInterno(
            ContextoRecomendacionesIADTO contexto, ResultadoGeneracionFundamentadaIADTO seleccion,
            AlcanceSeleccionIADTO alcance, Set<AccionAmbientalAdmisible.Referencia> realizadas) {
        try {
            if (alcance == null || !alcance.equals(validador.construirAlcanceExperimental(contexto, alcance.permitidas(), realizadas)))
                return rechazar(DEFINICIONES_INVALIDAS);
        } catch (com.huellago.backend.exceptions.OllamaClientException | IllegalArgumentException e) {
            return rechazar(DEFINICIONES_INVALIDAS);
        }
        return presentar(contexto, seleccion, alcance.permitidas(), realizadas, alcance);
    }

    private ResultadoPresentacionRecomendacionesIADTO presentar(
            ContextoRecomendacionesIADTO contexto, ResultadoGeneracionFundamentadaIADTO seleccion,
            List<CatalogoAccionesAmbientalesService.Candidata> compatibles, Set<AccionAmbientalAdmisible.Referencia> realizadas,
            AlcanceSeleccionIADTO alcance) {
        if (seleccion == null || seleccion.evaluacion() == null) return rechazar(CONTRATO_INVALIDO);
        if (compatibles == null || compatibles.stream().anyMatch(Objects::isNull) || realizadas == null
                || realizadas.stream().anyMatch(Objects::isNull)) return rechazar(DEFINICIONES_INVALIDAS);
        Map<String,Object> datos;
        List<CatalogoAccionesAmbientalesService.Candidata> canonicas;
        try {
            datos = ValidadorRecomendacionesIA.contexto(contexto);
            canonicas = catalogo.compatibles(contexto, Set.copyOf(realizadas));
        } catch (com.huellago.backend.exceptions.OllamaClientException e) { return rechazar(CONTEXTO_INVALIDO); }
        // Las definiciones de entrada no pueden autorizar acciones/condiciones inventadas.
        if (new HashSet<>(compatibles).size() != compatibles.size()
                || !canonicas.containsAll(compatibles)) return rechazar(DEFINICIONES_INVALIDAS);
        if (seleccion.propuesta() == null) {
            if ((alcance == null ? canonicas.isEmpty() : alcance.seleccionables().isEmpty())
                    && compatibles.isEmpty() && seleccion.propuestaJson() == null
                    && seleccion.evaluacion().estado() == PENDIENTE_REVISION
                    && seleccion.evaluacion().motivos().isEmpty() && seleccion.evaluacion().evaluaciones().isEmpty())
                return new ResultadoPresentacionRecomendacionesIADTO(Estado.SIN_PROPUESTA, null, List.of());
            return rechazar(CONTRATO_INVALIDO);
        }
        var evaluacion = alcance == null ? validador.validar(seleccion.propuestaJson(), contexto,
                compatibles.stream().map(CatalogoAccionesAmbientalesService.Candidata::definicion).toList(), realizadas)
                : validador.validarConAlcanceExperimental(seleccion.propuestaJson(), contexto, alcance, realizadas);
        if (evaluacion.estado() != ACEPTADA_ESTRUCTURALMENTE)
            return new ResultadoPresentacionRecomendacionesIADTO(Estado.SELECCION_RECHAZADA, evaluacion, List.of());
        if (seleccion.evaluacion().estado() != ACEPTADA_ESTRUCTURALMENTE) return rechazar(CONTRATO_INVALIDO);
        try {
            if (!mapper.readTree(seleccion.propuestaJson()).equals(mapper.readTree(mapper.writeValueAsString(seleccion.propuesta()))))
                return rechazar(CONTRATO_INVALIDO);
        } catch (JacksonException e) { return rechazar(CONTRATO_INVALIDO); }
        List<Detalle> detalles = new ArrayList<>();
        for (var p : seleccion.propuesta().resultados()) {
            if (p.decision() == Decision.ABSTENERSE) {
                detalles.add(new Detalle(p, EstadoDetalle.ABSTENCION_V2, Motivo.ABSTENCION_ORIGINAL, null)); continue;
            }
            // Ausencia explícita: no seleccionar uno de los enfoques admitidos en nombre del modelo.
            Valor parametro = p.accion().parametros().get("enfoque");
            String enfoque = parametro == null ? SIN_ENFOQUE : ((Texto)parametro).valor();
            var definicion = PRESENTACIONES.get(new Clave(p.accion().id(), p.accion().version(), enfoque));
            if (definicion == null) {
                detalles.add(new Detalle(p, EstadoDetalle.SIN_PRESENTACION, Motivo.COMBINACION_NO_DEFINIDA, null)); continue;
            }
            Map<String,String> valores = valores(definicion.campos(), datos);
            if (valores == null) {
                detalles.add(new Detalle(p, EstadoDetalle.SIN_PRESENTACION, Motivo.DATOS_NO_REPRESENTABLES, null)); continue;
            }
            String motivo = componer(definicion.motivo(), valores);
            String paso = componer(definicion.paso(), valores);
            if (definicion.titulo().length() > 160 || paso.length() > 1000 || motivo.length() > 1000)
                detalles.add(new Detalle(p, EstadoDetalle.SIN_PRESENTACION, Motivo.DATOS_NO_REPRESENTABLES, null));
            else detalles.add(new Detalle(p, EstadoDetalle.VISTA_PREVIA_BORRADOR, Motivo.REVISION_HUMANA_PENDIENTE,
                    new Presentacion(p.accion().id(), p.accion().version(), enfoque, parametro != null, VERSION_PRESENTACION,
                            definicion.titulo(), paso, motivo, definicion.campos(), Revision.BORRADOR, PENDIENTE_REVISION)));
        }
        return new ResultadoPresentacionRecomendacionesIADTO(Estado.SELECCION_ACEPTADA, evaluacion, detalles);
    }

    private static ResultadoPresentacionRecomendacionesIADTO rechazar(ResultadoEvaluacionRecomendacionIADTO.Motivo motivo) {
        return new ResultadoPresentacionRecomendacionesIADTO(Estado.SELECCION_RECHAZADA,
                new ResultadoEvaluacionRecomendacionIADTO(RECHAZADA, List.of(), List.of(motivo)), List.of());
    }
    private static Map<String,String> valores(Set<String> campos, Map<String,Object> datos) {
        Map<String,String> valores = new HashMap<>();
        for (String ruta : campos) {
            String[] partes = ruta.split("/");
            String categoria = switch (partes[1]) {
                case "transporte" -> "Transporte"; case "energia" -> "Energía";
                case "alimentacion" -> "Alimentación"; case "residuos" -> "Residuos";
                default -> throw new IllegalStateException("Ruta interna no admitida");
            };
            Object grupo = datos.get(categoria);
            Object valor = grupo instanceof Map<?,?> m ? m.get(partes[2]) : null;
            if (valor == null) return null;
            if (valor instanceof BigDecimal n) {
                // No redondear ni truncar hechos; evitar expansiones desmesuradas de exponentes.
                if ((long)n.precision() + Math.abs((long)n.scale()) > 100) return null;
                valores.put(ruta, n.stripTrailingZeros().toPlainString());
            } else valores.put(ruta, valor instanceof Boolean b ? (b ? "sí" : "no") : valor.toString());
        }
        return Map.copyOf(valores);
    }
    private static String componer(String texto, Map<String,String> valores) {
        String resultado = texto;
        for (var e : valores.entrySet()) resultado = resultado.replace("{" + e.getKey() + "}", e.getValue());
        if (resultado.contains("{") || resultado.contains("}")) throw new IllegalStateException("Variable interna sin resolver");
        return resultado;
    }

    private static Map<Clave,Definicion> crear() {
        Map<Clave,Definicion> m = new LinkedHashMap<>();
        var t = Set.of("/transporte/medio", "/transporte/kmSemana", "/transporte/diasSemana");
        String mt = "Registraste {/transporte/medio}, {/transporte/kmSemana} km/semana y {/transporte/diasSemana} días/semana. No conocemos tus próximas gestiones.";
        agregar(m,"TR_PLANIFICAR_DESPLAZAMIENTOS","Comprueba cómo organizar tus gestiones",mt,t,
                "Anota tus próximas gestiones y comprueba qué necesidades debe cubrir cada salida antes de organizarla.",
                "NECESIDAD","Anota tus próximas gestiones y comprueba cuáles requieren una salida y cuáles podrían resolverse de otra manera, si encuentras una opción adecuada.",
                "AGRUPACION","Anota tus próximas gestiones y comprueba si dos podrían realizarse en una misma salida sin comprometer tus necesidades.");
        agregar(m,"TR_REVISAR_MOVILIDAD_ACTIVA","Organiza tus gestiones con tu movilidad actual",mt,t,
                "Anota una próxima gestión y comprueba qué necesitas para realizarla con el medio que ya utilizas, considerando horario, carga y seguridad.",
                "ORGANIZACION","Anota tus próximas gestiones y comprueba cuáles podrías organizar en una misma salida con el medio que ya utilizas.",
                "VIABILIDAD","Elige una próxima gestión y comprueba si su horario, carga y condiciones permiten realizarla con el medio que ya utilizas.");
        agregar(m,"EN_REVISAR_REGISTRO_HOGAR","Comprueba tu registro eléctrico",
                "Registraste vivienda {/energia/vivienda}, {/energia/personas} personas y {/energia/fuente}, con {/energia/consumoKwhDiario} kWh/día del hogar. No tenemos información sobre cómo se obtuvo ese valor, qué medición o estimación lo respalda ni su periodo original.",
                Set.of("/energia/vivienda","/energia/personas","/energia/fuente","/energia/consumoKwhDiario"),
                "Consulta de dónde obtuviste el registro eléctrico del hogar y comprueba el periodo y la cobertura de la medición o estimación original.",
                "ALCANCE","Consulta qué periodo y qué parte del hogar cubría la medición o estimación original utilizada para obtener tu registro en kWh/día del hogar.",
                "PROCEDENCIA_REGISTRO","Consulta de dónde obtuviste el valor del consumo eléctrico y anota cómo se calculó o midió, si dispones de esa información.");
        agregar(m,"AL_PLANIFICAR_APROVECHAMIENTO","Consulta lo disponible antes de comprar",
                "Tu alimentación registrada es {/alimentacion/tipo}. No conocemos qué alimentos tienes disponibles.",Set.of("/alimentacion/tipo"),
                "Comprueba qué alimentos tienes disponibles antes de preparar tu próxima lista de compras, respetando tu tipo de alimentación.",
                "PLANIFICACION","Antes de preparar tu próxima lista de compras, consulta qué alimentos tienes disponibles y considera cuáles puedes aprovechar respetando tu tipo de alimentación.",
                "DISPONIBILIDAD","Anota los alimentos que realmente tienes disponibles y comprueba cuáles puedes utilizar respetando tu tipo de alimentación.");
        var r = Set.of("/residuos/plasticos","/residuos/reciclas");
        agregar(m,"RE_INICIAR_SEPARACION","Averigua cómo entregar materiales separados",
                "Registro de reciclaje: {/residuos/reciclas}; uso de plásticos: {/residuos/plasticos}. No conocemos las opciones de gestión de tu zona.",r,
                "Busca información sobre posibles vías de gestión de reciclables en tu zona y, si encuentras alguna, consulta qué materiales admite.",
                "CONDICIONES_LOCALES","Busca información sobre una posible vía de gestión de reciclables en tu zona. Si encuentras una disponible, consulta sus condiciones de recepción.",
                "SEPARACION","Si encuentras una vía de gestión disponible, consulta qué materiales admite e identifica alguno que puedas separar siguiendo sus indicaciones.");
        agregar(m,"RE_REVISAR_ENTREGA","Consulta las indicaciones para tu entrega",
                "Registraste que reciclas y un uso de plásticos {/residuos/plasticos}. No conocemos cómo lo realizas ni las indicaciones de una posible vía de entrega.",r,
                "Comprueba si tu forma de reciclar incluye entregar materiales. Si es así, consulta qué materiales admite esa vía y cuáles son sus condiciones antes de preparar una entrega.",
                "MATERIALES_ADMITIDOS","Si utilizas una vía de entrega, consulta qué materiales admite antes de preparar la próxima entrega.",
                "CONDICIONES_ENTREGA","Si entregas materiales a una persona, organización o servicio, consulta sus indicaciones de preparación y entrega.");
        agregar(m,"TR_EXPLORAR_ALTERNATIVAS_AUTO","Comprueba alternativas para una gestión",
                "Registraste {/transporte/medio}, {/transporte/kmSemana} km/semana y {/transporte/diasSemana} días/semana. No conocemos las alternativas disponibles para tus desplazamientos.",t,
                "Elige una próxima gestión y averigua si existe otra forma de realizar ese desplazamiento que sea adecuada para tus necesidades.",
                "DISPONIBILIDAD","Elige una próxima gestión y busca información sobre qué alternativas de desplazamiento existen para ella, si encuentras alguna.",
                "VIABILIDAD","Si identificas una alternativa para una próxima gestión, comprueba si su seguridad, accesibilidad y condiciones permiten utilizarla sin comprometer tus necesidades.");
        agregar(m,"EN_REVISAR_USOS","Identifica usos de tu fuente energética",
                "Registraste vivienda {/energia/vivienda}, {/energia/personas} personas y fuente {/energia/fuente}. No conocemos los usos concretos de esa fuente en tu hogar.",
                Set.of("/energia/vivienda","/energia/personas","/energia/fuente"),
                "Anota un uso real de la fuente registrada ({/energia/fuente}) en tu hogar y comprueba qué necesidad satisface antes de considerar algún cambio.",
                "IDENTIFICAR_USOS","Anota las actividades de tu hogar que usan la fuente registrada ({/energia/fuente}) y elige una para describir cómo la utilizas.",
                "COMPROBAR_NECESIDAD","Elige un uso real de la fuente registrada ({/energia/fuente}) y comprueba qué necesidad satisface. Considera un ajuste solo si mantiene la seguridad, el bienestar y las necesidades esenciales.");
        agregar(m,"AL_REVISAR_PRESENTACION","Consulta las presentaciones de un alimento",
                "Tu alimentación registrada es {/alimentacion/tipo}. No conocemos los alimentos que estás considerando ni sus presentaciones disponibles.",Set.of("/alimentacion/tipo"),
                "Para un alimento que estés considerando y sea compatible con tu alimentación, consulta qué presentaciones hay y cómo es su envase, si encuentras opciones disponibles.",
                "PRESENTACION","Si encuentras distintas presentaciones de un alimento que estés considerando, compara su envase y la cantidad que necesitas, respetando tu tipo de alimentación.",
                "DISPONIBILIDAD","Para un alimento que estés considerando, averigua si hay otras presentaciones disponibles que sean adecuadas para tus necesidades y tu tipo de alimentación.");
        agregar(m,"RE_REVISAR_REUTILIZACION","Comprueba un uso de plástico desechable",
                "Registro de reciclaje: {/residuos/reciclas}; uso de plásticos: {/residuos/plasticos}. No conocemos los objetos que utilizas ni las alternativas disponibles.",r,
                "Comprueba si entre tus usos de plástico hay alguno desechable. Si identificas uno, comprueba si puede evitarse o si encuentras una alternativa reutilizable destinada a ese uso y que pueda utilizarse e higienizarse con seguridad, sin que necesites comprarla. Si no puedes comprobar su adecuación, higiene o seguridad, no la reutilices para ese fin.",
                "NECESIDAD","Comprueba si entre tus usos de plástico hay alguno desechable. Si identificas uno, comprueba qué necesidad satisface y considera evitarlo solo si esa necesidad queda cubierta sin comprometer la seguridad ni las necesidades esenciales.",
                "REUTILIZACION_VIABLE","Si encuentras una alternativa reutilizable para un uso real de plástico desechable, comprueba que esté destinada a ese uso y que pueda utilizarse e higienizarse con seguridad. Si no puedes comprobarlo, no la reutilices para ese fin.");
        return Map.copyOf(m);
    }
    private static void agregar(Map<Clave,Definicion> mapa, String accion, String titulo, String motivo,
            Set<String> campos, String sinEnfoque, String enfoque1, String paso1, String enfoque2, String paso2) {
        mapa.put(new Clave(accion,"1",SIN_ENFOQUE),new Definicion(titulo,sinEnfoque,motivo,campos));
        mapa.put(new Clave(accion,"1",enfoque1),new Definicion(titulo,paso1,motivo,campos));
        mapa.put(new Clave(accion,"1",enfoque2),new Definicion(titulo,paso2,motivo,campos));
    }
}
