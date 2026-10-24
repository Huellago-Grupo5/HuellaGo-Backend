package com.huellago.backend.controllers;

import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.JwtRequestFilter;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.SecurityConfiguration;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.SesionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SesionController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class LogoutControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SesionService sesionService;

    @MockitoBean
    JwtUtilService jwtUtilService;

    @MockitoBean
    UserDetailsService userDetailsService;

    @MockitoBean
    SesionRepository sesionRepository;

    @Test
    void logoutProtegidoCierraElTokenDelPrincipalYDevuelveConfirmacion() throws Exception {
        mvc.perform(post("/auth/logout")
                        .with(authentication(autenticacion(22L)))
                        .header("Authorization", "Bearer jwt-actual"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Sesión cerrada correctamente"));

        verify(sesionService).cerrarSesion(
                org.mockito.ArgumentMatchers.argThat(user -> user.getUser().getId().equals(22L)),
                eq("jwt-actual"));
    }

    @Test
    void logoutSinJwtEsRechazado() throws Exception {
        mvc.perform(post("/auth/logout")).andExpect(status().isForbidden());
        verify(sesionService, never()).cerrarSesion(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void conservaLaRutaAnteriorDeCierreDeSesion() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/sesiones/actual")
                        .with(authentication(autenticacion(22L)))
                        .header("Authorization", "Bearer jwt-actual"))
                .andExpect(status().isNoContent());

        verify(sesionService).cerrarSesion(
                org.mockito.ArgumentMatchers.argThat(user -> user.getUser().getId().equals(22L)),
                eq("jwt-actual"));
    }

    private UsernamePasswordAuthenticationToken autenticacion(Long usuarioId) {
        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        usuario.setCorreo("usuario" + usuarioId + "@huellago.com");
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
