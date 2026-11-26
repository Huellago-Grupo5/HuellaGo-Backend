package com.huellago.backend.serviceimpl;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class PrioridadAccionAmbientalIATest {
    @Test void cubreExactamenteElCatalogoVersionUnoSinAtribuirAltaInjustificada() {
        var catalogo = new CatalogoAccionesAmbientalesServiceImpl();
        var evaluaciones = catalogo.listarBorradores().stream().collect(Collectors.toMap(
                c -> c.definicion().id(), c -> PrioridadAccionAmbientalIA.evaluar(c.definicion().id(), c.definicion().version())));
        assertEquals(10, evaluaciones.size());
        assertEquals(Map.of("MEDIA", 6L, "BAJA", 4L), evaluaciones.values().stream()
                .collect(Collectors.groupingBy(PrioridadAccionAmbientalIA.Evaluacion::prioridad, Collectors.counting())));
        assertEquals("BAJA", evaluaciones.get("EN_REVISAR_REGISTRO_HOGAR").prioridad());
        assertEquals("MEDIA", evaluaciones.get("TR_PLANIFICAR_DESPLAZAMIENTOS").prioridad());
        assertTrue(evaluaciones.values().stream().allMatch(e -> !e.justificacion().isBlank()));
        assertThrows(IllegalArgumentException.class, () -> PrioridadAccionAmbientalIA.evaluar("TR_PLANIFICAR_DESPLAZAMIENTOS", "2"));
        assertThrows(IllegalArgumentException.class, () -> PrioridadAccionAmbientalIA.evaluar("DESCONOCIDA", "1"));
    }
}
