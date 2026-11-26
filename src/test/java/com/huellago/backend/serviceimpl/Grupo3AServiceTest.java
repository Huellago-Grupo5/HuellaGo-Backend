package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.*;
import com.huellago.backend.dtos.*;
import com.huellago.backend.repositories.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Grupo3AServiceTest {
    Usuario usuario(long id) { Usuario u = new Usuario(); u.setId(id); return u; }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 4})
    void desglosePreservaAusenciasYCeroYOrdenEstable(int ausentes) {
        var usuarios = mock(UsuarioRepository.class);
        var huellas = mock(HuellaCarbonoRepository.class);
        var servicio = new HuellaCarbonoServiceImpl();
        ReflectionTestUtils.setField(servicio, "usuarioRepository", usuarios);
        ReflectionTestUtils.setField(servicio, "huellaCarbonoRepository", huellas);
        when(usuarios.existsById(22L)).thenReturn(true);
        var h = new HuellaCarbono(); h.setUsuario(usuario(22));
        h.setCo2Total(ausentes == 4 ? null : BigDecimal.TEN);
        h.setCo2Transporte(ausentes > 0 ? null : BigDecimal.TEN);
        h.setCo2Energia(ausentes > 1 ? null : BigDecimal.TEN);
        h.setCo2Alimentacion(ausentes > 2 ? null : BigDecimal.ZERO);
        h.setCo2Residuos(ausentes > 3 ? null : BigDecimal.ONE);
        when(huellas.findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(22L)).thenReturn(h);
        var r = servicio.obtenerDesglose(22L);
        assertEquals(4, r.getActividades().size());
        assertEquals(ausentes, r.getCategoriasIncompletas().size());
        assertEquals(ausentes == 0, r.isCompleto());
        assertEquals(h.getCo2Total(), r.getCo2Total());
        if (ausentes == 0) {
            assertEquals(List.of("Transporte", "Energ\u00eda", "Residuos", "Alimentaci\u00f3n"),
                r.getActividades().stream().map(ActividadHuellaDTO::getCategoria).toList());
            assertTrue(r.getActividades().get(3).isCompleta());
            assertEquals(BigDecimal.ZERO, r.getActividades().get(3).getCo2());
        }
        if (ausentes > 0) assertNull(r.getActividades().get(3).getCo2());
    }

    @Test void sinHuellaEs404() {
        var usuarios = mock(UsuarioRepository.class);
        var servicio = new HuellaCarbonoServiceImpl();
        ReflectionTestUtils.setField(servicio, "usuarioRepository", usuarios);
        ReflectionTestUtils.setField(servicio, "huellaCarbonoRepository", mock(HuellaCarbonoRepository.class));
        when(usuarios.existsById(22L)).thenReturn(true);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> servicio.obtenerDesglose(22L)).getStatusCode().value());
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void dashboardAgrupaVariantesSinPerderHabitos(boolean completo) {
        var usuarios = mock(UsuarioRepository.class); var habitos = mock(HabitoRepository.class);
        var servicio = new DashboardServiceImpl();
        ReflectionTestUtils.setField(servicio, "usuarioRepository", usuarios);
        ReflectionTestUtils.setField(servicio, "habitoRepository", habitos);
        ReflectionTestUtils.setField(servicio, "huellaCarbonoRepository", mock(HuellaCarbonoRepository.class));
        ReflectionTestUtils.setField(servicio, "usuarioRetoRepository", mock(UsuarioRetoRepository.class));
        ReflectionTestUtils.setField(servicio, "usuarioRecomendacionIARepository", mock(UsuarioRecomendacionIARepository.class));
        when(usuarios.findById(22L)).thenReturn(Optional.of(usuario(22)));
        var filas = new ArrayList<HabitoRepository.ConteoCategoria>();
        filas.add(fila("Energia", 2)); filas.add(fila(" ENERG\u00cdA ", 3));
        if (completo) { filas.add(fila("Transporte", 3)); filas.add(fila("Alimentacion", 1)); filas.add(fila("Residuos", 2)); }
        when(habitos.contarPorCategoria(22L)).thenReturn(filas);
        var r = servicio.obtenerDashboard(22L);
        assertEquals(completo ? 11L : 5L, r.getCantidadHabitos());
        assertEquals(5L, r.getHabitosPorCategoria().get("Energ\u00eda"));
        assertEquals(completo ? 1L : 0L, r.getHabitosPorCategoria().get("Alimentaci\u00f3n"));
        assertEquals("Energ\u00eda", r.getCategoriaConMasHabitos()); assertNull(r.getHuellaCarbonoActual());
        verify(habitos).contarPorCategoria(22L); verify(habitos, never()).contarPorCategoria(99L);
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Energia", "Energ\u00eda", " ENERG\u00cdA "})
    void dashboardReconoceCadaVarianteIndividual(String categoria) {
        var usuarios = mock(UsuarioRepository.class); var habitos = mock(HabitoRepository.class);
        var servicio = new DashboardServiceImpl();
        ReflectionTestUtils.setField(servicio, "usuarioRepository", usuarios);
        ReflectionTestUtils.setField(servicio, "habitoRepository", habitos);
        ReflectionTestUtils.setField(servicio, "huellaCarbonoRepository", mock(HuellaCarbonoRepository.class));
        ReflectionTestUtils.setField(servicio, "usuarioRetoRepository", mock(UsuarioRetoRepository.class));
        ReflectionTestUtils.setField(servicio, "usuarioRecomendacionIARepository", mock(UsuarioRecomendacionIARepository.class));
        when(usuarios.findById(22L)).thenReturn(Optional.of(usuario(22)));
        when(habitos.contarPorCategoria(22L)).thenReturn(List.of(fila(categoria, 3)));
        var r = servicio.obtenerDashboard(22L);
        assertEquals(3L, r.getCantidadHabitos()); assertEquals(3L, r.getHabitosPorCategoria().get("Energ\u00eda"));
    }

    @Test void dashboardSinHabitosConservaCuatroCategoriasYCategoriaAusenteNoPierdeTotal() {
        var usuarios = mock(UsuarioRepository.class); var habitos = mock(HabitoRepository.class);
        var servicio = new DashboardServiceImpl();
        ReflectionTestUtils.setField(servicio, "usuarioRepository", usuarios);
        ReflectionTestUtils.setField(servicio, "habitoRepository", habitos);
        ReflectionTestUtils.setField(servicio, "huellaCarbonoRepository", mock(HuellaCarbonoRepository.class));
        ReflectionTestUtils.setField(servicio, "usuarioRetoRepository", mock(UsuarioRetoRepository.class));
        ReflectionTestUtils.setField(servicio, "usuarioRecomendacionIARepository", mock(UsuarioRecomendacionIARepository.class));
        when(usuarios.findById(22L)).thenReturn(Optional.of(usuario(22)));
        var vacio = servicio.obtenerDashboard(22L);
        assertEquals(0L, vacio.getCantidadHabitos()); assertNull(vacio.getCategoriaConMasHabitos());
        assertEquals(4, vacio.getHabitosPorCategoria().size());
        when(habitos.contarPorCategoria(22L)).thenReturn(List.of(fila(null, 2)));
        var historico = servicio.obtenerDashboard(22L);
        assertEquals(2L, historico.getCantidadHabitos()); assertNull(historico.getCategoriaConMasHabitos());
        assertTrue(historico.getHabitosPorCategoria().values().stream().allMatch(c -> c == 0));
    }

    HabitoRepository.ConteoCategoria fila(String nombre, long cantidad) {
        return new HabitoRepository.ConteoCategoria() {
            public String getNombre() { return nombre; }
            public Long getCantidad() { return cantidad; }
        };
    }

    @Test void consultaHabitosIncluyeIdentidadYCategoriaSinConsultarOtrosUsuarios() {
        var repositorio = mock(HabitoRepository.class); var servicio = new HabitoServiceImpl();
        ReflectionTestUtils.setField(servicio, "habitoRepository", repositorio);
        var h = new Habito(); h.setId(70L); h.setUsuario(usuario(22)); h.setNombre("diasSemana");
        h.setValor(BigDecimal.valueOf(5)); h.setUnidad("dias");
        var c = new CategoriaHabito(); c.setId(1L); c.setNombre("Transporte"); h.setCategoria(c);
        when(repositorio.findByUsuario_IdOrderByIdAsc(22L)).thenReturn(List.of(h));
        var r = servicio.listar(usuario(22));
        assertEquals(70L, r.getFirst().getId()); assertEquals("Transporte", r.getFirst().getCategoriaNombre());
        assertEquals(22L, r.getFirst().getUsuarioId()); assertEquals(h.getValor(), r.getFirst().getValor());
        assertTrue(servicio.listar(usuario(99)).isEmpty());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> servicio.listar(null)).getStatusCode().value());
        verify(repositorio, never()).findAll();
    }
}
