package com.huellago.backend.dtos;

import java.util.List;

/** Resultado interno. No concede publicación ni modifica el estado editorial BORRADOR. */
public record ResultadoPersistenciaRecomendacionesIADTO(List<Guardada> resultados) {
    public ResultadoPersistenciaRecomendacionesIADTO { resultados = List.copyOf(resultados); }
    public boolean publicable() { return false; }
    public record Guardada(Integer asociacionId, Integer recomendacionId, boolean reutilizada,
            String accionId, String versionAccion, String enfoque, String versionPresentacion,
            String titulo, String descripcion, String motivo, String estado,
            java.time.LocalDateTime fechaGeneracion) {
        public boolean publicable() { return false; }
        public String estadoEditorial() { return "BORRADOR"; }
        public String revisionHumana() { return "PENDIENTE_REVISION"; }
    }
}
