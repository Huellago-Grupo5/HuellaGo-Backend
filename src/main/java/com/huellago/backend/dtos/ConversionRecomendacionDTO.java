package com.huellago.backend.dtos;

import java.time.LocalDateTime;

public record ConversionRecomendacionDTO(
        String mensaje,
        Integer usuarioRecomendacionId,
        Integer retoId,
        Integer participacionId,
        String estado,
        LocalDateTime fechaAceptacion) { }
