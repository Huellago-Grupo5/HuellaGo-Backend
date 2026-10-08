package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AlimentacionResiduosRespuestaDTO {
    private Long usuarioId;
    private Long categoriaAlimentacionId;
    private Long categoriaResiduosId;
    private String tipo;
    private String plasticos;
    private Boolean reciclas;
}
