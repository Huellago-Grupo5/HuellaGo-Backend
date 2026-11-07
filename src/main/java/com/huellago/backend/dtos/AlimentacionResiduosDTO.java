package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AlimentacionResiduosDTO {
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
    private Long usuarioId;
    @jakarta.validation.constraints.NotBlank
    private String tipo;
    @jakarta.validation.constraints.NotBlank
    private String plasticos;
    @jakarta.validation.constraints.NotNull
    private Boolean reciclas;
}
