package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoginDTO {
    @jakarta.validation.constraints.NotBlank(message = "El correo es obligatorio")
    @jakarta.validation.constraints.Email(message = "El correo no es válido")
    @jakarta.validation.constraints.Size(max = 120, message = "El correo admite máximo 120 caracteres")
    private String correo;
    @jakarta.validation.constraints.NotBlank(message = "La contraseña es obligatoria")
    @jakarta.validation.constraints.Size(max = 72, message = "La contraseña es demasiado larga")
    @lombok.ToString.Exclude
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String contrasena;
    public LoginDTO(String correo, String contrasena) { setCorreo(correo); this.contrasena = contrasena; }
    public void setCorreo(String correo) { this.correo = com.huellago.backend.security.ValidacionCuenta.correo(correo); }
    @jakarta.validation.constraints.AssertTrue(message = "La contraseña admite máximo 72 bytes UTF-8")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isLongitudContrasenaValida() {
        return contrasena == null || contrasena.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
