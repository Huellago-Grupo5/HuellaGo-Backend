package com.huellago.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.math.BigDecimal;
import jakarta.validation.constraints.Positive;

public record CampanaCrearDTO(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 255, message = "El nombre admite máximo 255 caracteres") String nombre,
        @NotBlank(message = "El objetivo es obligatorio")
        @Size(max = 255, message = "El objetivo admite máximo 255 caracteres") String objetivo,
        @NotNull(message = "La fecha de inicio es obligatoria") LocalDate fechaInicio,
        @NotNull(message = "La fecha de finalización es obligatoria") LocalDate fechaFin,
        @Positive(message = "La meta debe ser positiva") BigDecimal metaCantidad,
        @Size(max = 60, message = "La unidad admite máximo 60 caracteres") String unidadMedida) {
    public CampanaCrearDTO(String nombre, String objetivo, LocalDate fechaInicio, LocalDate fechaFin) {
        this(nombre, objetivo, fechaInicio, fechaFin, BigDecimal.ONE, "acciones");
    }
}
