package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HuellaDesgloseDTO {
    private Long usuarioId;
    private BigDecimal co2Total;
    private List<ActividadHuellaDTO> actividades;

    public List<String> getCategoriasIncompletas() {
        return actividades == null ? List.of("Transporte", "Energía", "Alimentación", "Residuos")
                : actividades.stream().filter(a -> !a.isCompleta())
                .map(ActividadHuellaDTO::getCategoria).toList();
    }

    public boolean isCompleto() {
        return co2Total != null && actividades != null && actividades.size() == 4
                && getCategoriasIncompletas().isEmpty();
    }
}
