package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EnergiaDTO {
    private Long usuarioId;
    private String vivienda;
    private Integer personas;
    private String fuente;
}
