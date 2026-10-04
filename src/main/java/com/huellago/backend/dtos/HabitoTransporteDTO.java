package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HabitoTransporteDTO {
    private Long usuarioId;
    private String categoria;
    private String nombre;
    private BigDecimal valor;
    private String unidad;
}
