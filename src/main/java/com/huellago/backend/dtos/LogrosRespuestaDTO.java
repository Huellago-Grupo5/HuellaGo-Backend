package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogrosRespuestaDTO {
    private Long usuarioId;
    private Integer ecoPuntos;
    private Integer nivel;
    private Integer puntosEnNivel;
    private Integer puntosParaSiguienteNivel;
    private Integer progresoPorcentaje;
    private List<InsigniaRespuestaDTO> insignias;
}
