package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.HuellaCarbono;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.HuellaCarbonoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HuellaConsultaFechaServiceTest {
    @Mock HuellaCarbonoRepository huellas;
    @InjectMocks HuellaCarbonoServiceImpl servicio;

    @BeforeEach void relojYPolitica() {
        servicio.relojConsulta = Clock.fixed(Instant.parse("2026-11-08T02:00:00Z"), ZoneOffset.UTC);
        servicio.politica = new PoliticaMedicionInicial();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-07", "2026-01-31", "2026-12-31", "2024-02-29", "2023-02-28", "9999-12-31", "0001-01-01"})
    void diarioUsaIntervaloSemiabiertoYConservaImportes(String texto) {
        LocalDate fecha = LocalDate.parse(texto);
        var h = huella(6, fecha.atTime(22, 35));
        when(huellas.findTopByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
                68L, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())).thenReturn(h);

        var resultado = servicio.obtenerHuellaDiaria(68L, fecha);

        assertEquals(new BigDecimal("5.72"), resultado.getCo2Total());
        assertEquals("diario (promedio estimado)", resultado.getPeriodo());
        assertEquals("kg CO2e", resultado.getUnidad());
        assertEquals(h.getFechaCalculo(), resultado.getFechaCalculo());
        assertEquals(new BigDecimal("4.48"), resultado.getCo2Energia());
        assertTrue(resultado.getMetodologia().contains("no es una medición"));
        assertEquals("NO_DEFINIDO", resultado.getNivelImpacto());
        assertEquals(new BigDecimal("40.06"), h.getCo2Total());
        assertEquals(new BigDecimal("31.36"), h.getCo2Energia());
        verify(huellas).findTopByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
                68L, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay());
        verifyNoMoreInteractions(huellas);
    }

    @Test void fechaOmitidaUsaLimaAunqueElRelojFijoEsteEnUtc() {
        var fecha = LocalDate.of(2026, 11, 7); // En UTC ya es 8; en Lima todavía es 7.
        when(huellas.findTopByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
                68L, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())).thenReturn(huella(6, fecha.atTime(22, 35)));
        assertEquals(fecha, servicio.obtenerHuellaDiaria(68L).getFechaCalculo().toLocalDate());
        verifyNoMoreInteractions(huellas);
    }

    @Test void componentesHistoricosAusentesNoSeConviertenEnCeroNiSeReescriben() {
        var fecha = LocalDate.of(2026, 11, 7);
        var h = huella(6, fecha.atTime(10, 0));
        h.setCo2Energia(null);
        when(huellas.findTopByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
                68L, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())).thenReturn(h);
        var diario = servicio.obtenerHuellaDiaria(68L, fecha);
        assertEquals(new BigDecimal("5.72"), diario.getCo2Total());
        assertNull(diario.getCo2Energia());
        assertNull(h.getCo2Energia());
        assertEquals(new BigDecimal("40.06"), h.getCo2Total());
        verify(huellas, never()).save(any());
    }

    @Test void relojDeProduccionDefineAmericaLima() {
        assertEquals(ZoneId.of("America/Lima"), new ConsultaHuellaConfiguration().huellaConsultaClock().getZone());
    }

    @Test void diarioSinDatosDevuelve404SinEscrituras() {
        var error = assertThrows(ResponseStatusException.class,
                () -> servicio.obtenerHuellaDiaria(68L, LocalDate.of(2026, 11, 7)));
        assertEquals(404, error.getStatusCode().value());
        assertEquals("No hay cálculos de huella de carbono registrados para la fecha seleccionada.", error.getReason());
        verify(huellas, never()).save(any());
    }

    @Test void historialFiltradoConservaOrdenYNoSumaCalculos() {
        var fecha = LocalDate.of(2026, 11, 7);
        var seis = huella(6, fecha.atTime(22, 35));
        var cinco = huella(5, fecha.atTime(22, 35)); cinco.setCo2Total(new BigDecimal("26.71"));
        when(huellas.findByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
                68L, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())).thenReturn(List.of(seis, cinco));
        var resultado = servicio.obtenerHistorial(68L, fecha);
        assertEquals(List.of(new BigDecimal("40.06"), new BigDecimal("26.71")),
                resultado.stream().map(r -> r.getCo2Total()).toList());
        assertTrue(resultado.stream().allMatch(r -> "semanal".equals(r.getPeriodo())));
        verifyNoMoreInteractions(huellas);
    }

    @Test void historialCompletoNoAplicaFiltroNiUsaHoy() {
        var antigua = huella(5, LocalDateTime.of(2026, 10, 1, 10, 0));
        when(huellas.findByUsuario_IdOrderByFechaCalculoDescIdDesc(68L)).thenReturn(List.of(antigua));
        assertEquals(antigua.getFechaCalculo(), servicio.obtenerHistorial(68L).getFirst().getFechaCalculo());
        assertEquals(1, servicio.obtenerHistorial(68L, null).size());
        verify(huellas, times(2)).findByUsuario_IdOrderByFechaCalculoDescIdDesc(68L);
        verifyNoMoreInteractions(huellas);
    }

    @Test void historialFiltradoSinDatosEsListaVacia() {
        assertTrue(servicio.obtenerHistorial(68L, LocalDate.of(2026, 11, 7)).isEmpty());
        verify(huellas, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"+999999999-12-31", "-999999999-01-01", "0000-01-01", "+10000-01-01"})
    void rangoInvalidoNoConsultaRepositorioNiDesborda(String texto) {
        var fecha = LocalDate.parse(texto);
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> servicio.obtenerHuellaDiaria(68L, fecha)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> servicio.obtenerHistorial(68L, fecha)).getStatusCode().value());
        verifyNoInteractions(huellas);
    }

    private HuellaCarbono huella(long id, LocalDateTime fecha) {
        var usuario = new Usuario(); usuario.setId(68L);
        var h = new HuellaCarbono(); h.setId(id); h.setUsuario(usuario); h.setFechaCalculo(fecha);
        h.setCo2Total(new BigDecimal("40.06")); h.setCo2Energia(new BigDecimal("31.36"));
        return h;
    }
}
