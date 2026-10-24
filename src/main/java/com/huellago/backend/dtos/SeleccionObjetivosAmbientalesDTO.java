package com.huellago.backend.dtos;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
@Data
public class SeleccionObjetivosAmbientalesDTO {
    @NotEmpty(message = "Debe seleccionar al menos un objetivo ambiental")
    private List<@NotNull(message = "El identificador del objetivo es obligatorio") Long> objetivoIds;
}
