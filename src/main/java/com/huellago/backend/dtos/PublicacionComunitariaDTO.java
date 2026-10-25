package com.huellago.backend.dtos;

import java.time.LocalDateTime;

public record PublicacionComunitariaDTO(Long id, Long autorId, String autor,
        String tipo, Integer origenId, String titulo, LocalDateTime fechaPublicacion) { }
