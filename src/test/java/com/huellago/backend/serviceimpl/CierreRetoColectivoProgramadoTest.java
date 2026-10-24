package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Reto;
import com.huellago.backend.entities.TipoReto;
import com.huellago.backend.repositories.RetoRepository;
import com.huellago.backend.services.RetoColectivoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CierreRetoColectivoProgramadoTest {
    @Mock RetoRepository retos;
    @Mock RetoColectivoService servicio;
    @InjectMocks CierreRetoColectivoProgramado cierre;

    @Test
    void cierraSoloColectivosConPeriodoVencido() {
        Reto vencido = new Reto();
        vencido.setId(7);
        vencido.setFechaFin(LocalDate.now().minusDays(1));
        Reto vigente = new Reto();
        vigente.setId(8);
        vigente.setFechaFin(LocalDate.now().plusDays(1));
        when(retos.findByTipo(TipoReto.COLECTIVO)).thenReturn(List.of(vencido, vigente));

        cierre.cerrarVencidos();

        verify(servicio).cerrarVencido(7);
        verify(servicio, never()).cerrarVencido(8);
    }

    @Test
    void falloDeUnCierreNoImpideProcesarElSiguiente() {
        Reto primero = new Reto();
        primero.setId(7);
        primero.setFechaFin(LocalDate.now().minusDays(1));
        Reto segundo = new Reto();
        segundo.setId(8);
        segundo.setFechaFin(LocalDate.now().minusDays(1));
        when(retos.findByTipo(TipoReto.COLECTIVO)).thenReturn(List.of(primero, segundo));
        doThrow(new IllegalStateException("fallo de prueba")).when(servicio).cerrarVencido(7);

        cierre.cerrarVencidos();

        verify(servicio).cerrarVencido(7);
        verify(servicio).cerrarVencido(8);
    }
}
