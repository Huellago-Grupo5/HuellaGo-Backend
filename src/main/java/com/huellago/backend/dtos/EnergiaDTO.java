package com.huellago.backend.dtos;

import io.swagger.v3.oas.annotations.media.Schema;


import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EnergiaDTO {
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
    @Schema(description = "ID propio autenticado; debe coincidir con el JWT", example = "70")
    private Long usuarioId;
    @jakarta.validation.constraints.NotBlank
    @Schema(description = "Tipo de vivienda declarado", example = "Casa")
    private String vivienda;
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
    @Schema(description = "Habitantes del hogar", example = "3")
    private Integer personas;
    @jakarta.validation.constraints.NotBlank
    @Schema(description = "Fuente de energ?a declarada", example = "Electricidad")
    private String fuente;
    @jakarta.validation.constraints.DecimalMin("0")
    @Schema(description = "Consumo el?ctrico diario del hogar en kWh; opcional", example = "8")
    private java.math.BigDecimal consumoKwhDiario;

    public EnergiaDTO(Long usuarioId, String vivienda, Integer personas, String fuente) {
        this.usuarioId = usuarioId;
        this.vivienda = vivienda;
        this.personas = personas;
        this.fuente = fuente;
    }
}
