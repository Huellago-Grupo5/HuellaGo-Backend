package com.huellago.backend.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ReporteInstitucionalDTO(InstitucionDatos institucion, LocalDateTime generadoEl,
        Filtros filtros, Resumen resumen, List<Campana> campanas, List<String> advertencias) {
    public ReporteInstitucionalDTO {
        campanas = List.copyOf(campanas);
        advertencias = List.copyOf(advertencias);
    }
    public record InstitucionDatos(Long id, String nombre) { }
    public record Filtros(LocalDate desde, LocalDate hasta, Integer campanaId) { }
    public record Resumen(int totalCampanas, int futuras, int activas, int finalizadas,
            int noDisponibles, int datosIncompletos, long totalParticipacionesRegistradas,
            long usuariosParticipantesUnicos, long participacionesConsideradasEnProgreso,
            BigDecimal progresoGeneralPonderado, int conResultado, int exitosas, int noAlcanzadas) { }
    public record Campana(Integer id, String nombre, String objetivo, LocalDate fechaInicio,
            LocalDate fechaFin, String estado, String estadoColectivo, long participacionesRegistradas,
            Integer participantes, BigDecimal progresoColectivo, String resultadoFinal,
            LocalDateTime fechaCierre) { }
}
