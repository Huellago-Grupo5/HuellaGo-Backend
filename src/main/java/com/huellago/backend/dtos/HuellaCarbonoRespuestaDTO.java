package com.huellago.backend.dtos;


import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class HuellaCarbonoRespuestaDTO {
    private Long usuarioId;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Huella semanal total estimada, en kg CO2e; alcance mixto personal y hogar")
    private BigDecimal co2Total;
    private BigDecimal co2Transporte;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Componente semanal de energía del hogar en kg CO2e; no se atribuye íntegramente a cada habitante")
    private BigDecimal co2Energia;
    private BigDecimal co2Alimentacion;
    private BigDecimal co2Residuos;
    private LocalDateTime fechaCalculo;
    private String unidad = "kg CO2e";
    private String periodo = "semanal";
    @io.swagger.v3.oas.annotations.media.Schema(description = "BAJO, MEDIO o ALTO según umbrales académicos internos; NO_DEFINIDO si faltan datos")
    private String nivelImpacto = "NO_DEFINIDO";
    private String referenciaImpacto = "Sin umbrales aprobados";
    private String metodologia = "Modelo orientativo HuellaGo de alcance mixto personal y del hogar; huella semanal estimada, coeficientes heredados sin validación científica";

    public HuellaCarbonoRespuestaDTO(Long usuarioId, BigDecimal co2Total, BigDecimal co2Transporte, BigDecimal co2Energia, BigDecimal co2Alimentacion, BigDecimal co2Residuos, LocalDateTime fechaCalculo) {
        this.usuarioId = usuarioId;
        this.co2Total = co2Total;
        this.co2Transporte = co2Transporte;
        this.co2Energia = co2Energia;
        this.co2Alimentacion = co2Alimentacion;
        this.co2Residuos = co2Residuos;
        this.fechaCalculo = fechaCalculo;
    }
}
