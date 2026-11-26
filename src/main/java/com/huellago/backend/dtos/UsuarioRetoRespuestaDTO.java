package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    private List<InsigniaRespuestaDTO> insigniasDesbloqueadas = List.of();

    public UsuarioRetoRespuestaDTO(Integer id, Long usuarioId, Integer retoId,
            String estado, BigDecimal progreso, LocalDateTime fechaAceptacion,
            LocalDateTime fechaCompletado) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.retoId = retoId;
        this.estado = estado;
        this.progreso = progreso;
        this.fechaAceptacion = fechaAceptacion;
        this.fechaCompletado = fechaCompletado;
    }
}
