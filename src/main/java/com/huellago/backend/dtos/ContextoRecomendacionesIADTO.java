package com.huellago.backend.dtos;

import java.math.BigDecimal;

/** Contexto interno sin identidad, credenciales ni entidades JPA. Categorías ausentes: null. */
public record ContextoRecomendacionesIADTO(
        Transporte transporte, Energia energia, Alimentacion alimentacion, Residuos residuos) {
    public record Transporte(String medio, BigDecimal kmSemana, Integer diasSemana) { }
    public record Energia(String vivienda, Integer personas, String fuente, BigDecimal consumoKwhDiario) { }
    public record Alimentacion(String tipo) { }
    public record Residuos(String plasticos, Boolean reciclas) { }
}
