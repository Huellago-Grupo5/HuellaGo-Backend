package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EnergiaRespuestaDTO {
    private Long usuarioId;
    private Long categoriaId;
    private String vivienda;
    private Integer personas;
    private String fuente;
}
