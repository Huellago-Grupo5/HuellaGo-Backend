package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ReporteInstitucionalDTO;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class ReporteInstitucionalCsvTest {
    final ReporteInstitucionalCsv csv = new ReporteInstitucionalCsv();
    ReporteInstitucionalDTO reporte(String nombre, String objetivo, int cantidad) {
        var campanas = IntStream.range(0, cantidad).mapToObj(i -> new ReporteInstitucionalDTO.Campana(i + 1,
                nombre, objetivo, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "ACTIVA", "ACTIVO",
                2L, 2, new BigDecimal("75.00"), null, null)).toList();
        return new ReporteInstitucionalDTO(new ReporteInstitucionalDTO.InstitucionDatos(5L, "Universidad Perú"),
                LocalDateTime.of(2026, 10, 25, 12, 30), new ReporteInstitucionalDTO.Filtros(null, null, null),
                new ReporteInstitucionalDTO.Resumen(cantidad, 0, cantidad, 0, 0, 0, cantidad * 2L, 2, cantidad * 2L,
                        cantidad == 0 ? null : new BigDecimal("75.00"), 0, 0, 0), campanas, List.of());
    }
    String texto(ReporteInstitucionalDTO r) { return new String(csv.generar(r), StandardCharsets.UTF_8); }

    // Analizador de prueba: verifica celdas reales, no solo subcadenas del archivo.
    List<List<String>> leer(String csv) {
        List<List<String>> filas = new ArrayList<>(); List<String> fila = new ArrayList<>();
        StringBuilder celda = new StringBuilder(); boolean comillas = false;
        for (int i = csv.startsWith("\uFEFF") ? 1 : 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (c == '"') {
                if (comillas && i + 1 < csv.length() && csv.charAt(i + 1) == '"') { celda.append('"'); i++; }
                else comillas = !comillas;
            } else if (!comillas && c == ',') { fila.add(celda.toString()); celda.setLength(0); }
            else if (!comillas && c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                fila.add(celda.toString()); filas.add(List.copyOf(fila)); fila.clear(); celda.setLength(0); i++;
            } else celda.append(c);
        }
        assertFalse(comillas); assertTrue(fila.isEmpty()); return filas;
    }
    @Test void utf8BomYCaracteresEspanolesSeConservan() {
        byte[] bytes = csv.generar(reporte("Movilidad sostenible Ñ", "Acción ecológica", 1));
        assertArrayEquals(new byte[] {(byte)0xEF, (byte)0xBB, (byte)0xBF}, Arrays.copyOf(bytes, 3));
        var filas = leer(new String(bytes, StandardCharsets.UTF_8));
        assertEquals("Universidad Perú", filas.get(1).get(2)); assertEquals("Movilidad sostenible Ñ", filas.get(1).get(21));
    }
    @Test void comasComillasYSaltosNoRompenColumnas() {
        String nombre = "Movilidad, \"limpia\""; String objetivo = "Reducir\r\nviajes\ny emisiones";
        var filas = leer(texto(reporte(nombre, objetivo, 2)));
        assertEquals(3, filas.size()); assertEquals(33, filas.getFirst().size());
        for (var fila : filas) assertEquals(33, fila.size());
        assertEquals(nombre, filas.get(1).get(21)); assertEquals(objetivo, filas.get(1).get(22));
    }
    @Test void protegeTodosLosPrefijosDeFormulasIncluyendoEspaciosYControles() {
        for (String peligro : List.of("=1+1", "+SUM(A1)", "-2+3", "@SUM(A1)", "  =cmd()", "\ttexto",
                "\rtexto", "\ntexto", "\uFEFF=1+1", "\u0000=cmd()", "\u00A0+1")) {
            var fila = leer(texto(reporte(peligro, peligro, 1))).get(1);
            assertEquals("'" + peligro, fila.get(21)); assertEquals("'" + peligro, fila.get(22));
        }
    }
    @Test void textoLegitimoNoSeAltera() {
        String nombre = "Campaña 2026 - movilidad";
        assertEquals(nombre, leer(texto(reporte(nombre, "Caminar 20 minutos", 1))).get(1).get(21));
    }
    @Test void numerosYFechasSonEstablesNoDependenDeLocale() {
        var fila = leer(texto(reporte("Prueba", "Objetivo", 1))).get(1);
        assertEquals("75.00", fila.get(29)); assertEquals("2026-01-01", fila.get(23));
        assertEquals("2026-10-25T12:30", fila.get(3)); assertEquals("", fila.get(30)); assertEquals("", fila.get(31));
    }
    @Test void sinCampanasConservaCabeceraResumenYColumnasVacias() {
        var filas = leer(texto(reporte("No usada", "", 0)));
        assertEquals(2, filas.size()); assertEquals("RESUMEN_SIN_CAMPANAS", filas.get(1).getFirst());
        assertEquals("0", filas.get(1).get(7)); assertEquals("", filas.get(1).get(20));
        assertEquals(33, filas.get(1).size());
    }
    @Test void camposNulosNoSeConviertenEnValoresInventados() {
        var base = reporte("Campaña", "Objetivo", 1);
        var c = new ReporteInstitucionalDTO.Campana(1, null, null, null, null, "DATOS_INCOMPLETOS",
                "NO_DISPONIBLE", 0, null, null, null, null);
        var r = new ReporteInstitucionalDTO(base.institucion(), base.generadoEl(), base.filtros(), base.resumen(), List.of(c), List.of("Faltan datos"));
        var fila = leer(texto(r)).get(1); assertEquals("", fila.get(21)); assertEquals("", fila.get(29));
        assertEquals("Faltan datos", fila.get(32));
    }
    @Test void milesDeCampanasConservanNumeroDeFilasYContenidoFinal() {
        var filas = leer(texto(reporte("Campaña amplia", "Objetivo", 5000)));
        assertEquals(5001, filas.size()); assertEquals("5000", filas.getLast().get(20));
    }
    @Test void generacionParalelaEsDeterministaYNoMutaElDto() throws Exception {
        var r = reporte("Campaña", "Objetivo", 10); byte[] esperado = csv.generar(r);
        ExecutorService ejecutor = Executors.newFixedThreadPool(4);
        try {
            var tareas = IntStream.range(0, 12).mapToObj(i -> (Callable<byte[]>) () -> csv.generar(r)).toList();
            for (var futuro : ejecutor.invokeAll(tareas)) assertArrayEquals(esperado, futuro.get());
        } finally { ejecutor.shutdownNow(); }
        assertEquals(10, r.campanas().size());
    }
}
