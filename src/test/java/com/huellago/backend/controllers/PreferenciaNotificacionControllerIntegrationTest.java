package com.huellago.backend.controllers;

import com.huellago.backend.dtos.PreferenciaNotificacionActualizarDTO;
import com.huellago.backend.dtos.PreferenciaNotificacionDTO;
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

@WebMvcTest(PreferenciaNotificacionController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class PreferenciaNotificacionControllerIntegrationTest {

    private static final String URL = "/usuarios/me/preferencias/notificaciones";

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
    void consultaLaPreferenciaDelUsuarioAutenticado() throws Exception {
        when(usuarioService.obtenerPreferenciaNotificaciones(any()))
                .thenReturn(respuesta(true));

        mvc.perform(get(URL).with(authentication(autenticacion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(22))
                .andExpect(jsonPath("$.notificacionesActivas").value(true));

        verify(usuarioService).obtenerPreferenciaNotificaciones(
                argThat(usuario -> usuario.getId().equals(22L)));
    }

    @Test
    void desactivaLaPreferenciaSinAceptarOtroUsuario() throws Exception {
        when(usuarioService.actualizarPreferenciaNotificaciones(
                any(), any(PreferenciaNotificacionActualizarDTO.class)))
                .thenReturn(respuesta(false));

        mvc.perform(put(URL)
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("""
                                {
                                  "usuarioId": 99,
                                  "notificacionesActivas": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(22))
                .andExpect(jsonPath("$.notificacionesActivas").value(false));

        verify(usuarioService).actualizarPreferenciaNotificaciones(
                argThat(usuario -> usuario.getId().equals(22L)),
                argThat(dto -> Boolean.FALSE.equals(dto.getNotificacionesActivas())));
    }

    @Test
    void permiteActivarlaNuevamente() throws Exception {
        when(usuarioService.actualizarPreferenciaNotificaciones(any(), any()))
                .thenReturn(respuesta(true));

        mvc.perform(put(URL)
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("{\"notificacionesActivas\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificacionesActivas").value(true));
    }

    @Test
    void rechazaValorNuloOMalFormado() throws Exception {
        mvc.perform(put(URL)
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("{\"notificacionesActivas\":null}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put(URL)
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("{\"notificacionesActivas\":\"quizas\"}"))
                .andExpect(status().isBadRequest());

        verify(usuarioService, never()).actualizarPreferenciaNotificaciones(any(), any());
    }

    @Test
    void solicitudesSinJwtSonRechazadas() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(put(URL)
                        .contentType("application/json")
                        .content("{\"notificacionesActivas\":false}"))
                .andExpect(status().isUnauthorized());

        verify(usuarioService, never()).obtenerPreferenciaNotificaciones(any());
        verify(usuarioService, never()).actualizarPreferenciaNotificaciones(any(), any());
    }

    private UsernamePasswordAuthenticationToken autenticacion() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo("usuario22@huellago.com");
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private PreferenciaNotificacionDTO respuesta(boolean activa) {
        return new PreferenciaNotificacionDTO(22L, activa, LocalDateTime.now());
    }
}
