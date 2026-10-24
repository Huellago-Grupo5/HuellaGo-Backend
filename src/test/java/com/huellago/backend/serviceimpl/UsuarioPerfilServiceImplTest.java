package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.UsuarioPerfilDTO;
import com.huellago.backend.entities.Sesion;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.repositories.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioPerfilServiceImplTest {

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    SesionRepository sesionRepository;

    @InjectMocks
    UsuarioServiceImpl service;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(22L);
        usuario.setNombres("Carlos");
        usuario.setApellidos("Quispe");
        usuario.setCorreo("anterior@huellago.com");
        usuario.setContrasena("hash");
        usuario.setEcoPuntos(80);
        usuario.setNivel(2);
        usuario.setNotificacionesActivas(true);
        usuario.setVisibilidadComunidad(false);
        usuario.setFechaCreacion(LocalDateTime.now().minusMonths(1));
        when(usuarioRepository.findByCorreo("anterior@huellago.com")).thenReturn(usuario);
    }

    @Test
    void actualizaPerfilPersisteYProtegeCamposInternos() {
        Sesion sesion = new Sesion();
        sesion.setActiva(true);
        when(usuarioRepository.existsByCorreoIgnoreCaseAndIdNot("nuevo@huellago.com", 22L))
                .thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(sesionRepository.findByUsuario_IdAndActivaTrue(22L)).thenReturn(List.of(sesion));

        var resultado = service.actualizarPerfil("anterior@huellago.com",
                new UsuarioPerfilDTO("  Carlos Alberto  ", " Quispe Soto ",
                        " NUEVO@HUELLAGO.COM "));

        assertEquals("Carlos Alberto", resultado.getNombres());
        assertEquals("Quispe Soto", resultado.getApellidos());
        assertEquals("nuevo@huellago.com", resultado.getCorreo());
        assertEquals(80, usuario.getEcoPuntos());
        assertEquals(2, usuario.getNivel());
        assertEquals("hash", usuario.getContrasena());
        assertFalse(sesion.getActiva());
        verify(usuarioRepository).save(usuario);
        verify(sesionRepository).saveAll(List.of(sesion));
    }

    @Test
    void rechazaCorreoDuplicadoSinPersistir() {
        when(usuarioRepository.existsByCorreoIgnoreCaseAndIdNot("duplicado@huellago.com", 22L))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.actualizarPerfil("anterior@huellago.com",
                        new UsuarioPerfilDTO("Carlos", "Quispe", "duplicado@huellago.com")));

        assertEquals(409, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("correo ya está registrado"));
        verify(usuarioRepository, never()).save(any());
        verify(sesionRepository, never()).saveAll(any());
    }

    @Test
    void conservaSesionCuandoElCorreoNoCambia() {
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        service.actualizarPerfil("anterior@huellago.com",
                new UsuarioPerfilDTO("Carlos", "Quispe Soto", "ANTERIOR@HUELLAGO.COM"));

        verify(sesionRepository, never()).findByUsuario_IdAndActivaTrue(any());
        verify(sesionRepository, never()).saveAll(any());
    }
}
