package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RestablecerContrasenaDTO {
    @jakarta.validation.constraints.NotBlank(message = "El token es obligatorio")
    @jakarta.validation.constraints.Size(max = 512, message = "Token inválido")
    @lombok.ToString.Exclude
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String token;
    @com.huellago.backend.security.ContrasenaSegura
    @lombok.ToString.Exclude
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String nuevaContrasena;
}
