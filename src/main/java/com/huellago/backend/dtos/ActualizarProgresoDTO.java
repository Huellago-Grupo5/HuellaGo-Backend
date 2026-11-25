package com.huellago.backend.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActualizarProgresoDTO {
    @Schema(description = "Progreso porcentual de la participaci?n, de 0 a 100", example = "50")
    private BigDecimal progreso;
}
