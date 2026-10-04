package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
