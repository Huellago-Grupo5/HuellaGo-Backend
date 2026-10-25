package com.huellago.backend.repositories.projections;

import java.math.BigDecimal;

public interface ParticipacionReporteVista {
    Integer getRetoId();
    Long getParticipantes();
    BigDecimal getSumaProgreso();
}
