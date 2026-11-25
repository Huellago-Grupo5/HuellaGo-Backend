package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.UsuarioRecomendacionIARepository;
import com.huellago.backend.services.ContextoRecomendacionesIAService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecomendacionImpactoPendienteTest {
    UsuarioRecomendacionIARepository asociaciones = mock(UsuarioRecomendacionIARepository.class);
    ContextoRecomendacionesIAService contextos = mock(ContextoRecomendacionesIAService.class);
    RecomendacionServiceImpl servicio = new RecomendacionServiceImpl();
    Usuario usuario = new Usuario();

    @BeforeEach void preparar() {
        usuario.setId(70L);
        ReflectionTestUtils.setField(servicio, "usuarioRecomendacionIARepository", asociaciones);
        ReflectionTestUtils.setField(servicio, "contextoRecomendacionesIAService", contextos);
        ReflectionTestUtils.setField(servicio, "catalogoAccionesAmbientalesService", new CatalogoAccionesAmbientalesServiceImpl());
        var politica = new PoliticaMedicionInicial();
        ReflectionTestUtils.setField(politica, "factorElectricidad", "0.23825");
        ReflectionTestUtils.setField(politica, "referenciaElectricidad", "Factor registrado");
        ReflectionTestUtils.setField(servicio, "politicaMedicionInicial", politica);
        var contexto = new ContextoRecomendacionesIADTO(
                new ContextoRecomendacionesIADTO.Transporte("Auto", new BigDecimal("100"), 5),
                new ContextoRecomendacionesIADTO.Energia("Casa", 3, "Electricidad", new BigDecimal("10")),
                null, null);
        when(contextos.construir(70L)).thenReturn(new ContextoRecomendacionesIAService.Resultado(
                contexto, List.of(), List.of(), List.of()));
    }

    UsuarioRecomendacionIA pendiente(int id, String accion, String categoria, LocalDateTime fecha) {
        CategoriaHabito c = new CategoriaHabito(); c.setNombre(categoria);
        RecomendacionIA r = new RecomendacionIA(); r.setId(id); r.setAccionId(accion);
        r.setVersionAccion("1"); r.setCategoria(c); r.setPrioridad("MEDIA");
        UsuarioRecomendacionIA a = new UsuarioRecomendacionIA(id, "Pendiente", fecha, null, usuario, r);
        a.setTituloInstantanea("Título " + id); a.setDescripcionInstantanea("Descripción " + id);
        a.setMotivoInstantanea("Motivo " + id); a.setPrioridadInstantanea("MEDIA");
        a.setMetodoGeneracion("OLLAMA_SELECCION_PRESENTACION_CONTROLADA");
        return a;
    }
    @Test void ordenaDosEscenariosDeTransporteComparablesSinEscribirInstantaneas() {
        var evitar = pendiente(10, "TR_PLANIFICAR_DESPLAZAMIENTOS", "Transporte", LocalDateTime.of(2026, 11, 1, 10, 0));
        var sustituir = pendiente(11, "TR_EXPLORAR_ALTERNATIVAS_AUTO", "Transporte", LocalDateTime.of(2026, 11, 2, 10, 0));
        when(asociaciones.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(70L, "pendiente"))
                .thenReturn(List.of(sustituir, evitar));
        var lista = servicio.pendientes(usuario, null, new EscenarioImpactoIADTO(
                new BigDecimal("20"), new BigDecimal("5"), null));
        assertEquals(List.of(10, 11), lista.stream().map(RecomendacionPersonalizadaDTO::getUsuarioRecomendacionId).toList());
        assertEquals(new BigDecimal("2.40"), lista.getFirst().getImpactoPotencialKgCo2eSemanal());
        assertEquals(new BigDecimal("0.60"), lista.getLast().getImpactoPotencialKgCo2eSemanal());
        assertEquals("MEDIA", lista.getFirst().getPrioridad());
        assertEquals("Título 10", evitar.getTituloInstantanea());
        verify(asociaciones, never()).save(any());
        verify(asociaciones, never()).saveAndFlush(any());
    }
    @Test void sinEscenarioConservaOrdenYNoLeeContextoOtraVez() {
        var primero = pendiente(10, "TR_PLANIFICAR_DESPLAZAMIENTOS", "Transporte", LocalDateTime.of(2026, 11, 1, 10, 0));
        var segundo = pendiente(11, "TR_EXPLORAR_ALTERNATIVAS_AUTO", "Transporte", LocalDateTime.of(2026, 11, 2, 10, 0));
        when(asociaciones.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(70L, "pendiente"))
                .thenReturn(List.of(primero, segundo));
        var lista = servicio.pendientes(usuario, null);
        assertEquals(List.of(11, 10), lista.stream().map(RecomendacionPersonalizadaDTO::getUsuarioRecomendacionId).toList());
        assertEquals("NO_ESTIMABLE", lista.getFirst().getImpactoEsperado());
        verifyNoInteractions(contextos);
    }
}
