package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PrivacidadDTO {
    private Long usuarioId;
    private Boolean visibilidadComunidad;
    private LocalDateTime fechaActualizacion;
}
