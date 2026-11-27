package com.huellago.backend.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.huellago.backend.security.ContrasenaSegura;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CambiarContrasenaDTO {
    @NotBlank(message = "La contraseña actual es obligatoria")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String contrasenaActual;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @ContrasenaSegura
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String contrasenaNueva;
}
