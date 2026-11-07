package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransporteDTO {
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
    private Long usuarioId;
    @jakarta.validation.constraints.NotBlank
    private String medio;
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin(value="0", inclusive=false)
    private BigDecimal kmSemana;
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(7)
    private Integer diasSemana;
}
