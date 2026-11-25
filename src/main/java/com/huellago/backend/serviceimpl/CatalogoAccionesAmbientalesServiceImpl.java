package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ContextoRecomendacionesIADTO;
import com.huellago.backend.dtos.RecomendacionFundamentadaIADTO;
import com.huellago.backend.exceptions.OllamaClientException;
import com.huellago.backend.services.AccionAmbientalAdmisible;
import com.huellago.backend.services.CatalogoAccionesAmbientalesService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.*;

import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.ACEPTADA_ESTRUCTURALMENTE;

/**
 * Diez borradores en memoria, sin red, repositorios, persistencia ni fallback. El filtrado acota opciones;
 * no selecciona una recomendación final, no redacta contenido para un usuario ni concede aprobación científica.
 * La futura inferencia Qwen3 deberá seleccionar y personalizar, o abstenerse. Los metadatos no son controles
 * semánticos ejecutables: las restricciones de redacción seguirán necesitando evaluación del contenido.
 */
@Service
public class CatalogoAccionesAmbientalesServiceImpl implements CatalogoAccionesAmbientalesService {
    private static final List<Candidata> CATALOGO = crearCatalogo();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ValidadorRecomendacionFundamentadaIA validador = new ValidadorRecomendacionFundamentadaIA();

    @Override public List<Candidata> listarBorradores() { return CATALOGO; }

    @Override public List<Candidata> compatibles(ContextoRecomendacionesIADTO contexto,
            Set<AccionAmbientalAdmisible.Referencia> realizadas) {
        Objects.requireNonNull(realizadas, "Se requiere un conjunto explícito de acciones realizadas");
        Set<AccionAmbientalAdmisible.Referencia> historial = Set.copyOf(realizadas);
        if (contexto == null) return List.of();
        // Reutiliza V1 para cada categoría. No replica opciones, rangos ni reglas de unidades.
        var t = valido(new ContextoRecomendacionesIADTO(contexto.transporte(), null, null, null)) ? contexto.transporte() : null;
        var e = valido(new ContextoRecomendacionesIADTO(null, contexto.energia(), null, null)) ? contexto.energia() : null;
        var a = valido(new ContextoRecomendacionesIADTO(null, null, contexto.alimentacion(), null)) ? contexto.alimentacion() : null;
        var r = valido(new ContextoRecomendacionesIADTO(null, null, null, contexto.residuos())) ? contexto.residuos() : null;
        if (t == null && e == null && a == null && r == null) return List.of();
        var completo = new ContextoRecomendacionesIADTO(t, e, a, r);
        Map<String, Object> datos = ValidadorRecomendacionesIA.contexto(completo);
        return CATALOGO.stream().filter(c -> datos.containsKey(c.definicion().categoria()))
                .filter(c -> compatible(c, completo, datos, historial)).toList();
    }

    private boolean compatible(Candidata candidata, ContextoRecomendacionesIADTO contexto,
            Map<String, Object> datos, Set<AccionAmbientalAdmisible.Referencia> realizadas) {
        var definicion = candidata.definicion();
        List<Evidencia> evidencias = new ArrayList<>();
        var categoria = (Map<?, ?>) datos.get(definicion.categoria());
        for (String ruta : definicion.evidenciasRequeridas().stream().sorted().toList()) {
            Object dato = categoria.get(ruta.substring(ruta.lastIndexOf('/') + 1));
            if (dato == null) continue; // V2 rechaza la ausencia; no crea una evidencia ficticia.
            Valor valor = dato instanceof String s ? new Texto(s)
                    : dato instanceof Boolean b ? new Booleano(b) : new Numero(new BigDecimal(dato.toString()));
            evidencias.add(new Evidencia(ruta, valor,
                    ruta.equals("/energia/consumoKwhDiario") ? Ambito.HOGAR : Ambito.NO_APLICA));
        }
        // Sonda técnica, nunca recomendación publicada: sin título, descripción o motivo del usuario.
        var sonda = new Propuesta(definicion.categoria(), Decision.PROPONER,
                new Accion(definicion.id(), definicion.version(), Map.of()), evidencias, null, null, null, List.of(), null);
        List<Propuesta> propuestas = new ArrayList<>();
        for (String nombre : datos.keySet()) propuestas.add(nombre.equals(definicion.categoria()) ? sonda
                : new Propuesta(nombre, Decision.ABSTENERSE, null, List.of(), null, null, null, List.of(), MotivoAbstencion.NO_VERIFICABLE));
        var evaluacion = validador.validar(mapper.writeValueAsString(new RecomendacionFundamentadaIADTO("2", propuestas)),
                contexto, List.of(definicion), realizadas);
        return evaluacion.estado() == ACEPTADA_ESTRUCTURALMENTE;
    }

