package com.huellago.backend.dtos;


import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EnergiaDTO {
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
    private Long usuarioId;
    @jakarta.validation.constraints.NotBlank
    private String vivienda;
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
    private Integer personas;
    @jakarta.validation.constraints.NotBlank
    private String fuente;
    @jakarta.validation.constraints.DecimalMin("0")
    private java.math.BigDecimal consumoKwhDiario;

    public EnergiaDTO(Long usuarioId, String vivienda, Integer personas, String fuente) {
        this.usuarioId = usuarioId;
        this.vivienda = vivienda;
        this.personas = personas;
        this.fuente = fuente;
    }
}
