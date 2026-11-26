package com.huellago.backend.controllers;

import com.huellago.backend.dtos.RecomendacionCompletadaDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.*;
import com.huellago.backend.services.RecomendacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecomendacionController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class RecomendacionCompletadaControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean RecomendacionService servicio;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;

    private UsernamePasswordAuthenticationToken identidad() {
        Usuario usuario = new Usuario();
        usuario.setId(77L);
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void completarEHistorialUsanSoloIdentidadAutenticada() throws Exception {
        var dto = new RecomendacionCompletadaDTO(3, 8, "Separar residuos", "Separar envases",
                "completado", null, LocalDateTime.of(2026, 1, 2, 10, 0));
        when(servicio.completar(eq(3), argThat(u -> u.getId().equals(77L)))).thenReturn(dto);
        when(servicio.historialCompletadas(argThat(u -> u.getId().equals(77L))))
                .thenReturn(List.of(dto));
        mvc.perform(put("/usuarios/me/recomendaciones/3/completar")
                        .param("usuarioId", "99").with(authentication(identidad())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("completado"))
                .andExpect(jsonPath("$.fechaCompletado").exists());
        mvc.perform(get("/usuarios/me/recomendaciones/historial")
                        .param("usuarioId", "99").with(authentication(identidad())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usuarioRecomendacionId").value(3));
    }

    @Test
    void sinAutenticacionNoConsultaNiActualiza() throws Exception {
        mvc.perform(put("/usuarios/me/recomendaciones/3/completar")).andExpect(status().isUnauthorized());
        mvc.perform(get("/usuarios/me/recomendaciones/historial")).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test
    void inexistenteOAjenaTieneMensaje404() throws Exception {
        when(servicio.completar(eq(3), any())).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Recomendación no encontrada para el usuario"));
        mvc.perform(put("/usuarios/me/recomendaciones/3/completar").with(authentication(identidad())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void duplicadoTieneMensaje409() throws Exception {
        when(servicio.completar(eq(3), any())).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "La recomendación ya fue completada"));
        mvc.perform(put("/usuarios/me/recomendaciones/3/completar").with(authentication(identidad())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La recomendación ya fue completada"));
    }

    @Test
    void falloPersistenciaTieneMensaje500SinFiltrarDetalles() throws Exception {
        when(servicio.completar(eq(3), any())).thenThrow(new DataAccessResourceFailureException("password secreto"));
        mvc.perform(put("/usuarios/me/recomendaciones/3/completar").with(authentication(identidad())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensaje").value("No se pudo procesar la recomendación. Intenta nuevamente más tarde"));
    }

    @Test
    void identificadorMalformadoEs400() throws Exception {
        mvc.perform(put("/usuarios/me/recomendaciones/invalido/completar").with(authentication(identidad())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").exists());
        verifyNoInteractions(servicio);
    }
}
