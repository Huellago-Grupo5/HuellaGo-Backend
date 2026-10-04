package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AlimentacionResiduosDTO {
    private Long usuarioId;
    private String tipo;
    private String plasticos;
    private Boolean reciclas;
}
