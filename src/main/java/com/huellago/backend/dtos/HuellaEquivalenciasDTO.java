package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HuellaEquivalenciasDTO {
    private Long usuarioId;
    private BigDecimal co2Total;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Equivalencia hipotética en árboles urbanos, no árboles plantados")
    private Long arboles;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Kilómetros hipotéticos de auto reemplazables por bicicleta, no trayectos realizados")
    private Long bicicletaKm;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Masa hipotética reciclada según factores EPA, no reciclaje realizado")
    private Long reciclajeKg;
    private String unidadHuella = "kg CO2e";
    private String periodoHuella = "semanal";
    private String fuente = "https://www.epa.gov/energy/greenhouse-gas-equivalencies-calculator-calculations-and-references";
    private String advertencia = "Equivalencias orientativas basadas en promedios de EE. UU., redondeadas al entero más cercano. No son reducciones reales, compensaciones realizadas ni mediciones de Perú.";
    private String explicacionArboles = "Árboles urbanos hipotéticos: 60 kg CO2 por árbol al año, promedio de los primeros diez años; tasa anual prorrateada a una semana. No implica árboles plantados.";
    private String explicacionBicicletaKm = "Kilómetros de auto de gasolina estadounidense que hipotéticamente podrían sustituirse por bicicleta; 0.393 kg CO2e por milla de auto y 1.609344 km por milla. El pedaleo no tiene emisiones directas; no acredita ahorro realizado.";
    private String explicacionReciclajeKg = "Kilogramos de residuos mixtos hipotéticamente reciclados en vez de enviados a vertedero; EPA WARM v16: 2830 kg CO2e por 907.18474 kg. No acredita reciclaje realizado.";

    public HuellaEquivalenciasDTO(Long usuarioId, BigDecimal co2Total, Long arboles,
                                  Long bicicletaKm, Long reciclajeKg) {
        this.usuarioId = usuarioId;
        this.co2Total = co2Total;
        this.arboles = arboles;
        this.bicicletaKm = bicicletaKm;
        this.reciclajeKg = reciclajeKg;
    }
}
