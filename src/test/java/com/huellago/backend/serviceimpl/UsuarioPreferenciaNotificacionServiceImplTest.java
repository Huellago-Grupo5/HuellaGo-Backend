package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.PreferenciaNotificacionActualizarDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioPreferenciaNotificacionServiceImplTest {

    @Mock
    UsuarioRepository usuarioRepository;

    @InjectMocks
    UsuarioServiceImpl service;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo("usuario22@huellago.com");
        usuario.setNotificacionesActivas(true);
        usuario.setFechaActualizacion(LocalDateTime.now().minusDays(1));
        when(usuarioRepository.findById(22L)).thenReturn(Optional.of(usuario));
    }

    @Test
    void consultaElValorPersistido() {
        var respuesta = service.obtenerPreferenciaNotificaciones(usuario);

        assertEquals(22L, respuesta.getUsuarioId());
        assertTrue(respuesta.getNotificacionesActivas());
    }

    @Test
    void desactivaYPersisteLaConfiguracion() {
        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        var respuesta = service.actualizarPreferenciaNotificaciones(
                usuario, new PreferenciaNotificacionActualizarDTO(false));

        assertFalse(respuesta.getNotificacionesActivas());
        assertFalse(usuario.getNotificacionesActivas());
        assertNotNull(usuario.getFechaActualizacion());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void permiteActivarlaDespuesDeEstarDesactivada() {
        usuario.setNotificacionesActivas(false);
        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        var respuesta = service.actualizarPreferenciaNotificaciones(
                usuario, new PreferenciaNotificacionActualizarDTO(true));

        assertTrue(respuesta.getNotificacionesActivas());
        assertTrue(usuario.getNotificacionesActivas());
        verify(usuarioRepository).save(usuario);
    }
}
