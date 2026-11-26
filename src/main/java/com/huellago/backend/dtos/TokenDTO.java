package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenDTO {
    @lombok.ToString.Exclude
    @io.swagger.v3.oas.annotations.media.Schema(description = "JWT firmado para usar en Authorize; no se incluye ningún token real en esta documentación")
    private String token;
    private Long id;
    private String correo;
}
