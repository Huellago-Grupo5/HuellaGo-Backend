package com.huellago.backend.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record RegistrarAvanceDTO(
        @NotNull @Positive BigDecimal cantidad,
        @NotNull UUID claveSolicitud) { }
