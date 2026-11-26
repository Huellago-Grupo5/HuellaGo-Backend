package com.huellago.backend.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PreferenciaNotificacionActualizarDTO {

    @NotNull(message = "notificacionesActivas es obligatorio")
    private Boolean notificacionesActivas;
}
