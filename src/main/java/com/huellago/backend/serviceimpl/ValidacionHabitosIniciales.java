package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Habito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/** Reglas compartidas por estado inicial, actualizacion y calculo. */
final class ValidacionHabitosIniciales {
    private ValidacionHabitosIniciales() {}
    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").strip().toLowerCase(Locale.ROOT);
    }
    static List<String> categoriasPendientes(List<Habito> habitos) {
        Map<String,List<Habito>> grupos=habitos.stream()
                .filter(h -> h.getCategoria()!=null && h.getCategoria().getNombre()!=null)
                .collect(Collectors.groupingBy(h -> normalizar(h.getCategoria().getNombre())));
        Map<String,List<String>> requeridos=new LinkedHashMap<>();
        requeridos.put("Transporte",List.of("medio","kmSemana","diasSemana"));
        requeridos.put("Energía",List.of("vivienda","personas","fuente"));
        requeridos.put("Alimentación",List.of("tipo"));
        requeridos.put("Residuos",List.of("plasticos","reciclas"));
        List<String> pendientes=new ArrayList<>();
        requeridos.forEach((categoria,campos) -> {
            List<Habito> datos=grupos.getOrDefault(normalizar(categoria),List.of());
            try {
                for (String campo:campos) validarCampo(datos,campo);
                if (normalizar(categoria).equals("energia") && datos.stream().anyMatch(h -> "consumoKwhDiario".equalsIgnoreCase(h.getNombre()))) {
                    validarCampo(datos,"consumoKwhDiario");
                    Habito fuente=datos.stream().filter(h -> "fuente".equalsIgnoreCase(h.getNombre())).findFirst().orElseThrow();
                    if (!"electricidad".equalsIgnoreCase(fuente.getUnidad().trim()))
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El consumo en kWh/dia requiere Electricidad");
                }
            } catch (ResponseStatusException error) { pendientes.add(categoria); }
        });
        return List.copyOf(pendientes);
    }
    static void validarCampo(List<Habito> habitos, String nombre) {
        List<Habito> encontrados = habitos.stream()
                .filter(habito -> nombre.equalsIgnoreCase(habito.getNombre()))
                .toList();
        if (encontrados.size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    encontrados.isEmpty() ? "Falta el hábito " + nombre
                            : "Hay registros duplicados del hábito " + nombre);
        }
        Habito habito = encontrados.get(0);
        BigDecimal valor = habito.getValor();
        String unidad = habito.getUnidad() == null ? ""
                : habito.getUnidad().trim().toLowerCase(Locale.ROOT);
        if (valor == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Falta el valor del hábito " + nombre);
        }
        boolean entero = valor.stripTrailingZeros().scale() <= 0;
        boolean valido = switch (nombre) {
            case "medio" -> valor.signum() == 0
                    && Set.of("bicicleta", "caminando", "bus", "auto", "moto").contains(unidad);
            case "kmSemana" -> valor.signum() > 0 && "km/semana".equals(unidad);
            case "diasSemana" -> entero && valor.signum() > 0
                    && valor.compareTo(BigDecimal.valueOf(7)) <= 0 && "dias/semana".equals(unidad);
            case "vivienda" -> valor.signum() == 0
                    && Set.of("casa", "departamento").contains(unidad);
            case "personas" -> entero && valor.signum() > 0 && "personas".equals(unidad);
            case "fuente" -> valor.signum() == 0
                    && Set.of("electricidad", "gas", "solar").contains(unidad);
            case "tipo" -> valor.signum() == 0
                    && Set.of("vegana", "vegetariana", "mixta").contains(unidad);
            case "plasticos" -> valor.signum() == 0
                    && Set.of("bajo", "medio", "alto").contains(unidad);
            case "consumoKwhDiario" -> valor.signum() >= 0 && "kwh/dia".equals(unidad);
            case "reciclas" -> entero
                    && (valor.signum() == 0 || valor.compareTo(BigDecimal.ONE) == 0)
                    && "boolean".equals(unidad);
            default -> false;
        };
        if (!valido) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Valor o unidad inválidos para " + nombre);
        }
    }

}
