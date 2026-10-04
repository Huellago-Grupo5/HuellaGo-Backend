package com.huellago.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsuarioPerfilDTO {
    private Long id;
    private String nombres;
    private String apellidos;
}
