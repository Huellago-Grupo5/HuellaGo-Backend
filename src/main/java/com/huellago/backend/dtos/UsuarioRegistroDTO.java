package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UsuarioRegistroDTO {
    @jakarta.validation.constraints.NotBlank(message = "Los nombres son obligatorios")
    @jakarta.validation.constraints.Size(min = 2, max = 80, message = "Los nombres deben tener entre 2 y 80 caracteres")
    @jakarta.validation.constraints.Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Los nombres contienen caracteres inválidos")
    private String nombres;
    @jakarta.validation.constraints.NotBlank(message = "Los apellidos son obligatorios")
    @jakarta.validation.constraints.Size(min = 2, max = 80, message = "Los apellidos deben tener entre 2 y 80 caracteres")
    @jakarta.validation.constraints.Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Los apellidos contienen caracteres inválidos")
    private String apellidos;
    @jakarta.validation.constraints.NotBlank(message = "El correo es obligatorio")
    @jakarta.validation.constraints.Email(message = "El correo no es válido")
    @jakarta.validation.constraints.Size(max = 120, message = "El correo admite máximo 120 caracteres")
    private String correo;
    @com.huellago.backend.security.ContrasenaSegura
    @lombok.ToString.Exclude
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String contrasena;
    public UsuarioRegistroDTO(String nombres, String apellidos, String correo, String contrasena) {
        setNombres(nombres); setApellidos(apellidos); setCorreo(correo); this.contrasena = contrasena;
    }
    public void setNombres(String valor) { nombres = com.huellago.backend.security.ValidacionCuenta.nombre(valor); }
    public void setApellidos(String valor) { apellidos = com.huellago.backend.security.ValidacionCuenta.nombre(valor); }
    public void setCorreo(String valor) { correo = com.huellago.backend.security.ValidacionCuenta.correo(valor); }
}
