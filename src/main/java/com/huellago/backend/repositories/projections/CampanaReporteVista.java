package com.huellago.backend.repositories.projections;

import java.time.LocalDate;

public interface CampanaReporteVista {
    Integer getId();
    String getTitulo();
    String getDescripcion();
    LocalDate getFechaInicio();
    LocalDate getFechaFin();
    Boolean getActivo();
}
