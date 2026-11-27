package com.huellago.backend.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AvanceRetoRespuestaDTO(Long id, BigDecimal cantidad, LocalDateTime registradoEn) { }
