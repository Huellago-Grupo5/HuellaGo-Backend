package com.huellago.backend.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsuarioPerfilDTO {
    @NotBlank(message = "Los nombres son obligatorios")
    @Size(min = 2, max = 80, message = "Los nombres deben tener entre 2 y 80 caracteres")
    @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$",
            message = "Los nombres contienen caracteres inválidos")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(min = 2, max = 80, message = "Los apellidos deben tener entre 2 y 80 caracteres")
    @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$",
            message = "Los apellidos contienen caracteres inválidos")
    private String apellidos;

    @NotBlank(message = "El correo es obligatorio")
    @Size(max = 120, message = "El correo no debe superar los 120 caracteres")
    @Email(message = "El formato del correo no es válido")
    private String correo;
}
