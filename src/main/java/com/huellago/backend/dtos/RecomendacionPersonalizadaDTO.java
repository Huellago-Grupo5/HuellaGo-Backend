package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecomendacionPersonalizadaDTO {
    private Integer usuarioRecomendacionId;
    private Integer recomendacionId;
    private String categoria;
    private String titulo;
    private String descripcion;
    private String motivo;
    private String prioridad;
    private BigDecimal co2Categoria;
    private String estado;
    private LocalDateTime fechaGeneracion;
}
