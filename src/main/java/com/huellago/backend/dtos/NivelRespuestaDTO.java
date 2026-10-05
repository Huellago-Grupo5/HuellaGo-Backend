package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NivelRespuestaDTO {
    private Long usuarioId;
    private Integer ecoPuntos;
    private Integer nivel;
    private Integer puntosEnNivel;
    private Integer puntosParaSiguienteNivel;
    private Integer progresoPorcentaje;
}
