package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Notificacion;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.NotificacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    private Usuario usuario(boolean activa) {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setNotificacionesActivas(activa);
        return usuario;
    }
}
