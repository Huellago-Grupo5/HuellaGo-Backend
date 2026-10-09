package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardDTO {
    private Long usuarioId;
    private Integer ecoPuntos;
    private Integer nivel;
    private Long cantidadHabitos;
    private java.math.BigDecimal huellaCarbonoActual;
    private Long retosActivos;
    private Long recomendacionesPendientes;
    private Map<String, Long> habitosPorCategoria;
    private String categoriaConMasHabitos;
    private Long retosCompletados;
    private Long recomendacionesCompletadas;
}
