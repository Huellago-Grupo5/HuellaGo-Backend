package com.huellago.backend.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record CompartirLogroDTO(@NotNull Tipo tipo, @NotNull @Positive Integer origenId) {
    public enum Tipo { INSIGNIA, RETO }

    @JsonCreator
    public static CompartirLogroDTO desdeJson(@JsonProperty("tipo") String tipo,
            @JsonProperty("origenId") BigDecimal origenId) {
        return new CompartirLogroDTO(tipo == null ? null : Tipo.valueOf(tipo),
                origenId == null ? null : origenId.intValueExact());
    }
}
