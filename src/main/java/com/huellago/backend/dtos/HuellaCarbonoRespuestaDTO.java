package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HuellaCarbonoRespuestaDTO {
    private Long usuarioId;
    private BigDecimal co2Total;
    private BigDecimal co2Transporte;
    private BigDecimal co2Energia;
    private BigDecimal co2Alimentacion;
    private BigDecimal co2Residuos;
    private LocalDateTime fechaCalculo;
}
