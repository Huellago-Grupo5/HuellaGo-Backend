package com.huellago.backend.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PrivacidadActualizarDTO {

    @NotNull(message = "visibilidadComunidad es obligatorio")
    private Boolean visibilidadComunidad;
}
