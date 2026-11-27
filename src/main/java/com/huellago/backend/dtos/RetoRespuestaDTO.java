package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.math.BigDecimal;

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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String clasificacion;
    private BigDecimal metaCantidad;
    private String unidadMedida;

    public RetoRespuestaDTO(Integer id, String titulo, String descripcion, Integer puntosRecompensa,
            String dificultad, LocalDate fechaInicio, LocalDate fechaFin, Boolean activo,
            Long categoriaId, String categoriaNombre) {
        this.id=id; this.titulo=titulo; this.descripcion=descripcion;
        this.puntosRecompensa=puntosRecompensa; this.dificultad=dificultad;
        this.fechaInicio=fechaInicio; this.fechaFin=fechaFin; this.activo=activo;
        this.categoriaId=categoriaId; this.categoriaNombre=categoriaNombre;
    }
}