    private static boolean valido(ContextoRecomendacionesIADTO contexto) {
        try { ValidadorRecomendacionesIA.contexto(contexto); return true; }
        catch (OllamaClientException error) { return false; }
    }

    private static List<Candidata> crearCatalogo() {
        List<Candidata> c = new ArrayList<>();
        c.add(crear("TR_PLANIFICAR_DESPLAZAMIENTOS", "Transporte", "/transporte/medio", textos("Auto", "Moto", "Bus"),
                Set.of("/transporte/medio", "/transporte/kmSemana", "/transporte/diasSemana"),
                "Revisar oportunidades de planificación del transporte registrado.",
                "Examinar si alguna gestión puede agruparse o evitarse, únicamente si las necesidades y condiciones reales lo permiten.",
                Set.of("NECESIDAD", "AGRUPACION"), Set.of("NECESIDADES_DESPLAZAMIENTO"),
                List.of("No inferir recorridos, horarios o destinos a partir del total semanal.", "Bus no implica uso de automóvil; no atribuir combustible o ahorro.")));
        c.add(crear("TR_EXPLORAR_ALTERNATIVAS_AUTO", "Transporte", "/transporte/medio", textos("Auto"),
                Set.of("/transporte/medio", "/transporte/kmSemana", "/transporte/diasSemana"),
                "Comprobar si existen alternativas viables a algún desplazamiento en auto.",
                "Revisar disponibilidad y viabilidad de alternativas antes de considerar sustituir algún desplazamiento; no asumir que existen.",
                Set.of("DISPONIBILIDAD", "VIABILIDAD"), Set.of("ALTERNATIVAS_DISPONIBLES"),
                List.of("No afirmar que hay transporte público, bicicleta disponible o trayectos cortos.", "No atribuir emisiones ni obligar a cambiar de medio.")));
        c.add(crear("TR_REVISAR_MOVILIDAD_ACTIVA", "Transporte", "/transporte/medio", textos("Bicicleta", "Caminando"),
                Set.of("/transporte/medio", "/transporte/kmSemana", "/transporte/diasSemana"),
                "Revisar la viabilidad de organizar gestiones compatibles con la movilidad activa ya registrada.",
                "Comprobar si la organización de gestiones admite alguna mejora viable; no pedir comenzar ni simplemente continuar caminando o usando bicicleta.",
                Set.of("ORGANIZACION", "VIABILIDAD"), Set.of("NECESIDADES_DESPLAZAMIENTO"),
                List.of("No asumir automóvil en otros días ni inventar rutas o distancias por trayecto.", "No afirmar que recorrer menos o más distancia activa garantiza un beneficio ambiental.")));
        c.add(crear("EN_REVISAR_USOS", "Energía", "/energia/fuente", textos("Electricidad", "Gas", "Solar"),
                Set.of("/energia/fuente", "/energia/vivienda", "/energia/personas"),
                "Identificar usos del hogar que podrían revisarse sin diagnosticar derroche.",
                "Revisar los usos reales de la fuente registrada y comprobar si hay algún consumo innecesario que pueda evitarse con seguridad.",
                Set.of("IDENTIFICAR_USOS", "COMPROBAR_NECESIDAD"), Set.of("USOS_REALES_HOGAR"),
                List.of("No asumir equipos concretos, uso continuo o consumos altos.", "No exigir compras ni desconexiones que comprometan seguridad o necesidades esenciales.", "No atribuir origen renovable o fósil a Electricidad.")));
        c.add(crear("EN_REVISAR_REGISTRO_HOGAR", "Energía", "/energia/fuente", textos("Electricidad"),
                Set.of("/energia/fuente", "/energia/personas", "/energia/vivienda", "/energia/consumoKwhDiario"),
                "Revisar el alcance del consumo eléctrico diario registrado del hogar.",
                "Comprobar cómo se obtuvo el registro y qué periodo y alcance describe antes de formular conclusiones sobre cambios de consumo.",
                Set.of("ALCANCE", "PROCEDENCIA_REGISTRO"), Set.of("PROCEDENCIA_CONSUMO"),
                List.of("Requiere consumo registrado; cero es válido y ausencia no equivale a cero.", "Conservar kWh/día y ámbito HOGAR, sin dividir o multiplicar por habitantes.", "No inferir tendencias, dispositivos, intensidad ni ahorros.")));
        c.add(crear("AL_PLANIFICAR_APROVECHAMIENTO", "Alimentación", "/alimentacion/tipo", textos("Mixta", "Vegetariana", "Vegana"),
                Set.of("/alimentacion/tipo"), "Revisar oportunidades de aprovechamiento de alimentos respetando el tipo registrado.",
                "Revisar lo que realmente hay disponible antes de planificar compras o preparaciones, sin atribuir alimentos ni desperdicios existentes.",
                Set.of("PLANIFICACION", "DISPONIBILIDAD"), Set.of("ALIMENTOS_DISPONIBLES"),
                List.of("No prescribir carne ni productos animales como obligación.", "No formular diagnósticos, carencias o recomendaciones nutricionales o médicas.", "No inventar alimentos, cantidades, frecuencias o emisiones.")));
        c.add(crear("AL_REVISAR_PRESENTACION", "Alimentación", "/alimentacion/tipo", textos("Mixta", "Vegetariana", "Vegana"),
                Set.of("/alimentacion/tipo"), "Examinar opciones de presentación compatibles con la alimentación registrada.",
                "Si hay opciones disponibles y adecuadas, comparar su presentación y envases antes de elegir; no cambiar obligatoriamente el tipo de alimentación.",
                Set.of("PRESENTACION", "DISPONIBILIDAD"), Set.of("OPCIONES_ALIMENTARIAS_DISPONIBLES"),
                List.of("No asumir comercios, alimentos, presupuesto o alternativas disponibles.", "No equiparar automáticamente menor envase con menor huella ni inventar ahorros.", "No prescribir dietas ni consumo animal obligatorio.")));
        c.add(crear("RE_INICIAR_SEPARACION", "Residuos", "/residuos/reciclas", Set.of(new Booleano(false)),
                Set.of("/residuos/reciclas", "/residuos/plasticos"), "Explorar el inicio de separación de materiales para su posible recuperación.",
                "Comprobar las condiciones locales y considerar separar los materiales admitidos, si existe una vía de gestión disponible.",
                Set.of("CONDICIONES_LOCALES", "SEPARACION"), Set.of("GESTION_LOCAL_DISPONIBLE"),
                List.of("reciclas=false no demuestra ausencia de toda separación previa.", "No garantizar recogida ni reciclaje efectivo; no aplicable cuando reciclas=true.")));
        c.add(crear("RE_REVISAR_ENTREGA", "Residuos", "/residuos/reciclas", Set.of(new Booleano(true)),
                Set.of("/residuos/reciclas", "/residuos/plasticos"), "Revisar condiciones de entrega complementarias al reciclaje registrado.",
                "Consultar qué materiales acepta la vía de gestión utilizada y revisar si la separación es compatible con esas indicaciones.",
                Set.of("MATERIALES_ADMITIDOS", "CONDICIONES_ENTREGA"), Set.of("GESTION_LOCAL_DISPONIBLE"),
                List.of("Reconocer que ya recicla; no limitarse a mantenerlo ni proponer iniciarlo.", "No asumir tipo de plástico, eficiencia o recuperación efectiva.")));
        c.add(crear("RE_REVISAR_REUTILIZACION", "Residuos", "/residuos/plasticos", textos("Bajo", "Medio", "Alto"),
                Set.of("/residuos/plasticos", "/residuos/reciclas"), "Identificar oportunidades viables para evitar algún uso desechable.",
                "Revisar si algún uso real de plástico admite una alternativa reutilizable disponible y adecuada, sin imponer compras ni usos inseguros.",
                Set.of("NECESIDAD", "REUTILIZACION_VIABLE"), Set.of("USOS_PLASTICOS_REALES"),
                List.of("Bajo, Medio y Alto describen uso de plásticos, no nivel de reciclaje ni cantidades medidas.", "No asumir objetos, compras o alternativas disponibles; respetar reciclas y no prometer beneficios cuantificados.")));
        return List.copyOf(c);
    }

    private static Candidata crear(String id, String categoria, String ruta, Set<Valor> valores,
            Set<String> evidencias, String objetivo, String descripcion, Set<String> enfoques,
            Set<String> informacion, List<String> restricciones) {
        Set<Valor> permitidos = new HashSet<>(); enfoques.forEach(s -> permitidos.add(new Texto(s)));
        var definicion = new AccionAmbientalAdmisible(id, "1", categoria, Map.of(ruta, valores), evidencias,
                Map.of("enfoque", new AccionAmbientalAdmisible.ParametroPermitido(TipoValor.TEXTO, permitidos, false)), informacion);
        return new Candidata(definicion, objetivo, descripcion, restricciones, EstadoRevision.BORRADOR);
    }

    private static Set<Valor> textos(String... valores) {
        Set<Valor> textos = new HashSet<>(); for (String s : valores) textos.add(new Texto(s)); return Set.copyOf(textos);
    }
}
