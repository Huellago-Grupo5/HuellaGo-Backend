package com.huellago.backend.dtos;

import java.util.List;

/** No publica contenido ni incluye valores del contexto o del JSON rechazado. */
public record ResultadoEvaluacionRecomendacionIADTO(Estado estado, List<Detalle> evaluaciones, List<Motivo> motivos) {
    public ResultadoEvaluacionRecomendacionIADTO {
        evaluaciones = List.copyOf(evaluaciones); motivos = List.copyOf(motivos);
    }
    public enum Estado { ACEPTADA_ESTRUCTURALMENTE, RECHAZADA, PENDIENTE_REVISION }
    public enum Motivo {
        JSON_INVALIDO, CONTRATO_INVALIDO, CONTEXTO_INVALIDO, DEFINICIONES_INVALIDAS,
        CATEGORIA_AUSENTE, CATEGORIA_DUPLICADA, CATEGORIAS_INCOMPLETAS,
        RUTA_NO_AUTORIZADA, CAMPO_AUSENTE, EVIDENCIA_NO_COINCIDE, EVIDENCIA_DUPLICADA,
        EVIDENCIAS_INSUFICIENTES, AMBITO_INVALIDO, ACCION_NO_AUTORIZADA, VERSION_ACCION_INVALIDA,
        ACCION_INCOMPATIBLE, ACCION_REPETIDA, PARAMETRO_NO_AUTORIZADO, PARAMETRO_INVALIDO,
        INFORMACION_ADICIONAL_NO_AUTORIZADA, TEXTO_LIBRE_REQUIERE_REVISION
    }
    public record Detalle(String categoria, Estado estado, List<Motivo> motivos) {
        public Detalle { motivos = List.copyOf(motivos); }
    }
}
