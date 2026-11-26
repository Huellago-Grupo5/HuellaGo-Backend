package com.huellago.backend.controllers;

import com.huellago.backend.dtos.AmbienteDTO;
import com.huellago.backend.services.AmbienteService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AmbienteControllerTest {
    private final AmbienteService servicio = mock(AmbienteService.class);
    private final AmbienteController controlador = new AmbienteController(servicio);

    @Test void rechazaCoordenadasFueraDeRangoAntesDeConsultar() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controlador.calidadAire(Double.NaN, -77)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controlador.calidadAire(91, -77)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controlador.puntos(-12, -181, 3000, AmbienteDTO.TipoPunto.RECICLAJE)).getStatusCode().value());
        verifyNoInteractions(servicio);
    }

    @Test void rechazaRadioFueraDeRango() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controlador.puntos(-12, -77, 5001, AmbienteDTO.TipoPunto.RECICLAJE)).getStatusCode().value());
        verifyNoInteractions(servicio);
    }
}
