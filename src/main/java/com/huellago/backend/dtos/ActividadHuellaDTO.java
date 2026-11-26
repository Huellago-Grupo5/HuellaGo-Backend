package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActividadHuellaDTO {
    private String categoria;
    private BigDecimal co2;

    public boolean isCompleta() {
        return co2 != null;
    }
}
