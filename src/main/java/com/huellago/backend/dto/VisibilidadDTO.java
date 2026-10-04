package com.huellago.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VisibilidadDTO {
    private Long id;
    private Boolean visibilidadComunidad;
}