package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ReporteInstitucionalDTO;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.repositories.projections.*;
import com.huellago.backend.security.AutorizacionInstitucionalService;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReporteInstitucionalServiceImplTest {
    InstitucionRepository instituciones;
    UsuarioInstitucionRepository afiliaciones;
    RetoRepository retos;
    UsuarioRetoRepository participaciones;
    ResultadoRetoColectivoRepository resultados;
    ReporteInstitucionalServiceImpl servicio;
    Usuario usuario;
    LocalDate hoy = LocalDate.now();

    record Campana(Integer getId, String getTitulo, String getDescripcion, LocalDate getFechaInicio,
            LocalDate getFechaFin, Boolean getActivo) implements CampanaReporteVista { }
    record Avance(Integer getRetoId, Long getParticipantes, BigDecimal getSumaProgreso) implements ParticipacionReporteVista { }
    record Cierre(Integer getRetoId, Integer getParticipantes, BigDecimal getProgresoFinal,
            String getEstado, LocalDateTime getFechaCierre) implements ResultadoReporteVista { }
    record Totales(Long getTotal, Long getUsuarios) implements TotalesParticipacionVista { }

    @BeforeEach void preparar() {
        instituciones = mock(InstitucionRepository.class);
        afiliaciones = mock(UsuarioInstitucionRepository.class);
        retos = mock(RetoRepository.class);
        participaciones = mock(UsuarioRetoRepository.class);
        resultados = mock(ResultadoRetoColectivoRepository.class);
        servicio = new ReporteInstitucionalServiceImpl(instituciones, new AutorizacionInstitucionalService(afiliaciones),
                retos, participaciones, resultados);
        usuario = new Usuario(); usuario.setId(77L); usuario.setEcoPuntos(30); usuario.setNivel(1);
        Institucion institucion = new Institucion();
        ReflectionTestUtils.setField(institucion, "id", 5L); institucion.setNombre("Universidad Perú");
        institucion.setCorreoContacto("privado@institucion.test");
        when(instituciones.findById(5L)).thenReturn(Optional.of(institucion));
        when(afiliaciones.existsByUsuario_IdAndInstitucion_IdAndRol(77L, 5L, RolInstitucional.ADMIN_INSTITUCIONAL)).thenReturn(true);
        when(retos.buscarParaReporte(eq(5L), any(), any(), any(), any())).thenReturn(Page.empty());
        when(participaciones.resumirParaReporte(any())).thenReturn(List.of());
        when(resultados.buscarParaReporte(any())).thenReturn(List.of());
        when(participaciones.totalesParaReporte(eq(5L), any(), any(), any())).thenReturn(new Totales(0L, 0L));
    }

    Campana campana(int id, LocalDate inicio, LocalDate fin) { return new Campana(id, "Campaña " + id, "Reducir residuos", inicio, fin, true); }
    void campanas(CampanaReporteVista... datos) {
        when(retos.buscarParaReporte(eq(5L), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(datos)));
    }
    ReporteInstitucionalDTO consultar() { return servicio.consultar(5L, usuario, null, null, null); }
    void error(int codigo, Runnable accion) {
        assertEquals(codigo, assertThrows(ResponseStatusException.class, accion::run).getStatusCode().value());
    }

    @Test void institucionSinCampanasEsReporteValidoSinInventarProgreso() {
        var r = consultar();
        assertEquals(5L, r.institucion().id()); assertEquals("Universidad Perú", r.institucion().nombre());
        assertNotNull(r.generadoEl()); assertTrue(r.campanas().isEmpty());
        assertEquals(0, r.resumen().totalCampanas()); assertNull(r.resumen().progresoGeneralPonderado());
        assertEquals(1, r.advertencias().size());
        verifyNoInteractions(participaciones, resultados);
    }
    @Test void activaUsaPromedioHu29YTotalesDistinguenPersonasDeParticipaciones() {
        campanas(campana(10, hoy.minusDays(1), hoy.plusDays(1)), campana(11, hoy, hoy.plusDays(3)));
        when(participaciones.resumirParaReporte(List.of(10, 11))).thenReturn(List.of(
                new Avance(10, 2L, new BigDecimal("150")), new Avance(11, 1L, new BigDecimal("30"))));
        when(participaciones.totalesParaReporte(5L, null, null, null)).thenReturn(new Totales(3L, 2L));
        var r = consultar();
        assertEquals(new BigDecimal("75.00"), r.campanas().getFirst().progresoColectivo());
        assertEquals("ACTIVA", r.campanas().getFirst().estado());
        assertEquals("ACTIVO", r.campanas().getFirst().estadoColectivo());
        assertEquals(3, r.resumen().totalParticipacionesRegistradas());
        assertEquals(2, r.resumen().usuariosParticipantesUnicos());
        assertEquals(new BigDecimal("60.00"), r.resumen().progresoGeneralPonderado());
        assertEquals(2, r.resumen().activas()); assertNull(r.campanas().getFirst().resultadoFinal());
    }
    @Test void futuraSinParticipacionesEsCeroSinResultadoFinal() {
        campanas(campana(10, hoy.plusDays(1), hoy.plusDays(5)));
        var r = consultar(); var c = r.campanas().getFirst();
        assertEquals("FUTURA", c.estado()); assertEquals("NO_DISPONIBLE", c.estadoColectivo());
        assertEquals(0, c.participantes()); assertEquals(new BigDecimal("0.00"), c.progresoColectivo());
        assertNull(c.resultadoFinal()); assertNull(c.fechaCierre()); assertEquals(1, r.resumen().futuras());
    }
    @Test void cierreConservaSnapshotAunqueElAvanceActualSeaDistinto() {
        campanas(campana(10, hoy.minusDays(5), hoy.minusDays(1)));
        LocalDateTime fecha = hoy.minusDays(1).atTime(23, 59);
        when(participaciones.resumirParaReporte(any())).thenReturn(List.of(new Avance(10, 2L, new BigDecimal("200"))));
        when(resultados.buscarParaReporte(any())).thenReturn(List.of(new Cierre(10, 2, new BigDecimal("75.00"), "NO_ALCANZADO", fecha)));
        when(participaciones.totalesParaReporte(anyLong(), any(), any(), any())).thenReturn(new Totales(2L, 2L));
        var r = consultar(); var c = r.campanas().getFirst();
        assertEquals(new BigDecimal("75.00"), c.progresoColectivo()); assertEquals(fecha, c.fechaCierre());
        assertEquals("NO_ALCANZADO", c.resultadoFinal()); assertEquals(1, r.resumen().finalizadas());
        assertEquals(1, r.resumen().conResultado()); assertEquals(1, r.resumen().noAlcanzadas());
    }
    @Test void exitosasProvienenDeResultadosRegistradosNoDeProgresoActual() {
        campanas(campana(10, hoy.minusDays(2), hoy.minusDays(1)));
        when(resultados.buscarParaReporte(any())).thenReturn(List.of(new Cierre(10, 1, new BigDecimal("100"), "EXITOSO", hoy.atStartOfDay())));
        var r = consultar(); assertEquals(1, r.resumen().exitosas()); assertEquals("EXITOSO", r.campanas().getFirst().resultadoFinal());
        assertFalse(r.advertencias().isEmpty()); // informa diferencia entre filas actuales y snapshot, no lo reescribe
    }
    @Test void vencidaSinCierrePermanecePendienteSinInventarResultado() {
        campanas(campana(10, hoy.minusDays(2), hoy.minusDays(1)));
        var c = consultar().campanas().getFirst();
        assertEquals("PENDIENTE_CIERRE", c.estadoColectivo()); assertNull(c.resultadoFinal()); assertNull(c.fechaCierre());
    }
    @Test void limitesDeFechasSonInclusivos() {
        campanas(campana(10, hoy, hoy));
        assertEquals("ACTIVA", consultar().campanas().getFirst().estado());
    }
    @Test void campanaInactivaNoSeCuentaActiva() {
        campanas(new Campana(10, "Deshabilitada", "Objetivo", hoy, hoy.plusDays(1), false));
        assertEquals(1, consultar().resumen().noDisponibles());
    }
    @Test void fechasNulasNoProvocan500NiSeInventan() {
        campanas(new Campana(10, null, null, null, null, null));
        var r = consultar(); assertEquals(1, r.resumen().datosIncompletos());
        assertNull(r.campanas().getFirst().fechaInicio()); assertFalse(r.advertencias().isEmpty());
    }
    @Test void progresoNuloEnCierreNoSeFabrica() {
        campanas(campana(10, hoy.minusDays(2), hoy.minusDays(1)));
        when(resultados.buscarParaReporte(any())).thenReturn(List.of(new Cierre(10, 1, null, "NO_ALCANZADO", null)));
        var r = consultar(); assertNull(r.campanas().getFirst().progresoColectivo());
        assertNull(r.resumen().progresoGeneralPonderado());
    }
    @Test void redondeoCoincideConHu29() {
        campanas(campana(10, hoy, hoy));
        when(participaciones.resumirParaReporte(any())).thenReturn(List.of(new Avance(10, 3L, new BigDecimal("100"))));
        assertEquals(new BigDecimal("33.33"), consultar().campanas().getFirst().progresoColectivo());
    }
    @Test void institucionInexistente404() { error(404, () -> servicio.consultar(999L, usuario, null, null, null)); verifyNoInteractions(afiliaciones, retos); }
    @Test void sinIdentidad401EnServicio() { error(401, () -> servicio.consultar(5L, null, null, null, null)); verifyNoInteractions(instituciones); }
    @Test void miembroOrdinario403SinConsultarCampanas() {
        when(afiliaciones.existsByUsuario_IdAndInstitucion_IdAndRol(any(), any(), any())).thenReturn(false);
        error(403, this::consultar); verifyNoInteractions(retos, participaciones, resultados);
    }
    @Test void administradorAjeno403NoUsaSuInstitucionComoSustituto() {
        usuario.setId(88L); error(403, this::consultar);
        verify(afiliaciones).existsByUsuario_IdAndInstitucion_IdAndRol(88L, 5L, RolInstitucional.ADMIN_INSTITUCIONAL);
        verifyNoInteractions(retos);
    }
    @Test void campanaAjenaONoExistente404SinRevelarDatos() {
        for (int id : List.of(999, 10)) error(404, () -> servicio.consultar(5L, usuario, null, null, id));
        verifyNoInteractions(participaciones, resultados);
    }
    @Test void filtroCampanaYPeriodoSePropaganATodasLasConsultas() {
        when(retos.existsByIdAndInstitucion_IdAndTipo(10, 5L, TipoReto.COLECTIVO)).thenReturn(true);
        campanas(campana(10, hoy.minusDays(1), hoy.plusDays(1)));
        var r = servicio.consultar(5L, usuario, hoy, hoy.plusDays(1), 10);
        assertEquals(10, r.filtros().campanaId()); assertEquals(hoy, r.filtros().desde());
        verify(retos).buscarParaReporte(eq(5L), eq(hoy), eq(hoy.plusDays(1)), eq(10), any());
        verify(participaciones).totalesParaReporte(5L, hoy, hoy.plusDays(1), 10);
    }
    @Test void periodoSinResultadosDevuelve200LogicoConListaVacia() {
        var r = servicio.consultar(5L, usuario, hoy.plusYears(5), hoy.plusYears(6), null);
        assertTrue(r.campanas().isEmpty()); assertEquals(0, r.resumen().totalParticipacionesRegistradas());
    }
    @Test void parametrosInvalidosSeRechazanAntesDeConsultar() {
        error(400, () -> servicio.consultar(-1L, usuario, null, null, null));
        error(400, () -> servicio.consultar(5L, usuario, null, null, 0));
        error(400, () -> servicio.consultar(5L, usuario, hoy, hoy.minusDays(1), null));
        error(400, () -> servicio.consultar(5L, usuario, LocalDate.of(0, 1, 1), null, null));
        error(400, () -> servicio.consultar(5L, usuario, null, LocalDate.of(10000, 1, 1), null));
        verifyNoInteractions(instituciones);
    }
    @Test void consultasNoEscribenNiBloqueanNiCambianPuntos() {
        campanas(campana(10, hoy, hoy)); consultar();
        assertEquals(30, usuario.getEcoPuntos()); assertEquals(1, usuario.getNivel());
        verify(instituciones).findById(5L);
        verify(afiliaciones).existsByUsuario_IdAndInstitucion_IdAndRol(77L, 5L, RolInstitucional.ADMIN_INSTITUCIONAL);
        verify(retos).buscarParaReporte(eq(5L), isNull(), isNull(), isNull(), any());
        verify(participaciones).resumirParaReporte(List.of(10));
        verify(participaciones).totalesParaReporte(5L, null, null, null);
        verify(resultados).buscarParaReporte(List.of(10));
        verifyNoMoreInteractions(instituciones, afiliaciones, retos, participaciones, resultados);
    }
    @Test void falloDeLecturaNoDevuelveReporteParcialNiEscribe() {
        campanas(campana(10, hoy, hoy));
        when(resultados.buscarParaReporte(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Fallo"));
        assertThrows(org.springframework.dao.DataAccessException.class, this::consultar);
        verify(participaciones, never()).totalesParaReporte(any(), any(), any(), any());
        verify(retos, never()).save(any()); verify(resultados, never()).save(any());
    }
    @Test void totalesIncompletosNoSeTransformanEnCeroFalso() {
        campanas(campana(10, hoy, hoy)); when(participaciones.totalesParaReporte(any(), any(), any(), any())).thenReturn(null);
        error(500, this::consultar);
    }
    @Test void transaccionDeclaraSnapshotConsistenteSoloLecturaSinBloqueos() throws Exception {
        var anotacion = ReporteInstitucionalServiceImpl.class.getMethod("consultar", Long.class, Usuario.class,
                LocalDate.class, LocalDate.class, Integer.class).getAnnotation(Transactional.class);
        assertTrue(anotacion.readOnly()); assertEquals(Isolation.REPEATABLE_READ, anotacion.isolation());
        var auth = AutorizacionInstitucionalService.class.getMethod("exigirAdministradorConsulta", Long.class, Long.class)
                .getAnnotation(Transactional.class);
        assertTrue(auth.readOnly());
    }
    @Test void cientosDeCampanasSeLeenEnLotesSinDuplicar() {
        var datos = IntStream.rangeClosed(1, 503).mapToObj(i -> (CampanaReporteVista) campana(i, hoy, hoy)).toList();
        when(retos.buscarParaReporte(eq(5L), any(), any(), any(), any())).thenAnswer(inv -> {
            Pageable p = inv.getArgument(4); int desde = (int) p.getOffset();
            return new PageImpl<>(datos.subList(desde, Math.min(desde + p.getPageSize(), datos.size())), p, datos.size());
        });
        var r = consultar(); assertEquals(503, r.resumen().totalCampanas());
        assertEquals(503, r.campanas().stream().map(ReporteInstitucionalDTO.Campana::id).distinct().count());
        verify(retos, times(3)).buscarParaReporte(eq(5L), any(), any(), any(), any());
        verify(participaciones, times(3)).resumirParaReporte(argThat(ids -> ids.size() <= 250));
    }
    @Test void solicitudesParalelasSonIndependientesYNoAcumulanDatos() throws Exception {
        campanas(campana(10, hoy, hoy));
        ExecutorService ejecutor = Executors.newFixedThreadPool(4);
        try {
            var tareas = IntStream.range(0, 20).mapToObj(i -> (Callable<ReporteInstitucionalDTO>) this::consultar).toList();
            for (var futuro : ejecutor.invokeAll(tareas)) assertEquals(1, futuro.get().campanas().size());
        } finally { ejecutor.shutdownNow(); }
        verify(retos, never()).save(any()); verify(participaciones, never()).save(any());
    }
    @Test void coleccionesDevueltasSonInmutables() {
        var r = consultar(); assertThrows(UnsupportedOperationException.class, () -> r.advertencias().clear());
        assertThrows(UnsupportedOperationException.class, () -> r.campanas().clear());
    }
}
