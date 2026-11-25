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
    HuellaCarbonoRepository huellas;
    ReporteInstitucionalServiceImpl servicio;
    Usuario usuario;
    LocalDate hoy = LocalDate.now();

    record Campana(Integer getId, String getTitulo, String getDescripcion, LocalDate getFechaInicio,
            LocalDate getFechaFin, Boolean getActivo) implements CampanaReporteVista { }
    record Avance(Integer getRetoId, Long getParticipantes, BigDecimal getSumaProgreso) implements ParticipacionReporteVista { }
    record Cierre(Integer getRetoId, Integer getParticipantes, BigDecimal getProgresoFinal,
            String getEstado, LocalDateTime getFechaCierre) implements ResultadoReporteVista { }
    record Totales(Long getTotal, Long getUsuarios) implements TotalesParticipacionVista { }
    record HuellaFila(Long getUsuarioId, BigDecimal getCo2Total,
            BigDecimal getCo2Transporte, BigDecimal getCo2Energia,
            BigDecimal getCo2Alimentacion, BigDecimal getCo2Residuos,
            LocalDateTime getFechaCalculo, String getMetodologiaVersion,
            String getPeriodoCalculo, String getUnidadCalculo,
            String getPerimetroCalculo, String getFactoresAplicados)
            implements HuellaCarbonoRepository.FilaInstitucional {
        HuellaFila(Long usuario, BigDecimal total, BigDecimal transporte, BigDecimal energia,
                BigDecimal alimentacion, BigDecimal residuos, LocalDateTime fecha) {
            this(usuario, total, transporte, energia, alimentacion, residuos, fecha,
                    null, null, null, null, null);
        }
    }

    HuellaFila huella(long usuarioId, String total, LocalDateTime fecha) {
        return new HuellaFila(usuarioId, new BigDecimal(total), new BigDecimal("5"),
                new BigDecimal("10"), new BigDecimal("3"), new BigDecimal("2"), fecha);
    }
    HuellaFila versionada(long usuarioId, String total, LocalDateTime fecha, String factores) {
        return new HuellaFila(usuarioId, new BigDecimal(total), new BigDecimal("5"),
                new BigDecimal("10"), new BigDecimal("3"), new BigDecimal("2"), fecha,
                "HUELLAGO_SEMANAL_V1", "semanal", "kg CO2e", "PERSONAL_Y_HOGAR", factores);
    }

    @BeforeEach void preparar() {
        instituciones = mock(InstitucionRepository.class);
        afiliaciones = mock(UsuarioInstitucionRepository.class);
        retos = mock(RetoRepository.class);
        participaciones = mock(UsuarioRetoRepository.class);
        resultados = mock(ResultadoRetoColectivoRepository.class);
        huellas = mock(HuellaCarbonoRepository.class);
        servicio = new ReporteInstitucionalServiceImpl(instituciones, new AutorizacionInstitucionalService(afiliaciones),
                retos, participaciones, resultados, afiliaciones, huellas);
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
    @Test void promedioUsaUltimaHuellaCompletaPorMiembroSinExponerDatosIndividuales() {
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 24, 10, 0);
        when(afiliaciones.countByInstitucion_Id(5L)).thenReturn(4L);
        when(huellas.historialInstitucional(5L)).thenReturn(List.of(
                huella(22, "20.00", ahora), huella(22, "30.00", ahora.minusDays(7)),
                huella(23, "40.00", ahora), huella(24, "30.00", ahora),
                new HuellaFila(25L, null, null, null, null, null, ahora)));

        var resultado = consultar();

        assertEquals(4, resultado.huellaPromedio().miembrosInstitucion());
        assertEquals(4, resultado.huellaPromedio().miembrosConCalculo());
        assertEquals(3, resultado.huellaPromedio().miembrosConsiderados());
        assertEquals(new BigDecimal("30.00"), resultado.huellaPromedio().promedioKgCo2eSemanal());
        assertEquals("semanal", resultado.huellaPromedio().periodo());
        assertEquals("ORIENTATIVO_NO_CERTIFICADO", resultado.huellaPromedio().estado());
        assertEquals(1, resultado.seguimientoImpacto().miembrosConDosCalculosCompletos());
        assertNull(resultado.seguimientoImpacto().variacionAbsolutaKgCo2eSemanal());
        assertEquals("METODOLOGIA_NO_VERIFICABLE", resultado.seguimientoImpacto().estado());
    }
    @Test void noPublicaPromedioQueIdentifiqueUnaHuellaIndividual() {
        when(afiliaciones.countByInstitucion_Id(5L)).thenReturn(1L);
        when(huellas.historialInstitucional(5L)).thenReturn(List.of(
                huella(22, "20.00", LocalDateTime.of(2026, 11, 24, 10, 0))));
        var reporte = consultar();
        assertEquals(1, reporte.huellaPromedio().miembrosConsiderados());
        assertNull(reporte.huellaPromedio().promedioKgCo2eSemanal());
        assertEquals("COHORTE_INSUFICIENTE", reporte.huellaPromedio().estado());
    }
    @Test void consultaHuellasLimitadaALaInstitucion() throws Exception {
        String consulta = HuellaCarbonoRepository.class.getMethod("historialInstitucional", Long.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
        assertTrue(consulta.contains("ui.institucion.id = :institucionId"));
        assertTrue(consulta.contains("ui.usuario.id = h.usuario.id"));
        assertFalse(consulta.contains("correo"));
    }
    @Test void variacionComparaLosMismosTresMiembrosConMetodoYFactoresIguales() {
        LocalDateTime primero = LocalDateTime.of(2026, 11, 1, 10, 0);
        LocalDateTime ultimo = primero.plusDays(14);
        when(huellas.historialInstitucional(5L)).thenReturn(List.of(
                versionada(22, "20", ultimo, "factores-v1"), versionada(22, "30", primero, "factores-v1"),
                versionada(23, "25", ultimo, "factores-v1"), versionada(23, "20", primero, "factores-v1"),
                versionada(24, "5", ultimo, "factores-v1"), versionada(24, "10", primero, "factores-v1")));
        var resultado = consultar().seguimientoImpacto();
        assertEquals(3, resultado.miembrosIncluidos());
        assertEquals(new BigDecimal("60"), resultado.huellaInicialKgCo2eSemanal());
        assertEquals(new BigDecimal("50"), resultado.huellaFinalKgCo2eSemanal());
        assertEquals(new BigDecimal("-10"), resultado.variacionAbsolutaKgCo2eSemanal());
        assertEquals(new BigDecimal("-16.67"), resultado.variacionPorcentual());
        assertEquals("REDUCCION_ORIENTATIVA", resultado.estado());
    }

    @Test void noMezclaFactoresNiMuestraUnParIndividual() {
        LocalDateTime antes = LocalDateTime.of(2026, 11, 1, 10, 0);
        LocalDateTime despues = antes.plusDays(7);
        when(huellas.historialInstitucional(5L)).thenReturn(List.of(
                versionada(22, "10", despues, "factores-v2"),
                versionada(22, "20", antes, "factores-v1"),
                versionada(23, "10", despues, "factores-v1"),
                versionada(23, "20", antes, "factores-v1")));
        var resultado = consultar().seguimientoImpacto();
        assertNull(resultado.variacionAbsolutaKgCo2eSemanal());
        assertEquals(0, resultado.miembrosIncluidos());
        assertEquals("COHORTE_INSUFICIENTE", resultado.estado());
    }

    @Test void baseInicialCeroNoInventaPorcentajeYAumentoConservaSigno() {
        LocalDateTime antes = LocalDateTime.of(2026, 11, 1, 10, 0);
        LocalDateTime despues = antes.plusDays(7);
        var filas = new ArrayList<HuellaCarbonoRepository.FilaInstitucional>();
        for (long id : List.of(22L, 23L, 24L)) {
            filas.add(versionada(id, "5", despues, "factores-v1"));
            filas.add(versionada(id, "0", antes, "factores-v1"));
        }
        when(huellas.historialInstitucional(5L)).thenReturn(filas);
        var resultado = consultar().seguimientoImpacto();
        assertEquals(new BigDecimal("15"), resultado.variacionAbsolutaKgCo2eSemanal());
        assertNull(resultado.variacionPorcentual());
        assertEquals("BASE_INICIAL_CERO", resultado.estado());
    }
    @Test void intervaloEsInclusivoYNoUsaCalculosFueraDeEl() {
        LocalDateTime fuera = LocalDateTime.of(2026, 10, 31, 23, 59);
        LocalDateTime inicial = LocalDateTime.of(2026, 11, 1, 10, 0);
        LocalDateTime finalizada = LocalDateTime.of(2026, 11, 15, 10, 0);
        var filas = new ArrayList<HuellaCarbonoRepository.FilaInstitucional>();
        for (long id : List.of(22L, 23L, 24L)) {
            filas.add(versionada(id, "10", finalizada, "factores-v1"));
            filas.add(versionada(id, "20", inicial, "factores-v1"));
            filas.add(versionada(id, "100", fuera, "factores-v1"));
        }
        when(huellas.historialInstitucional(5L)).thenReturn(filas);
        var seguimiento = servicio.consultar(5L, usuario,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15), null).seguimientoImpacto();
        assertEquals(new BigDecimal("60"), seguimiento.huellaInicialKgCo2eSemanal());
        assertEquals(new BigDecimal("30"), seguimiento.huellaFinalKgCo2eSemanal());
    }
    @Test void aumentoConBasePositivaConservaSignoYPorcentaje() {
        LocalDateTime inicio = LocalDateTime.of(2026, 11, 1, 10, 0);
        var filas = new ArrayList<HuellaCarbonoRepository.FilaInstitucional>();
        for (long id : List.of(22L, 23L, 24L)) {
            filas.add(versionada(id, "15", inicio.plusDays(7), "factores-v1"));
            filas.add(versionada(id, "10", inicio, "factores-v1"));
        }
        when(huellas.historialInstitucional(5L)).thenReturn(filas);
        var seguimiento = consultar().seguimientoImpacto();
        assertEquals(new BigDecimal("15"), seguimiento.variacionAbsolutaKgCo2eSemanal());
        assertEquals(new BigDecimal("50.00"), seguimiento.variacionPorcentual());
        assertEquals("AUMENTO_ORIENTATIVO", seguimiento.estado());
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
    @Test void cierreExitosoNoDisponibleSeCuentaYExportaComoFinalizado() {
        var original = campana(48, hoy.minusDays(5), hoy.minusDays(1));
        campanas(new Campana(original.getId(), original.getTitulo(), original.getDescripcion(),
                original.getFechaInicio(), original.getFechaFin(), false));
        var fechaCierre = hoy.minusDays(1).atTime(12, 0);
        when(resultados.buscarParaReporte(any())).thenReturn(List.of(
                new Cierre(48, 3, new BigDecimal("100.00"), "EXITOSO", fechaCierre)));
        var reporte = consultar();
        assertEquals("FINALIZADA", reporte.campanas().getFirst().estado());
        assertEquals("EXITOSO", reporte.campanas().getFirst().estadoColectivo());
        assertEquals(fechaCierre, reporte.campanas().getFirst().fechaCierre());
        assertEquals(1, reporte.resumen().finalizadas());
        assertEquals(0, reporte.resumen().noDisponibles());
        assertEquals(1, reporte.resumen().exitosas());
        String csv = new String(new ReporteInstitucionalCsv().generar(reporte), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"FINALIZADA\",\"EXITOSO\""));
        assertTrue(csv.contains("\"1\",\"0\","));
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
        verify(afiliaciones).countByInstitucion_Id(5L);
        verify(huellas).historialInstitucional(5L);
        verify(retos).buscarParaReporte(eq(5L), isNull(), isNull(), isNull(), any());
        verify(participaciones).resumirParaReporte(List.of(10));
        verify(participaciones).totalesParaReporte(5L, null, null, null);
        verify(resultados).buscarParaReporte(List.of(10));
        verifyNoMoreInteractions(instituciones, afiliaciones, retos, participaciones, resultados, huellas);
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
