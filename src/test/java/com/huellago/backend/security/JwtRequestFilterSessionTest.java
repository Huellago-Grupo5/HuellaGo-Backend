package com.huellago.backend.security;

import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtRequestFilterSessionTest {

    @Mock
    UserDetailsService userDetailsService;

    @Mock
    JwtUtilService jwtUtilService;

    @Mock
    SesionRepository sesionRepository;

    @InjectMocks
    JwtRequestFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rechazaTokenLuegoDelLogoutPorqueLaSesionEstaInactiva()
            throws ServletException, IOException {
        UserSecurity usuario = usuario();
        when(jwtUtilService.extractUsername("jwt-logout")).thenReturn(usuario.getUsername());
        when(userDetailsService.loadUserByUsername(usuario.getUsername())).thenReturn(usuario);
        when(jwtUtilService.validateToken("jwt-logout", usuario)).thenReturn(true);
        when(sesionRepository.existsByTokenAndActivaTrueAndFechaExpiracionAfter(
                org.mockito.ArgumentMatchers.eq("jwt-logout"),
                org.mockito.ArgumentMatchers.any())).thenReturn(false);

        MockFilterChain chain = filtrar("jwt-logout");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
    }

    @Test
    void permiteElJwtEmitidoAlIniciarUnaSesionNueva()
            throws ServletException, IOException {
        UserSecurity usuario = usuario();
        when(jwtUtilService.extractUsername("jwt-login-nuevo")).thenReturn(usuario.getUsername());
        when(userDetailsService.loadUserByUsername(usuario.getUsername())).thenReturn(usuario);
        when(jwtUtilService.validateToken("jwt-login-nuevo", usuario)).thenReturn(true);
        when(sesionRepository.existsByTokenAndActivaTrueAndFechaExpiracionAfter(
                org.mockito.ArgumentMatchers.eq("jwt-login-nuevo"),
                org.mockito.ArgumentMatchers.any())).thenReturn(true);

        filtrar("jwt-login-nuevo");

        assertEquals(usuario, ((UserSecurity) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal()));
    }

    @Test
    void tokenInvalidoNoAutenticaLaPeticion() throws ServletException, IOException {
        when(jwtUtilService.extractUsername("token-invalido"))
                .thenThrow(new IllegalArgumentException("invalid token"));

        MockFilterChain chain = filtrar("token-invalido");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
    }

    private MockFilterChain filtrar(String token) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain;
    }

    private UserSecurity usuario() {
        Usuario entidad = new Usuario();
        entidad.setId(22L);
        entidad.setCorreo("usuario22@huellago.com");
        entidad.setContrasena("hash");
        return new UserSecurity(entidad);
    }
}
