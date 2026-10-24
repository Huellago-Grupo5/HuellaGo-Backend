package com.huellago.backend.dtos;

import java.time.LocalDateTime;

public record RecomendacionCompletadaDTO(
        Integer usuarioRecomendacionId,
        Integer recomendacionId,
        String titulo,
        String descripcion,
        String estado,
        LocalDateTime fechaGeneracion,
        LocalDateTime fechaCompletado) { }
