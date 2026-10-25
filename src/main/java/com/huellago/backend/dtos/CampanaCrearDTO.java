package com.huellago.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CampanaCrearDTO(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 255, message = "El nombre admite máximo 255 caracteres") String nombre,
        @NotBlank(message = "El objetivo es obligatorio")
        @Size(max = 255, message = "El objetivo admite máximo 255 caracteres") String objetivo,
        @NotNull(message = "La fecha de inicio es obligatoria") LocalDate fechaInicio,
        @NotNull(message = "La fecha de finalización es obligatoria") LocalDate fechaFin) { }
