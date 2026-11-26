package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.*;
import com.huellago.backend.dtos.RecomendacionPersonalizadaDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huellago.backend.repositories.UsuarioRecomendacionIARepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecomendacionPendienteServiceTest {
    @Mock UsuarioRecomendacionIARepository asociaciones;
    @InjectMocks RecomendacionServiceImpl servicio;
    Usuario usuario;
    LocalDateTime fecha = LocalDateTime.of(2026, 11, 24, 10, 0);

    @BeforeEach void preparar() { usuario = new Usuario(); usuario.setId(70L); }

    private UsuarioRecomendacionIA pendiente(int id, String categoria, String prioridad,
                                              LocalDateTime generada, boolean ia) {
        CategoriaHabito c = new CategoriaHabito(); c.setNombre(categoria);
        RecomendacionIA r = new RecomendacionIA(); r.setId(id); r.setCategoria(c);
        r.setTitulo("Titulo compartido " + id); r.setDescripcion("Descripcion compartida " + id);
        r.setPrioridad(prioridad);
        UsuarioRecomendacionIA p = new UsuarioRecomendacionIA(id, "Pendiente", generada, null, usuario, r);
        if (ia) {
            r.setAccionId("ACCION_" + id); r.setVersionAccion("1");
            p.setMetodoGeneracion("OLLAMA_SELECCION_PRESENTACION_CONTROLADA");
            p.setBasePriorizacion("HABITOS_REGISTRADOS");
            p.setTituloInstantanea("Titulo IA " + id);
            p.setDescripcionInstantanea("Descripcion IA " + id);
            p.setMotivoInstantanea("Motivo IA " + id);
            p.setPrioridadInstantanea(prioridad);
        }
        return p;
    }

    private void registros(List<UsuarioRecomendacionIA> items) {
        when(asociaciones.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(70L, "pendiente"))
                .thenReturn(items);
    }

    @Test void mezclaLegacyEIAConInstantaneasYOrdenEstable() {
        var legado = pendiente(10, "Residuos", "MEDIA", fecha, false);
        var ia = pendiente(11, "Transporte", "ALTA", fecha.plusDays(1), true);
        var energia = pendiente(12, "Energía", "ALTA", fecha.plusDays(2), true);
        var transporte = pendiente(13, "Transporte", "ALTA", fecha.plusDays(1), true);
        registros(List.of(legado, energia, transporte, ia));
        var resultado = servicio.pendientes(usuario, null);
        assertEquals(List.of(11, 13, 12, 10), resultado.stream().map(x -> x.getUsuarioRecomendacionId()).toList());
        assertEquals("Titulo IA 11", resultado.getFirst().getTitulo());
        assertEquals("Motivo IA 11", resultado.getFirst().getMotivo());
        assertEquals("NO_ESTIMABLE", resultado.getFirst().getImpactoEsperado());
        assertTrue(resultado.getFirst().getExplicacionImpacto().contains("CO2e evitado"));
        assertEquals("Titulo compartido 10", resultado.getLast().getTitulo());
        assertNull(resultado.getLast().getMotivo());
        assertNull(resultado.getLast().getImpactoEsperado());
        assertEquals("Pendiente", resultado.getFirst().getEstado());
        verify(asociaciones, never()).saveAndFlush(any());
    }

    @Test void filtroCategoriaAceptaAcentosYNoMuestraOtrasCategorias() {
        var energia = pendiente(12, "Energía", "MEDIA", fecha, true);
        registros(List.of(pendiente(10, "Transporte", "ALTA", fecha, true), energia));
        var resultado = servicio.pendientes(usuario, "  ENERGIA  ");
        assertEquals(List.of(12), resultado.stream().map(x -> x.getUsuarioRecomendacionId()).toList());
    }

    @Test void sinPendientesDevuelveListaVaciaYCategoriaInvalidaSeRechaza() {
        registros(List.of());
        assertEquals(List.of(), servicio.pendientes(usuario, "Transporte"));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> servicio.pendientes(usuario, "Otra")).getStatusCode());
    }

    @Test void noExponeOtrosUsuariosNiCompletadas() {
        var otro = new Usuario(); otro.setId(71L);
        var ajena = pendiente(15, "Transporte", "ALTA", fecha, true); ajena.setUsuario(otro);
        var completada = pendiente(16, "Transporte", "ALTA", fecha, true); completada.setEstado("completado");
        registros(List.of(ajena, completada));
        assertTrue(servicio.pendientes(usuario, null).isEmpty());
    }

    @Test void instantaneaIncompletaNoUsaTextoCompartido() {
        var ia = pendiente(17, "Transporte", "MEDIA", fecha, true);
        ia.setDescripcionInstantanea(null); registros(List.of(ia));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, assertThrows(ResponseStatusException.class,
                () -> servicio.pendientes(usuario, null)).getStatusCode());
    }

    @Test void prioridadNuevaTieneJustificacionYLaHistoricaConservaSuValor() {
        var nueva = pendiente(18, "Energía", "BAJA", fecha, true);
        nueva.getRecomendacionIA().setAccionId("EN_REVISAR_REGISTRO_HOGAR");
        nueva.setBasePriorizacion(PrioridadAccionAmbientalIA.BASE);
        var historica = pendiente(19, "Transporte", "MEDIA", fecha, true);
        registros(List.of(nueva, historica));
        var resultado = servicio.pendientes(usuario, null);
        assertEquals(List.of(19, 18), resultado.stream().map(x -> x.getUsuarioRecomendacionId()).toList());
        assertNull(resultado.getFirst().getJustificacionPrioridad());
        assertEquals("MEDIA", resultado.getFirst().getPrioridad());
        assertTrue(resultado.getLast().getJustificacionPrioridad().contains("informativa"));
        assertEquals("BAJA", resultado.getLast().getPrioridad());
    }

    @Test void jsonLegadoOmiteMetadatosDeImpactoYNoLosConfundeConCo2Categoria() throws Exception {
        var dto = new RecomendacionPersonalizadaDTO(1, 2, "Transporte", "Título", "Descripción",
                null, "MEDIA", new java.math.BigDecimal("12.00"), "Pendiente", null);
        String json = new ObjectMapper().writeValueAsString(dto);
        assertFalse(json.contains("impactoEsperado"));
        assertFalse(json.contains("explicacionImpacto"));
        assertFalse(json.contains("justificacionPrioridad"));
        assertTrue(json.contains("\"co2Categoria\":12.00"));
    }
}
