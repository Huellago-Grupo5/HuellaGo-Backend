package com.huellago.backend.dtos;

import java.time.LocalDateTime;

public record NotificacionRespuestaDTO(Integer id, String tipo, String titulo, String mensaje,
        boolean leida, LocalDateTime fechaCreacion) { }
