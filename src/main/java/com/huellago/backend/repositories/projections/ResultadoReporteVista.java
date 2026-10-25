package com.huellago.backend.repositories.projections;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface ResultadoReporteVista {
    Integer getRetoId();
    Integer getParticipantes();
    BigDecimal getProgresoFinal();
    String getEstado();
    LocalDateTime getFechaCierre();
}
