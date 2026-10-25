package com.huellago.backend.dtos;

import java.time.LocalDate;

public record CampanaRespuestaDTO(Integer id, Long institucionId, String nombre, String objetivo,
        LocalDate fechaInicio, LocalDate fechaFin, long duracionDias, boolean publicada,
        String estado, RetoColectivoDTO seguimiento) { }
