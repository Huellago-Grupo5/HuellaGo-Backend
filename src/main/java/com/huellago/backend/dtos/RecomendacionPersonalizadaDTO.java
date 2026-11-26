package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecomendacionPersonalizadaDTO {
    @io.swagger.v3.oas.annotations.media.Schema(description = "Identificador de la asociación propia, distinto del identificador canónico")
    private Integer usuarioRecomendacionId;
    private Integer recomendacionId;
    private String categoria;
    private String titulo;
    private String descripcion;
    private String motivo;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Prioridad cualitativa de actuación; no mide kg CO2e evitados", allowableValues = {"ALTA", "MEDIA", "BAJA"})
    private String prioridad;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Huella actual orientativa de la categoría en kg CO2e semanales; no es ahorro potencial")
    private BigDecimal co2Categoria;
    private String estado;
    private LocalDateTime fechaGeneracion;
    /** Estado de la estimación del beneficio de esta acción; nunca representa la huella actual. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @io.swagger.v3.oas.annotations.media.Schema(description = "NO_ESTIMABLE cuando faltan datos o supuestos para calcular una reducción hipotética")
    private String impactoEsperado;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String explicacionImpacto;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String justificacionPrioridad;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @io.swagger.v3.oas.annotations.media.Schema(description = "Reducción hipotética en kg CO2e semanales, nunca reducción observada")
    private BigDecimal impactoPotencialKgCo2eSemanal;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String supuestosImpacto;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String metodologiaImpacto;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String limitacionesImpacto;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String accionId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String versionAccion;

    /** Conserva el contrato utilizado por LEGADO y los consumidores existentes. */
    public RecomendacionPersonalizadaDTO(Integer usuarioRecomendacionId, Integer recomendacionId,
            String categoria, String titulo, String descripcion, String motivo, String prioridad,
            BigDecimal co2Categoria, String estado, LocalDateTime fechaGeneracion) {
        this.usuarioRecomendacionId = usuarioRecomendacionId;
        this.recomendacionId = recomendacionId;
        this.categoria = categoria;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.motivo = motivo;
        this.prioridad = prioridad;
        this.co2Categoria = co2Categoria;
        this.estado = estado;
        this.fechaGeneracion = fechaGeneracion;
    }

    public void indicarImpactoNoEstimable() {
        this.impactoEsperado = "NO_ESTIMABLE";
        this.explicacionImpacto = "No hay datos suficientes sobre el cambio de comportamiento atribuible a esta acción para estimar CO2e evitado. La prioridad mostrada no mide ese ahorro.";
    }
}
