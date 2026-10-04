package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsuarioRetoRespuestaDTO {
    private Integer id;
    private Long usuarioId;
    private Integer retoId;
    private String estado;
    private BigDecimal progreso;
    private LocalDateTime fechaAceptacion;
    private LocalDateTime fechaCompletado;
}
