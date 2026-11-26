package com.huellago.backend.controllers;

import com.huellago.backend.dtos.NotificacionRespuestaDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.JwtRequestFilter;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.SecurityConfiguration;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.NotificacionService;
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
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificacionController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class NotificacionControllerIntegrationTest {
    private static final String URL = "/usuarios/me/notificaciones";
    @Autowired MockMvc mvc;
    @MockitoBean NotificacionService notificaciones;
    @MockitoBean JwtUtilService jwtUtilService;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean SesionRepository sesionRepository;

    @Test void exigeAutenticacionParaTodasLasOperaciones() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL + "/no-leidas")).andExpect(status().isUnauthorized());
        mvc.perform(put(URL + "/5/leer")).andExpect(status().isUnauthorized());
        mvc.perform(put(URL + "/leer-todas")).andExpect(status().isUnauthorized());
        verifyNoInteractions(notificaciones);
    }

    @Test void consultaYMarcaSoloConIdentidadAutenticada() throws Exception {
        var aviso = new NotificacionRespuestaDTO(5, "INSIGNIA", "Nueva insignia",
                "Obtuviste una insignia", false, LocalDateTime.of(2026, 11, 24, 12, 0));
        when(notificaciones.listar(22L)).thenReturn(List.of(aviso));
        when(notificaciones.contarNoLeidas(22L)).thenReturn(1L);
        when(notificaciones.marcarLeida(22L, 5)).thenReturn(
                new NotificacionRespuestaDTO(5, aviso.tipo(), aviso.titulo(), aviso.mensaje(), true, aviso.fechaCreacion()));
        when(notificaciones.marcarTodasLeidas(22L)).thenReturn(1);
        var auth = autenticacion();
        mvc.perform(get(URL).with(authentication(auth))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].leida").value(false));
        mvc.perform(get(URL + "/no-leidas").with(authentication(auth))).andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad").value(1));
        mvc.perform(put(URL + "/5/leer").with(authentication(auth))).andExpect(status().isOk())
                .andExpect(jsonPath("$.leida").value(true));
        mvc.perform(put(URL + "/leer-todas").with(authentication(auth))).andExpect(status().isOk())
                .andExpect(jsonPath("$.actualizadas").value(1));
        verify(notificaciones).listar(22L);
        verify(notificaciones).marcarLeida(22L, 5);
    }

    private UsernamePasswordAuthenticationToken autenticacion() {
        var usuario = new Usuario(); usuario.setId(22L); usuario.setCorreo("sintetico@example.invalid");
        var principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
