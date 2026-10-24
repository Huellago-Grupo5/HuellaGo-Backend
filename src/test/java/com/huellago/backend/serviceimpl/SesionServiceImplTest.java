package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Sesion;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.SesionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SesionServiceImplTest {

    @Mock
    SesionRepository sesionRepository;

    @Mock
    JwtUtilService jwtUtilService;

    @InjectMocks
    SesionServiceImpl service;

    private Usuario usuario;
    private UserSecurity userSecurity;
    private Sesion sesionActual;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo("usuario22@huellago.com");
        userSecurity = new UserSecurity(usuario);

        sesionActual = new Sesion();
        sesionActual.setId(1L);
        sesionActual.setToken("jwt-actual");
        sesionActual.setUsuario(usuario);
        sesionActual.setActiva(true);
        sesionActual.setFechaExpiracion(LocalDateTime.now().plusHours(1));
        lenient().when(sesionRepository.findByToken("jwt-actual"))
                .thenReturn(Optional.of(sesionActual));
    }

    @Test
    void logoutInvalidaYPersisteSolamenteLaSesionSolicitante() {
        Sesion otraSesion = new Sesion();
        otraSesion.setId(2L);
        otraSesion.setToken("jwt-otra-sesion");
        otraSesion.setUsuario(usuario);
        otraSesion.setActiva(true);
        otraSesion.setFechaExpiracion(LocalDateTime.now().plusHours(1));

        service.cerrarSesion(userSecurity, "jwt-actual");

        assertFalse(sesionActual.getActiva());
        assertTrue(otraSesion.getActiva());
        verify(sesionRepository).save(sesionActual);
        verify(sesionRepository, never()).save(otraSesion);
    }

    @Test
    void impideCerrarUnaSesionQuePerteneceAOtroUsuario() {
        Usuario otroUsuario = new Usuario();
        otroUsuario.setId(23L);
        sesionActual.setUsuario(otroUsuario);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.cerrarSesion(userSecurity, "jwt-actual"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertTrue(sesionActual.getActiva());
        verify(sesionRepository, never()).save(any());
    }

    @Test
    void rechazaTokensSinSesionYSesionesYaCerradas() {
        when(sesionRepository.findByToken("token-desconocido"))
                .thenReturn(Optional.empty());
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ResponseStatusException.class,
                        () -> service.cerrarSesion(userSecurity, "token-desconocido"))
                        .getStatusCode());

        sesionActual.setActiva(false);
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ResponseStatusException.class,
                        () -> service.cerrarSesion(userSecurity, "jwt-actual"))
                        .getStatusCode());
        verify(sesionRepository, never()).save(any());
    }

    @Test
    void rechazaSesionesExpiradas() {
        sesionActual.setFechaExpiracion(LocalDateTime.now().minusSeconds(1));

        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ResponseStatusException.class,
                        () -> service.cerrarSesion(userSecurity, "jwt-actual"))
                        .getStatusCode());
        verify(sesionRepository, never()).save(any());
    }

    @Test
    void registrarLoginPersistiraUnaNuevaSesionActiva() {
        when(jwtUtilService.extractExpiration("jwt-nuevo"))
                .thenReturn(new Date(System.currentTimeMillis() + 3_600_000));

        service.registrarSesion(userSecurity, "jwt-nuevo");

        verify(sesionRepository).save(org.mockito.ArgumentMatchers.argThat(nuevaSesion ->
                nuevaSesion.getUsuario().getId().equals(22L)
                        && nuevaSesion.getToken().equals("jwt-nuevo")
                        && Boolean.TRUE.equals(nuevaSesion.getActiva())));
    }

    @Test
    void rechazaSesionQueHuella45YaDesactivo() {
        sesionActual.setActiva(false);

        assertThrows(ResponseStatusException.class,
                () -> service.cerrarSesion(userSecurity, "jwt-actual"));
        verify(sesionRepository, never()).save(any());
    }
}
