package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransporteRespuestaDTO {
    private Long usuarioId;
    private Long categoriaId;
    private String medio;
    private BigDecimal kmSemana;
    private Integer diasSemana;
}
