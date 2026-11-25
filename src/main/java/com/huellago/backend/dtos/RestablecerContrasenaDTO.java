package com.huellago.backend.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

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
    @Schema(description = "Token de recuperaci?n recibido por correo; ejemplo ficticio", example = "token-de-ejemplo")
    private String token;
    @com.huellago.backend.security.ContrasenaSegura
    @lombok.ToString.Exclude
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "Nueva contrase?a; nunca se muestra en respuestas", example = "NuevaClave123!")
    private String nuevaContrasena;
}
