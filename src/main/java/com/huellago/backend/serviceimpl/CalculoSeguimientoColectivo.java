package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.RetoColectivoDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Reglas existentes de HU29, compartidas por consultas individuales y reportes por lotes. */
public final class CalculoSeguimientoColectivo {
    private CalculoSeguimientoColectivo() { }
    public record Datos(Integer id, String titulo, LocalDate inicio, LocalDate fin, Boolean activo) { }
    public record Cierre(Integer participantes, BigDecimal progreso, String estado, LocalDateTime fecha) { }

    public static BigDecimal promedio(BigDecimal suma, long cantidad) {
        return cantidad == 0 ? BigDecimal.ZERO.setScale(2)
                : suma.divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);
    }

    public static boolean disponible(Datos datos, LocalDate hoy) {
        return Boolean.TRUE.equals(datos.activo()) && datos.fin() != null
                && (datos.inicio() == null || !hoy.isBefore(datos.inicio()))
                && !hoy.isAfter(datos.fin())
                && (datos.inicio() == null || !datos.inicio().isAfter(datos.fin()));
    }

    public static RetoColectivoDTO resumen(Datos datos, int cantidad, BigDecimal suma,
                                           Cierre cierre, LocalDate hoy) {
        if (cierre != null) {
            return new RetoColectivoDTO(datos.id(), datos.titulo(), datos.inicio(), datos.fin(),
                    cierre.participantes(), cierre.progreso(), cierre.estado(), cierre.fecha());
        }
        String estado = datos.fin() != null && hoy.isAfter(datos.fin()) ? "PENDIENTE_CIERRE"
                : disponible(datos, hoy) ? "ACTIVO" : "NO_DISPONIBLE";
        return new RetoColectivoDTO(datos.id(), datos.titulo(), datos.inicio(), datos.fin(),
                cantidad, promedio(suma, cantidad), estado, null);
    }

    public static String estadoCampana(Datos datos, LocalDate hoy) {
        if (datos.inicio() == null || datos.fin() == null || datos.fin().isBefore(datos.inicio())) {
            return "DATOS_INCOMPLETOS";
        }
        return !Boolean.TRUE.equals(datos.activo()) ? "NO_DISPONIBLE"
                : hoy.isBefore(datos.inicio()) ? "FUTURA"
                : hoy.isAfter(datos.fin()) ? "FINALIZADA" : "ACTIVA";
    }
}
