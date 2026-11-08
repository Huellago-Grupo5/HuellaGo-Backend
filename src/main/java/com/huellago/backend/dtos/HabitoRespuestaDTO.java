package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HabitoRespuestaDTO {
    private Long id;
    private Long usuarioId;
    private Long categoriaId;
    private String nombre;
    private BigDecimal valor;
    private String unidad;
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaActualizacion;
    private String categoriaNombre;

    public HabitoRespuestaDTO(Long id, Long usuarioId, Long categoriaId, String nombre,
                             BigDecimal valor, String unidad, LocalDateTime fechaRegistro,
                             LocalDateTime fechaActualizacion) {
        this(id, usuarioId, categoriaId, nombre, valor, unidad, fechaRegistro, fechaActualizacion, null);
    }
}
