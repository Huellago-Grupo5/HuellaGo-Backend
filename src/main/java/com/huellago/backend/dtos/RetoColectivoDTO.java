package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RetoColectivoDTO {
    private Integer retoId;
    private String titulo;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Integer participantes;
    private BigDecimal progresoColectivo;
    private String estado;
    private LocalDateTime fechaCierre;
}
