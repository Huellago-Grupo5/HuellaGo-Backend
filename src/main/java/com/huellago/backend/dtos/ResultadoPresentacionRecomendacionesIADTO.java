package com.huellago.backend.dtos;

import java.util.List;
import java.util.Set;

/** Vista previa local candidata, nunca una inferencia ni una aprobación ambiental.
 * La selección recibida no acredita por sí sola una llamada real a Qwen3. */
public record ResultadoPresentacionRecomendacionesIADTO(Estado estado,
        ResultadoEvaluacionRecomendacionIADTO evaluacionVerificada, List<Detalle> resultados) {
    public ResultadoPresentacionRecomendacionesIADTO { resultados = List.copyOf(resultados); }
    public boolean publicable() { return false; }
    public enum Estado { SELECCION_ACEPTADA, SELECCION_RECHAZADA, SIN_PROPUESTA }
    public enum EstadoDetalle { VISTA_PREVIA_BORRADOR, ABSTENCION_V2, SIN_PRESENTACION }
    public enum Motivo { REVISION_HUMANA_PENDIENTE, ABSTENCION_ORIGINAL, COMBINACION_NO_DEFINIDA, DATOS_NO_REPRESENTABLES }
    public enum Revision { BORRADOR }
    public record Detalle(RecomendacionFundamentadaIADTO.Propuesta seleccionOriginal,
                          EstadoDetalle estado, Motivo motivo, Presentacion presentacion) { }
    public record Presentacion(String accionId, String versionAccion, String enfoque,
                               boolean enfoqueSeleccionado, String versionPresentacion,
                               String titulo, String descripcion, String motivo,
                               Set<String> camposContextoPermitidos, Revision estado,
                               ResultadoEvaluacionRecomendacionIADTO.Estado revisionHumana) {
        public Presentacion { camposContextoPermitidos = Set.copyOf(camposContextoPermitidos); }
        public boolean publicable() { return false; }
    }
}
