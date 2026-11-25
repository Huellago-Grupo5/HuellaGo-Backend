package com.huellago.backend.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AlimentacionResiduosDTO {
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
    @Schema(description = "ID propio autenticado; debe coincidir con el JWT", example = "70")
    private Long usuarioId;
    @jakarta.validation.constraints.NotBlank
    @Schema(description = "Tipo de alimentaci?n declarado", example = "Mixta")
    private String tipo;
    @jakarta.validation.constraints.NotBlank
    @Schema(description = "Uso de pl?sticos declarado", example = "Medio")
    private String plasticos;
    @jakarta.validation.constraints.NotNull
    @Schema(description = "Indica si separa residuos para reciclaje", example = "true")
    private Boolean reciclas;
}
