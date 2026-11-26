package com.huellago.backend.dtos;

/** Solo pruebas internas sintéticas. El JSON original es contenido no confiable, nunca una respuesta pública. */
public record ResultadoGeneracionFundamentadaIADTO(String propuestaJson,
        RecomendacionFundamentadaIADTO propuesta, ResultadoEvaluacionRecomendacionIADTO evaluacion) {
    /** Ni la aceptación estructural ni el estado BORRADOR habilitan publicación. */
    public boolean publicable() { return false; }
}
