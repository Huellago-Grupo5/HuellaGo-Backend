package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Notificacion;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.NotificacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceImplTest {

    @Mock
    NotificacionRepository notificacionRepository;

    @InjectMocks
    NotificacionServiceImpl service;

    @Test
    void noCreaAlertasOpcionalesSiLaPreferenciaEstaDesactivada() {
        Usuario usuario = usuario(false);

        var resultado = service.crearOpcional(usuario, "Reto", "Nuevo reto", "RETO");

        assertTrue(resultado.isEmpty());
        verify(notificacionRepository, never()).save(any());
    }

    @Test
    void creaLaAlertaOpcionalSiLaPreferenciaEstaActiva() {
        Usuario usuario = usuario(true);
        when(notificacionRepository.save(any(Notificacion.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        var resultado = service.crearOpcional(usuario, "Reto", "Nuevo reto", "RETO");

        assertTrue(resultado.isPresent());
        assertEquals(usuario, resultado.orElseThrow().getUsuario());
        assertFalse(resultado.orElseThrow().getLeida());
        verify(notificacionRepository).save(any(Notificacion.class));
    }

    @Test void eventoRespetaPreferenciaYClaveIdempotente() {
        var usuario=usuario(false);
        assertTrue(service.crearPorEvento(usuario,"RETO_COMPLETADO:29","Reto","Completado","RETO_COMPLETADO").isEmpty());
        verify(notificacionRepository,never()).save(any());
        usuario.setNotificacionesActivas(true);
        when(notificacionRepository.save(any(Notificacion.class))).thenAnswer(i->i.getArgument(0));
        var creada=service.crearPorEvento(usuario,"RETO_COMPLETADO:29","Reto","Completado","RETO_COMPLETADO");
        assertEquals("RETO_COMPLETADO:29",creada.orElseThrow().getClaveEvento());
        when(notificacionRepository.existsByUsuario_IdAndClaveEvento(22L,"RETO_COMPLETADO:29")).thenReturn(true);
        assertTrue(service.crearPorEvento(usuario,"RETO_COMPLETADO:29","Reto","Completado","RETO_COMPLETADO").isEmpty());
        verify(notificacionRepository).save(any(Notificacion.class));
    }

    @Test void lecturaYMarcadoSoloPropiosConservanHistoricasAunquePreferenciaEsteApagada() {
        var usuario=usuario(false); var n=new Notificacion(); n.setId(5); n.setUsuario(usuario);
        n.setTitulo("Reto"); n.setLeida(false);
        when(notificacionRepository.findByUsuario_IdOrderByFechaCreacionDescIdDesc(22L)).thenReturn(List.of(n));
        when(notificacionRepository.contarNoLeidas(22L)).thenReturn(1L);
        when(notificacionRepository.findByIdAndUsuario_Id(5,22L)).thenReturn(java.util.Optional.of(n));
        when(notificacionRepository.findByIdAndUsuario_Id(5,23L)).thenReturn(java.util.Optional.empty());
        when(notificacionRepository.save(n)).thenReturn(n);
        assertEquals(1,service.listar(22L).size()); assertEquals(1,service.contarNoLeidas(22L));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.marcarLeida(23L,5));
        assertTrue(service.marcarLeida(22L,5).leida());
        assertEquals(0,service.marcarTodasLeidas(22L));
        verify(notificacionRepository,times(1)).save(n);
    }

    @Test void marcarTodasSoloActualizaLasNoLeidasDelUsuario() {
        var usuario = usuario(false);
        var sinLeer = new Notificacion(); sinLeer.setId(6); sinLeer.setUsuario(usuario); sinLeer.setLeida(false);
        var leida = new Notificacion(); leida.setId(7); leida.setUsuario(usuario); leida.setLeida(true);
        when(notificacionRepository.findByUsuario_IdOrderByFechaCreacionDescIdDesc(22L))
                .thenReturn(List.of(sinLeer, leida));
        assertEquals(1, service.marcarTodasLeidas(22L));
        assertTrue(sinLeer.getLeida());
        verify(notificacionRepository).save(sinLeer);
        verify(notificacionRepository, never()).save(leida);
    }

    private Usuario usuario(boolean activa) {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setNotificacionesActivas(activa);
        return usuario;
    }
}
