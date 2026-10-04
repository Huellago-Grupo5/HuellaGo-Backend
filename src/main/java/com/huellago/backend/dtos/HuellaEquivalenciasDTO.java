package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HuellaEquivalenciasDTO {
    private Long usuarioId;
    private BigDecimal co2Total;
    private Long arboles;
    private Long bicicletaKm;
    private Long reciclajeKg;
}
