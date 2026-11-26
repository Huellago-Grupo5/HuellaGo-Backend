package com.huellago.backend.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransporteDTO {
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
    @Schema(description = "ID propio autenticado; debe coincidir con el JWT", example = "70")
    private Long usuarioId;
    @jakarta.validation.constraints.NotBlank
    @Schema(description = "Medio de transporte registrado", example = "Auto")
    private String medio;
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin(value="0", inclusive=false)
    @Schema(description = "Kil?metros recorridos por semana", example = "100")
    private BigDecimal kmSemana;
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(7)
    @Schema(description = "D?as por semana, entre 1 y 7", example = "5")
    private Integer diasSemana;
}
