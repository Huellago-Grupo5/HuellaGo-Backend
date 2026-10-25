package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ActualizarProgresoDTO;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.ResultadoRetoColectivoRepository;
import com.huellago.backend.repositories.RetoRepository;
import com.huellago.backend.repositories.UsuarioRetoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetoColectivoServiceImplTest {
    @Mock org.springframework.context.ApplicationEventPublisher eventos;
    @Mock RetoRepository retos;
    @Mock UsuarioRetoRepository participaciones;
    @Mock ResultadoRetoColectivoRepository resultados;
    @Mock com.huellago.backend.security.AutorizacionInstitucionalService autorizacionInstitucional;
    @InjectMocks RetoColectivoServiceImpl servicio;

    @Test
    void campanaInstitucionalNoSeFiltraPorCatalogoGlobalNiDetalleSinIdentidad() {
        Reto campana = reto(7, LocalDate.now().plusDays(1));
        var institucion = new Institucion();
        org.springframework.test.util.ReflectionTestUtils.setField(institucion, "id", 5L);
        campana.setInstitucion(institucion);
        when(retos.findByTipo(TipoReto.COLECTIVO)).thenReturn(List.of(campana));
        when(retos.findById(7)).thenReturn(Optional.of(campana));
        assertTrue(servicio.listarDisponibles().isEmpty());
        assertTrue(servicio.listarDisponibles(33L).isEmpty());
        assertEquals(HttpStatus.FORBIDDEN, error(() -> servicio.obtener(7)));
        when(autorizacionInstitucional.esMiembro(22L, 5L)).thenReturn(true);
        assertEquals(1, servicio.listarDisponibles(22L).size());
    }

    @Test
    void campanaExigeAfiliacionEnDetalleUnionYProgresoInclusoPorRutasHu29() {
        Reto campana = reto(7, LocalDate.now().plusDays(1));
        var institucion = new Institucion();
        org.springframework.test.util.ReflectionTestUtils.setField(institucion, "id", 5L);
        campana.setInstitucion(institucion);
        when(retos.findById(7)).thenReturn(Optional.of(campana));
        when(retos.bloquearParaAceptacion(7)).thenReturn(Optional.of(7));
        when(participaciones.buscarRetoIdDeParticipacion(1, 33L)).thenReturn(Optional.of(7));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No miembro"))
                .when(autorizacionInstitucional).exigirMiembro(33L, 5L);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No miembro"))
                .when(autorizacionInstitucional).exigirMiembroParaParticipar(33L, 5L);
        assertEquals(HttpStatus.FORBIDDEN, error(() -> servicio.obtener(7, 33L)));
        assertEquals(HttpStatus.FORBIDDEN, error(() -> servicio.unirse(7, usuario(33L))));
        assertEquals(HttpStatus.FORBIDDEN, error(() -> servicio.actualizarProgreso(1, 33L,
                new ActualizarProgresoDTO(BigDecimal.valueOf(50)))));
        verify(participaciones, never()).saveAndFlush(any());
        verify(participaciones, never()).save(any());
    }

    @Test
    void miembroSeUneACampanaConElMismoMecanismoHu29SinRecompensas() {
        Reto campana = reto(7, LocalDate.now().plusDays(1));
        var institucion = new Institucion();
        org.springframework.test.util.ReflectionTestUtils.setField(institucion, "id", 5L);
        campana.setInstitucion(institucion);
        when(retos.findById(7)).thenReturn(Optional.of(campana));
        when(retos.bloquearParaAceptacion(7)).thenReturn(Optional.of(7));
        when(participaciones.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals("activo", servicio.unirse(7, usuario(22L)).getEstado());
        verify(autorizacionInstitucional).exigirMiembroParaParticipar(22L, 5L);
        verify(participaciones).saveAndFlush(argThat(p -> p.getProgreso().signum() == 0));
    }

    @Test
    void listaSoloColectivosDisponiblesSinParticipantesConProgresoCero() {
        Reto disponible = reto(7, LocalDate.now().plusDays(1));
        Reto vencido = reto(8, LocalDate.now().minusDays(1));
        when(retos.findByTipo(TipoReto.COLECTIVO)).thenReturn(List.of(disponible, vencido));
        when(participaciones.findByReto_Id(7)).thenReturn(List.of());

        var lista = servicio.listarDisponibles();

        assertEquals(1, lista.size());
        assertEquals(7, lista.getFirst().getRetoId());
        assertEquals(0, lista.getFirst().getParticipantes());
        assertEquals(0, lista.getFirst().getProgresoColectivo().compareTo(BigDecimal.ZERO));
        verify(participaciones, never()).save(any());
    }

    @Test
    void cierreDeCampanaInstitucionalConservaCalculoYEventoHu29Hu37SinExigirJwt() {
        Reto campana = reto(7, LocalDate.now().minusDays(1));
        var institucion = new Institucion();
        org.springframework.test.util.ReflectionTestUtils.setField(institucion, "id", 5L);
        campana.setInstitucion(institucion);
        when(retos.bloquearParaAceptacion(7)).thenReturn(Optional.of(7));
        when(retos.findById(7)).thenReturn(Optional.of(campana));
        when(participaciones.findByReto_Id(7)).thenReturn(List.of(
                participacion(1, 22L, campana, 100), participacion(2, 33L, campana, 50)));
        servicio.cerrarVencido(7);
        verify(resultados).saveAndFlush(argThat(r -> r.getParticipantes() == 2
                && "NO_ALCANZADO".equals(r.getEstado())
                && r.getProgresoFinal().compareTo(new BigDecimal("75.00")) == 0));
        verify(eventos).publishEvent(new com.huellago.backend.events.InformacionAmbientalActualizada(List.of(22L, 33L)));
        verifyNoInteractions(autorizacionInstitucional);
    }

    @Test
    void dosUsuariosPuedenUnirseSinRecompensas() {
        Reto reto = reto(7, LocalDate.now().plusDays(1));
        when(retos.bloquearParaAceptacion(7)).thenReturn(Optional.of(7));
        when(retos.findById(7)).thenReturn(Optional.of(reto));
        when(participaciones.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(22L, servicio.unirse(7, usuario(22L)).getUsuarioId());
        assertEquals(33L, servicio.unirse(7, usuario(33L)).getUsuarioId());
        verify(participaciones).existsByUsuario_IdAndReto_Id(22L, 7);
        verify(participaciones).existsByUsuario_IdAndReto_Id(33L, 7);
        verify(participaciones, times(2)).saveAndFlush(any());
    }

    @Test
    void rechazaDuplicadoInexistenteIndividualYVencido() {
        Reto colectivo = reto(7, LocalDate.now().plusDays(1));
        when(retos.bloquearParaAceptacion(7)).thenReturn(Optional.of(7));
        when(retos.findById(7)).thenReturn(Optional.of(colectivo));
        when(participaciones.existsByUsuario_IdAndReto_Id(22L, 7)).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, error(() -> servicio.unirse(7, usuario(22L))));

        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.unirse(999, usuario(22L))));
        colectivo.setTipo(TipoReto.INDIVIDUAL);
        assertEquals(HttpStatus.CONFLICT, error(() -> servicio.unirse(7, usuario(22L))));
        colectivo.setTipo(TipoReto.COLECTIVO);
        colectivo.setFechaFin(LocalDate.now().minusDays(1));
        assertEquals(HttpStatus.CONFLICT, error(() -> servicio.unirse(7, usuario(22L))));
        verify(participaciones, never()).saveAndFlush(any());
    }

    @Test
    void promedioReflejaValoresActualesNoSumaAvancesPrevios() {
        Reto reto = reto(7, LocalDate.now().plusDays(1));
        when(retos.findById(7)).thenReturn(Optional.of(reto));
        UsuarioReto a = participacion(1, 22L, reto, 100);
        UsuarioReto b = participacion(2, 33L, reto, 50);
        UsuarioReto c = participacion(3, 44L, reto, 75);
        when(participaciones.findByReto_Id(7)).thenReturn(List.of(a, b, c));

        assertEquals(0, servicio.obtener(7).getProgresoColectivo()
                .compareTo(new BigDecimal("75.00")));
        b.setProgreso(BigDecimal.valueOf(100));
        assertEquals(0, servicio.obtener(7).getProgresoColectivo()
                .compareTo(new BigDecimal("91.67")));
    }

    @Test
    void actualizaSoloParticipacionPropiaYRechazaPeriodoCerrado() {
        Reto reto = reto(7, LocalDate.now().plusDays(1));
        UsuarioReto participacion = participacion(1, 22L, reto, 0);
        when(participaciones.buscarRetoIdDeParticipacion(1, 22L)).thenReturn(Optional.of(7));
        when(retos.bloquearParaAceptacion(7)).thenReturn(Optional.of(7));
        when(retos.findById(7)).thenReturn(Optional.of(reto));
        when(participaciones.bloquearParticipacion(1, 22L)).thenReturn(Optional.of(1));
        when(participaciones.findByIdAndUsuario_Id(1, 22L)).thenReturn(Optional.of(participacion));
        when(participaciones.save(participacion)).thenReturn(participacion);

        assertEquals(50, servicio.actualizarProgreso(1, 22L,
                new ActualizarProgresoDTO(BigDecimal.valueOf(50))).getProgreso().intValue());
        when(participaciones.findByReto_Id(7)).thenReturn(List.of(participacion));
        assertEquals(0, servicio.obtener(7).getProgresoColectivo()
                .compareTo(new BigDecimal("50.00")));
        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.actualizarProgreso(1, 33L,
                new ActualizarProgresoDTO(BigDecimal.valueOf(80)))));
        reto.setFechaFin(LocalDate.now().minusDays(1));
        assertEquals(HttpStatus.CONFLICT, error(() -> servicio.actualizarProgreso(1, 22L,
                new ActualizarProgresoDTO(BigDecimal.valueOf(80)))));
        assertEquals(50, participacion.getProgreso().intValue());
        verify(participaciones, times(1)).save(any());
    }

    @Test
    void cierreExitosoPersisteUnaVezYConservaResultado() {
        Reto reto = reto(7, LocalDate.now().minusDays(1));
        when(retos.bloquearParaAceptacion(7)).thenReturn(Optional.of(7));
        when(retos.findById(7)).thenReturn(Optional.of(reto));
        when(participaciones.findByReto_Id(7)).thenReturn(List.of(
                participacion(1, 22L, reto, 100), participacion(2, 33L, reto, 100)));
        when(resultados.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        servicio.cerrarVencido(7);
        ArgumentCaptor<ResultadoRetoColectivo> captor =
                ArgumentCaptor.forClass(ResultadoRetoColectivo.class);
        verify(resultados).saveAndFlush(captor.capture());
        var cerrado = captor.getValue();
        assertEquals("EXITOSO", cerrado.getEstado());
        assertEquals(2, cerrado.getParticipantes());
        assertEquals(0, cerrado.getProgresoFinal().compareTo(BigDecimal.valueOf(100)));
        assertNotNull(cerrado.getFechaCierre());
        verify(eventos).publishEvent(new com.huellago.backend.events.InformacionAmbientalActualizada(List.of(22L, 33L)));

        when(resultados.existsByReto_Id(7)).thenReturn(true);
        when(resultados.findByReto_Id(7)).thenReturn(Optional.of(cerrado));
        servicio.cerrarVencido(7);
        assertEquals("EXITOSO", servicio.obtener(7).getEstado());
        verify(resultados, times(1)).saveAndFlush(any());
    }

    @Test
    void cierreSinObjetivoIncluyeCeroParticipantes() {
        Reto reto = reto(7, LocalDate.now().minusDays(1));
        when(retos.bloquearParaAceptacion(7)).thenReturn(Optional.of(7));
        when(retos.findById(7)).thenReturn(Optional.of(reto));
        when(participaciones.findByReto_Id(7)).thenReturn(List.of());

        servicio.cerrarVencido(7);

        verify(resultados).saveAndFlush(argThat(r -> r.getParticipantes() == 0
                && r.getProgresoFinal().compareTo(BigDecimal.ZERO) == 0
                && "NO_ALCANZADO".equals(r.getEstado())));
    }

    @Test
    void cierreNoExitosoConParticipantesConservaPromedio() {
        Reto reto = reto(7, LocalDate.now().minusDays(1));
        when(retos.bloquearParaAceptacion(7)).thenReturn(Optional.of(7));
        when(retos.findById(7)).thenReturn(Optional.of(reto));
        when(participaciones.findByReto_Id(7)).thenReturn(List.of(
                participacion(1, 22L, reto, 100), participacion(2, 33L, reto, 50)));

        servicio.cerrarVencido(7);

        verify(resultados).saveAndFlush(argThat(r -> r.getParticipantes() == 2
                && r.getProgresoFinal().compareTo(new BigDecimal("75.00")) == 0
                && "NO_ALCANZADO".equals(r.getEstado())));
    }

    @Test
    void resultadoPersistidoBloqueaNuevasUnionesYAvances() {
        Reto reto = reto(7, LocalDate.now().plusDays(1));
        when(retos.bloquearParaAceptacion(7)).thenReturn(Optional.of(7));
        when(retos.findById(7)).thenReturn(Optional.of(reto));
        when(resultados.existsByReto_Id(7)).thenReturn(true);
        when(participaciones.buscarRetoIdDeParticipacion(1, 22L)).thenReturn(Optional.of(7));

        assertEquals(HttpStatus.CONFLICT, error(() -> servicio.unirse(7, usuario(22L))));
        assertEquals(HttpStatus.CONFLICT, error(() -> servicio.actualizarProgreso(1, 22L,
                new ActualizarProgresoDTO(BigDecimal.valueOf(50)))));
        verify(participaciones, never()).save(any());
        verify(participaciones, never()).saveAndFlush(any());
    }

    @Test
    void bloqueoYTransaccionProtegenCierreYAvance() throws Exception {
        assertNotNull(RetoColectivoServiceImpl.class.getMethod("cerrarVencido", Integer.class)
                .getAnnotation(Transactional.class));
        assertNotNull(RetoColectivoServiceImpl.class.getMethod("actualizarProgreso", Integer.class,
                Long.class, ActualizarProgresoDTO.class).getAnnotation(Transactional.class));
        assertTrue(RetoRepository.class.getMethod("bloquearParaAceptacion", Integer.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class)
                .value().contains("FOR UPDATE"));
    }

    private HttpStatus error(org.junit.jupiter.api.function.Executable llamada) {
        return (HttpStatus) assertThrows(ResponseStatusException.class, llamada).getStatusCode();
    }

    private Reto reto(int id, LocalDate fin) {
        Reto reto = new Reto();
        reto.setId(id);
        reto.setTipo(TipoReto.COLECTIVO);
        reto.setTitulo("Reto colectivo");
        reto.setActivo(true);
        reto.setFechaInicio(LocalDate.now().minusDays(2));
        reto.setFechaFin(fin);
        return reto;
    }

    private Usuario usuario(long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setEcoPuntos(30);
        usuario.setNivel(1);
        return usuario;
    }

    private UsuarioReto participacion(int id, long usuarioId, Reto reto, int progreso) {
        UsuarioReto participacion = new UsuarioReto();
        participacion.setId(id);
        participacion.setUsuario(usuario(usuarioId));
        participacion.setReto(reto);
        participacion.setEstado("activo");
        participacion.setProgreso(BigDecimal.valueOf(progreso));
        return participacion;
    }
}
