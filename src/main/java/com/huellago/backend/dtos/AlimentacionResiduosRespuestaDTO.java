package com.huellago.backend.dtos;


import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AlimentacionResiduosRespuestaDTO {
    private Long usuarioId;
    private Long categoriaAlimentacionId;
    private Long categoriaResiduosId;
    private String tipo;
    private String plasticos;
    private Boolean reciclas;
    private boolean configuracionInicialCompleta;
    private java.util.List<String> categoriasPendientes = java.util.List.of();

    public AlimentacionResiduosRespuestaDTO(Long usuarioId, Long categoriaAlimentacionId, Long categoriaResiduosId, String tipo, String plasticos, Boolean reciclas) {
        this.usuarioId = usuarioId;
        this.categoriaAlimentacionId = categoriaAlimentacionId;
        this.categoriaResiduosId = categoriaResiduosId;
        this.tipo = tipo;
        this.plasticos = plasticos;
        this.reciclas = reciclas;
    }
}
