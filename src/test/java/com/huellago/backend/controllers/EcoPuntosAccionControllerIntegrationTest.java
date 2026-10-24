package com.huellago.backend.controllers;

import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.JwtRequestFilter;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.SecurityConfiguration;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EcoPuntosController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class EcoPuntosAccionControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean UsuarioService usuarios;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;

    @Test
    void accionAutodeclaradaDevuelveErrorClaroYUsaIdentidadDelJwt() throws Exception {
        when(usuarios.otorgarEcoPuntos(argThat(u -> u.getId().equals(22L)), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "No se pueden otorgar eco-puntos por acciones sin verificación"));

        mvc.perform(post("/usuarios/me/eco-puntos")
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("{\"accion\":\"RECICLAR\",\"usuarioId\":99}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(
                        "No se pueden otorgar eco-puntos por acciones sin verificación"));
        verify(usuarios).otorgarEcoPuntos(argThat(u -> u.getId().equals(22L)),
                argThat(dto -> "RECICLAR".equals(dto.getAccion())));
    }

    @Test
    void sinJwtNoPermiteSolicitarEcoPuntos() throws Exception {
        mvc.perform(post("/usuarios/me/eco-puntos")
                        .contentType("application/json")
                        .content("{\"accion\":\"RECICLAR\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(usuarios);
    }

    private UsernamePasswordAuthenticationToken autenticacion() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo("usuario22@ejemplo.pe");
        return new UsernamePasswordAuthenticationToken(new UserSecurity(usuario), null,
                new UserSecurity(usuario).getAuthorities());
    }
}
