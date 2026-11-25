package com.huellago.backend.dtos;

import java.util.List;

/** Resultado interno no publicable. La procedencia HTTP no acredita por sí sola una inferencia real:
 * un transporte de pruebas puede estar simulado. No certifica la semántica del texto. */
public record ResultadoContenidoRecomendacionesIADTO(
        RecomendacionFundamentadaIADTO seleccionOriginal, List<Contenido> contenidos,
        ResultadoEvaluacionRecomendacionIADTO.Estado estado, Traza traza) {
    public ResultadoContenidoRecomendacionesIADTO { contenidos = List.copyOf(contenidos); }
    public boolean publicable() { return false; }
    public record Contenido(String categoria, RecomendacionFundamentadaIADTO.Accion accion,
                            String titulo, String descripcion, String motivo) { }
    public record Traza(String origen, String modelo, String sha256Solicitud, long duracionMs) { }
}
