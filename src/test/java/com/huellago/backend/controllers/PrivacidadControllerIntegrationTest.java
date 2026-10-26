package com.huellago.backend.controllers;

import com.huellago.backend.dtos.PrivacidadActualizarDTO;
import com.huellago.backend.dtos.PrivacidadDTO;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PrivacidadController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class PrivacidadControllerIntegrationTest {

    private static final String URL = "/usuarios/me/preferencias/privacidad";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UsuarioService usuarioService;

    @MockitoBean
    JwtUtilService jwtUtilService;

    @MockitoBean
    UserDetailsService userDetailsService;

    @MockitoBean
    SesionRepository sesionRepository;

    @Test
    void consultaLaPrivacidadDelUsuarioAutenticado() throws Exception {
        when(usuarioService.obtenerPrivacidad(any())).thenReturn(respuesta(true));

        mvc.perform(get(URL).with(authentication(autenticacion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(22))
                .andExpect(jsonPath("$.visibilidadComunidad").value(true));

        verify(usuarioService).obtenerPrivacidad(
                argThat(usuario -> usuario.getId().equals(22L)));
    }

    @Test
    void activaModoPrivadoSinAceptarOtroUsuario() throws Exception {
        when(usuarioService.actualizarPrivacidad(
                any(), any(PrivacidadActualizarDTO.class)))
                .thenReturn(respuesta(false));

        mvc.perform(put(URL)
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("""
                                {
                                  "usuarioId": 99,
                                  "visibilidadComunidad": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(22))
                .andExpect(jsonPath("$.visibilidadComunidad").value(false));

        verify(usuarioService).actualizarPrivacidad(
                argThat(usuario -> usuario.getId().equals(22L)),
                argThat(dto -> Boolean.FALSE.equals(dto.getVisibilidadComunidad())));
    }

    @Test
    void permiteVolverAlModoPublico() throws Exception {
        when(usuarioService.actualizarPrivacidad(any(), any()))
                .thenReturn(respuesta(true));

        mvc.perform(put(URL)
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("{\"visibilidadComunidad\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibilidadComunidad").value(true));
    }

    @Test
    void rechazaValoresNulosOInvalidos() throws Exception {
        mvc.perform(put(URL)
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("{\"visibilidadComunidad\":null}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put(URL)
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("{\"visibilidadComunidad\":\"privado\"}"))
                .andExpect(status().isBadRequest());

        verify(usuarioService, never()).actualizarPrivacidad(any(), any());
    }

    @Test
    void solicitudesSinJwtSonRechazadas() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(put(URL)
                        .contentType("application/json")
                        .content("{\"visibilidadComunidad\":false}"))
                .andExpect(status().isUnauthorized());

        verify(usuarioService, never()).obtenerPrivacidad(any());
        verify(usuarioService, never()).actualizarPrivacidad(any(), any());
    }

    private UsernamePasswordAuthenticationToken autenticacion() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo("usuario22@huellago.com");
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private PrivacidadDTO respuesta(boolean visible) {
        return new PrivacidadDTO(22L, visible, LocalDateTime.now());
    }
}
