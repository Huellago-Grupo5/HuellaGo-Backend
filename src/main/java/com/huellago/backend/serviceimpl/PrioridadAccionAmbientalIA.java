package com.huellago.backend.serviceimpl;

/** Rúbrica interna de actuación, no estimación de CO2e evitado. Solo acciones canónicas v1. */
final class PrioridadAccionAmbientalIA {
    static final String BASE = "RUBRICA_CUALITATIVA_UNIVERSITARIA_V1";
    private PrioridadAccionAmbientalIA() { }

    record Evaluacion(String prioridad, String justificacion) { }

    static Evaluacion evaluar(String id, String version) {
        if (!"1".equals(version)) throw new IllegalArgumentException("Versión de acción sin rúbrica");
        return switch (id) {
            case "TR_PLANIFICAR_DESPLAZAMIENTOS" -> media("Agrupar o evitar una salida depende de necesidades y recorridos reales aún desconocidos.");
            case "TR_EXPLORAR_ALTERNATIVAS_AUTO" -> media("Sustituir un trayecto en auto depende de alternativas disponibles y viables no registradas.");
            case "TR_REVISAR_MOVILIDAD_ACTIVA" -> baja("Revisar la organización de la movilidad activa registrada es una acción preparatoria; no acredita un desplazamiento evitado.");
            case "EN_REVISAR_USOS" -> media("Evitar un uso innecesario del hogar depende de consumos y necesidades reales no registrados.");
            case "EN_REVISAR_REGISTRO_HOGAR" -> baja("Comprobar procedencia y alcance del registro eléctrico del hogar es una acción informativa.");
            case "AL_PLANIFICAR_APROVECHAMIENTO" -> media("Evitar desperdicio depende de alimentos disponibles y desperdicio real no registrados.");
            case "AL_REVISAR_PRESENTACION" -> baja("Comparar presentaciones y envases es preparatorio; no acredita una opción de menor huella.");
            case "RE_INICIAR_SEPARACION" -> media("La posible recuperación de materiales depende de la gestión local y de los materiales efectivamente admitidos.");
            case "RE_REVISAR_ENTREGA" -> baja("Consultar condiciones de entrega complementa el reciclaje registrado, sin acreditar recuperación adicional.");
            case "RE_REVISAR_REUTILIZACION" -> media("Evitar un uso desechable depende de usos reales y alternativas reutilizables viables no registrados.");
            default -> throw new IllegalArgumentException("Acción sin rúbrica");
        };
    }

    private static Evaluacion media(String motivo) { return new Evaluacion("MEDIA", motivo); }
    private static Evaluacion baja(String motivo) { return new Evaluacion("BAJA", motivo); }
}
