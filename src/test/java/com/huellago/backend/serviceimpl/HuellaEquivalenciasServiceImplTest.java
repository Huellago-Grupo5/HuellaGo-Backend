package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.HuellaCarbono;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.HuellaCarbonoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HuellaEquivalenciasServiceImplTest {
    @Mock HuellaCarbonoRepository huellaCarbonoRepository;
    @InjectMocks HuellaCarbonoServiceImpl servicio;

    @ParameterizedTest
    @CsvSource({"0,0,0,0", "0.000,0,0,0", "0.009,0,0,0", "0.01,0,0,0",
            "0.05,0,0,0", "0.1,0,0,0", "1.25,1,5,0", "10.50,9,43,3"})
    void convierteConFactoresEPAEnUnidadesCompatibles(String total, long arboles,
                                                            long bicicleta, long reciclaje) {
        var huella = preparar(new BigDecimal(total));
        var resultado = servicio.obtenerEquivalencias(22L);
        assertEquals(22L, resultado.getUsuarioId());
        assertEquals(new BigDecimal(total), resultado.getCo2Total());
        assertEquals(arboles, resultado.getArboles());
        assertEquals(bicicleta, resultado.getBicicletaKm());
        assertEquals(reciclaje, resultado.getReciclajeKg());
        assertEquals("semanal", resultado.getPeriodoHuella());
        assertTrue(resultado.getFuente().contains("epa.gov"));
        assertTrue(resultado.getAdvertencia().contains("No son reducciones reales"));
        assertEquals(new BigDecimal(total), huella.getCo2Total());
        verify(huellaCarbonoRepository, never()).save(any());
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"-0.01", "-1"})
    void totalHistoricoAusenteONegativoDevuelve404(String total) {
        preparar(total == null ? null : new BigDecimal(total));
        var error = assertThrows(ResponseStatusException.class, () -> servicio.obtenerEquivalencias(22L));
        assertEquals(404, error.getStatusCode().value());
        assertEquals("No hay una huella de carbono válida para mostrar equivalencias", error.getReason());
    }

    @Test void sinRegistroConserva404() {
        var error = assertThrows(ResponseStatusException.class, () -> servicio.obtenerEquivalencias(22L));
        assertEquals(404, error.getStatusCode().value());
        assertEquals("Huella de carbono no encontrada", error.getReason());
    }

    @ParameterizedTest @ValueSource(strings = {"0", "0.49"})
    void aceptaLongMaxValueDespuesDelRedondeo(String fraccion) {
        BigDecimal total = BigDecimal.valueOf(Long.MAX_VALUE).add(new BigDecimal(fraccion))
                .multiply(new BigDecimal("0.393")).divide(new BigDecimal("1.609344"), 10, java.math.RoundingMode.HALF_UP);
        preparar(total);
        var resultado = servicio.obtenerEquivalencias(22L);
        assertEquals(Long.MAX_VALUE, resultado.getBicicletaKm());
        assertTrue(resultado.getArboles() >= 0);
        assertTrue(resultado.getReciclajeKg() >= 0);
    }

    @ParameterizedTest @ValueSource(strings = {"0.5", "1", "100000000000000000000"})
    void rechazaDesbordamientoInclusivePorRedondeo(String exceso) {
        preparar(BigDecimal.valueOf(Long.MAX_VALUE).add(new BigDecimal(exceso))
                .multiply(new BigDecimal("0.393")).divide(new BigDecimal("1.609344"), 30, java.math.RoundingMode.CEILING));
        var error = assertThrows(ResponseStatusException.class, () -> servicio.obtenerEquivalencias(22L));
        assertEquals(404, error.getStatusCode().value());
        assertEquals("La huella de carbono excede el rango admitido para mostrar equivalencias", error.getReason());
        verify(huellaCarbonoRepository, never()).save(any());
    }

    private HuellaCarbono preparar(BigDecimal total) {
        var usuario = new Usuario(); usuario.setId(22L);
        var huella = new HuellaCarbono(); huella.setUsuario(usuario); huella.setCo2Total(total);
        when(huellaCarbonoRepository.findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(22L)).thenReturn(huella);
        return huella;
    }
}
