package com.huellago.backend.dtos;

import java.math.BigDecimal;

/** Cantidades hipotéticas declaradas por el usuario; nunca son una predicción del modelo. */
public record EscenarioImpactoIADTO(BigDecimal kmAutoEvitadosSemana,
        BigDecimal kmAutoSustituidosBicicletaSemana, BigDecimal kwhHogarEvitadosDia) {
    public boolean presente() {
        return kmAutoEvitadosSemana != null || kmAutoSustituidosBicicletaSemana != null
                || kwhHogarEvitadosDia != null;
    }
}
