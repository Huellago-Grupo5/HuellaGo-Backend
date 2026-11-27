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
    private java.math.BigDecimal metaCantidad;
    private String unidadMedida;

    public RetoColectivoDTO(Integer retoId, String titulo, LocalDate fechaInicio, LocalDate fechaFin,
            Integer participantes, BigDecimal progresoColectivo, String estado, LocalDateTime fechaCierre) {
        this.retoId = retoId;
        this.titulo = titulo;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.participantes = participantes;
        this.progresoColectivo = progresoColectivo;
        this.estado = estado;
        this.fechaCierre = fechaCierre;
    }
}
