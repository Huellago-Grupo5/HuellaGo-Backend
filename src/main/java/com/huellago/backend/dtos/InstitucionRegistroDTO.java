package com.huellago.backend.dtos;

import com.huellago.backend.entities.TipoInstitucion;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InstitucionRegistroDTO(
        @NotNull(message = "El tipo es obligatorio") TipoInstitucion tipo,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres") String nombre,
        @NotBlank(message = "El país es obligatorio")
        @Pattern(regexp = "[A-Za-z]{2}", message = "El país debe tener dos letras") String pais,
        @NotBlank(message = "El identificador fiscal es obligatorio")
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9.-]{4,29}",
                message = "El identificador fiscal debe tener entre 5 y 30 caracteres alfanuméricos")
        String identificadorFiscal,
        @NotBlank(message = "El correo de contacto es obligatorio")
        @Email(message = "El correo de contacto no es válido")
        @Size(max = 254, message = "El correo de contacto es demasiado largo") String correoContacto
) {}
