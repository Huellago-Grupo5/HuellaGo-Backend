package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InsigniaRespuestaDTO {
    private Integer id;
    private String nombre;
    private String descripcion;
    private String imagenUrl;
    private Integer puntosRequeridos;
    private LocalDateTime fechaObtencion;
}
