package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RetoRespuestaDTO {
    private Integer id;
    private String titulo;
    private String descripcion;
    private Integer puntosRecompensa;
    private String dificultad;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Boolean activo;
    private Long categoriaId;
    private String categoriaNombre;
}
