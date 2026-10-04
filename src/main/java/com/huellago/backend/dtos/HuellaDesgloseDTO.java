package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HuellaDesgloseDTO {
    private Long usuarioId;
    private BigDecimal co2Total;
    private List<ActividadHuellaDTO> actividades;
}
