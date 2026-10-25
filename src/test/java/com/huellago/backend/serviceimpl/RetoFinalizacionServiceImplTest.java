package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ActualizarProgresoDTO;
import com.huellago.backend.dtos.InsigniaRespuestaDTO;
import com.huellago.backend.entities.Reto;
import com.huellago.backend.entities.TipoReto;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.entities.UsuarioReto;
import com.huellago.backend.repositories.RetoRepository;
import com.huellago.backend.repositories.UsuarioRetoRepository;
import com.huellago.backend.repositories.UsuarioRepository;
import com.huellago.backend.services.InsigniaService;
import com.huellago.backend.services.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetoFinalizacionServiceImplTest {
    @Mock org.springframework.context.ApplicationEventPublisher eventos;
    @Mock RetoRepository retos;
    @Mock UsuarioRetoRepository participaciones;
    @Mock UsuarioRepository usuariosRepository;
    @Mock UsuarioService usuarios;
    @Mock InsigniaService insignias;
    @InjectMocks RetoServiceImpl servicio;

    @Test
    void completaParticipacionPropiaConCienSinPerderFechaDeAceptacion() {
        UsuarioReto participacion = participacion();
        participacion.setProgreso(BigDecimal.valueOf(100));
        LocalDateTime aceptacion = participacion.getFechaAceptacion();
        prepararPropietario(participacion);
        when(usuarios.actualizarNivel(participacion.getUsuario()))
                .thenReturn(participacion.getUsuario());
        when(insignias.evaluarYDesbloquearInsignias(participacion.getUsuario()))
                .thenReturn(List.of(new InsigniaRespuestaDTO(1, "Primer paso verde",
                        "Insignia por puntos", "/img/primer-paso-verde.png", 10,
                        LocalDateTime.now(), true)));
        when(participaciones.save(participacion)).thenReturn(participacion);

        var respuesta = servicio.completarReto(29, 22L);

        assertEquals(29, respuesta.getId());
        assertEquals(22L, respuesta.getUsuarioId());
        assertEquals(18, respuesta.getRetoId());
        assertEquals("completado", respuesta.getEstado());
        assertEquals(0, respuesta.getProgreso().compareTo(BigDecimal.valueOf(100)));
        assertNotNull(respuesta.getFechaCompletado());
        assertEquals(aceptacion, respuesta.getFechaAceptacion());
        assertEquals(aceptacion, participacion.getFechaAceptacion());
        assertEquals(110, participacion.getUsuario().getEcoPuntos());
        assertEquals(1, respuesta.getInsigniasDesbloqueadas().size());
        assertEquals("Primer paso verde", respuesta.getInsigniasDesbloqueadas().getFirst().getNombre());
        verify(participaciones).save(participacion);
        verify(usuarios).actualizarNivel(participacion.getUsuario());
        verify(insignias).evaluarYDesbloquearInsignias(participacion.getUsuario());
        verify(eventos).publishEvent(new com.huellago.backend.events.InformacionAmbientalActualizada(22L));
    }

    @Test
    void progresoInsuficienteNoCompletaNiConcedeRecompensas() {
        UsuarioReto participacion = participacion();
        prepararPropietario(participacion);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> servicio.completarReto(29, 22L)).getStatusCode());
        assertEquals("activo", participacion.getEstado());
        assertNull(participacion.getFechaCompletado());
        assertEquals(10, participacion.getUsuario().getEcoPuntos());
        verify(participaciones, never()).save(any());
        verifyNoInteractions(usuarios, insignias);
    }

    @Test
    void retoColectivoNoPuedeUsarFinalizacionIndividualNiRecibirPuntos() {
        UsuarioReto participacion = participacion();
        participacion.getReto().setTipo(TipoReto.COLECTIVO);
        participacion.setProgreso(BigDecimal.valueOf(100));
        prepararPropietario(participacion);

        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> servicio.completarReto(29, 22L)).getStatusCode());
        assertEquals(10, participacion.getUsuario().getEcoPuntos());
        assertEquals("activo", participacion.getEstado());
        verifyNoInteractions(usuarios, insignias);
        verify(participaciones, never()).save(any());
    }

    @Test
    void completadoOCanceladoNoSeFinalizaDosVeces() {
        UsuarioReto completado = participacion();
        completado.setEstado("completado");
        completado.setProgreso(BigDecimal.valueOf(100));
        completado.setFechaCompletado(LocalDateTime.now().minusDays(1));
        prepararPropietario(completado);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> servicio.completarReto(29, 22L)).getStatusCode());
        completado.setEstado("cancelado");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> servicio.completarReto(29, 22L)).getStatusCode());
        verify(participaciones, never()).save(any());
        verifyNoInteractions(usuarios, insignias);
    }

    @Test
    void participacionInexistenteOAjenaDevuelve404() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> servicio.completarReto(29, 22L)).getStatusCode());
        verify(participaciones, never()).findByIdAndUsuario_Id(any(), any());
        verifyNoInteractions(usuarios, insignias);
    }

    @Test
    void actualizarProgresoA100NoOtorgaRecompensas() {
        UsuarioReto participacion = participacion();
        when(participaciones.bloquearParticipacion(29, 22L)).thenReturn(Optional.of(29));
        when(participaciones.findByIdAndUsuario_Id(29, 22L))
                .thenReturn(Optional.of(participacion));
        when(participaciones.save(participacion)).thenReturn(participacion);
        var respuesta = servicio.actualizarProgreso(29, 22L,
                new ActualizarProgresoDTO(BigDecimal.valueOf(100)));
        assertEquals(0, respuesta.getProgreso().compareTo(BigDecimal.valueOf(100)));
        assertEquals("activo", respuesta.getEstado());
        assertNull(respuesta.getFechaCompletado());
        assertEquals(10, participacion.getUsuario().getEcoPuntos());
        verifyNoInteractions(usuarios, insignias);
    }

    @Test
    void sumaRecompensasDeRetosDistintosAlSaldoAcumulado() {
        UsuarioReto primera = participacion();
        primera.setProgreso(BigDecimal.valueOf(100));
        primera.getReto().setPuntosRecompensa(20);
        prepararPropietario(primera);
        when(usuarios.actualizarNivel(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(participaciones.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.completarReto(29, 22L);
        assertEquals(30, primera.getUsuario().getEcoPuntos());

        UsuarioReto segunda = participacion();
        segunda.setId(30);
        segunda.setProgreso(BigDecimal.valueOf(100));
        segunda.getUsuario().setEcoPuntos(30);
        segunda.getReto().setPuntosRecompensa(15);
        when(participaciones.bloquearParticipacion(30, 22L)).thenReturn(Optional.of(30));
        when(participaciones.findByIdAndUsuario_Id(30, 22L)).thenReturn(Optional.of(segunda));

        servicio.completarReto(30, 22L);
        assertEquals(45, segunda.getUsuario().getEcoPuntos());
        verify(usuariosRepository, times(2)).bloquearParaRecomendaciones(22L);
        verify(usuarios, times(2)).actualizarNivel(any());
    }

    @Test
    void falloAlPersistirRecompensaInterrumpeLaFinalizacionTransaccional() throws Exception {
        UsuarioReto participacion = participacion();
        participacion.setProgreso(BigDecimal.valueOf(100));
        prepararPropietario(participacion);
        when(usuarios.actualizarNivel(any())).thenThrow(new IllegalStateException("fallo de persistencia"));

        assertThrows(IllegalStateException.class, () -> servicio.completarReto(29, 22L));
        verify(participaciones, never()).save(any());
        verifyNoInteractions(insignias);
        assertNotNull(RetoServiceImpl.class.getMethod("completarReto", Integer.class, Long.class)
                .getAnnotation(Transactional.class));
    }

    @Test
    void progresoInvalidoNoPersiste() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> servicio.actualizarProgreso(29, 22L,
                        new ActualizarProgresoDTO(BigDecimal.valueOf(101)))).getStatusCode());
        verify(participaciones, never()).save(any());
    }

    @Test
    void bloqueoDeParticipacionProtegeFinalizacionYProgreso() throws Exception {
        Query query = UsuarioRetoRepository.class
                .getMethod("bloquearParticipacion", Integer.class, Long.class)
                .getAnnotation(Query.class);
        assertTrue(query.nativeQuery());
        assertTrue(query.value().contains("usuario_id = :usuarioId FOR UPDATE"));
        Query bloqueoUsuario = UsuarioRepository.class
                .getMethod("bloquearParaRecomendaciones", Long.class).getAnnotation(Query.class);
        assertTrue(bloqueoUsuario.nativeQuery());
        assertTrue(bloqueoUsuario.value().contains("FOR UPDATE"));
        assertNotNull(RetoServiceImpl.class.getMethod("completarReto", Integer.class, Long.class)
                .getAnnotation(Transactional.class));
        assertNotNull(RetoServiceImpl.class.getMethod("actualizarProgreso", Integer.class,
                Long.class, ActualizarProgresoDTO.class).getAnnotation(Transactional.class));
    }

    private void prepararPropietario(UsuarioReto participacion) {
        when(participaciones.bloquearParticipacion(29, 22L)).thenReturn(Optional.of(29));
        when(usuariosRepository.bloquearParaRecomendaciones(22L)).thenReturn(Optional.of(22L));
        when(participaciones.findByIdAndUsuario_Id(29, 22L))
                .thenReturn(Optional.of(participacion));
    }

    private UsuarioReto participacion() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setEcoPuntos(10);
        Reto reto = new Reto();
        reto.setId(18);
        reto.setPuntosRecompensa(100);
        UsuarioReto participacion = new UsuarioReto();
        participacion.setId(29);
        participacion.setUsuario(usuario);
        participacion.setReto(reto);
        participacion.setEstado("activo");
        participacion.setProgreso(BigDecimal.ZERO);
        participacion.setFechaAceptacion(LocalDateTime.of(2026, 10, 20, 10, 0));
        return participacion;
    }
}
