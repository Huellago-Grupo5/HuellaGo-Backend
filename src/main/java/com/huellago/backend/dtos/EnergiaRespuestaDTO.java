package com.huellago.backend.dtos;


import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EnergiaRespuestaDTO {
    private Long usuarioId;
    private Long categoriaId;
    private String vivienda;
    private Integer personas;
    private String fuente;
    private java.math.BigDecimal consumoKwhDiario;

    public EnergiaRespuestaDTO(Long usuarioId, Long categoriaId, String vivienda, Integer personas, String fuente) {
        this.usuarioId = usuarioId;
        this.categoriaId = categoriaId;
        this.vivienda = vivienda;
        this.personas = personas;
        this.fuente = fuente;
    }
}
