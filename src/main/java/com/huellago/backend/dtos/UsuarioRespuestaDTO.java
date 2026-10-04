package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsuarioRespuestaDTO {
    private Long id;
    private String nombres;
    private String apellidos;
    private String correo;
    private Integer ecoPuntos;
    private Integer nivel;
    private Boolean notificacionesActivas;
    private Boolean visibilidadComunidad;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
}
