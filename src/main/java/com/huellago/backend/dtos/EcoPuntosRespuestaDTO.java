package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EcoPuntosRespuestaDTO {
    private Long usuarioId;
    private Integer ecoPuntos;
    private Integer puntosOtorgados;
    private String accion;
}
