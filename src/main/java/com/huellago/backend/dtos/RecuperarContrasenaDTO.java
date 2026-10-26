package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RecuperarContrasenaDTO {
    @jakarta.validation.constraints.NotBlank(message = "El correo es obligatorio")
    @jakarta.validation.constraints.Email(message = "El correo no es válido")
    @jakarta.validation.constraints.Size(max = 120, message = "El correo admite máximo 120 caracteres")
    private String correo;
    public RecuperarContrasenaDTO(String correo) { setCorreo(correo); }
    public void setCorreo(String correo) { this.correo = com.huellago.backend.security.ValidacionCuenta.correo(correo); }
}
