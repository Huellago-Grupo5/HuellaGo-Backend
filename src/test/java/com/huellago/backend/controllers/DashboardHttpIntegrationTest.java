package com.huellago.backend.controllers;

import com.huellago.backend.dtos.DashboardDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.*;
import com.huellago.backend.services.DashboardService;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class, DashboardErrorHandler.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class DashboardHttpIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean DashboardService servicio;
    @MockitoBean UsuarioAuthorizationService autorizacion;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService usuarios;
    @MockitoBean SesionRepository sesiones;

    @Test void usaSoloIdentidadJwtYConservaContratoConCategoriasVacias() throws Exception {
        when(servicio.obtenerDashboard(22L)).thenReturn(new DashboardDTO(22L, 0, 1, 0L, null,
                0L, 0L, java.util.Map.of("Transporte", 0L, "Energ\u00eda", 0L, "Alimentaci\u00f3n", 0L, "Residuos", 0L), null, 0L, 0L));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/dashboard")
                .param("usuarioId", "99").with(authentication(autenticacion())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuarioId").value(22))
                .andExpect(jsonPath("$.cantidadHabitos").value(0))
                .andExpect(jsonPath("$.huellaCarbonoActual").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.habitosPorCategoria.Transporte").value(0));
        verify(servicio).obtenerDashboard(22L); verify(servicio, never()).obtenerDashboard(99L);
    }
    @Test void sinJwt401() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/dashboard"))
                .andExpect(status().isUnauthorized()); verifyNoInteractions(servicio);
    }
    @Test void persistencia503SinDetallesInternos() throws Exception {
        when(servicio.obtenerDashboard(22L)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("secreto"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/dashboard")
                .with(authentication(autenticacion())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("No se pudo cargar el dashboard en este momento"));
    }
    @Test void usuarioAusente404Controlado() throws Exception {
        when(servicio.obtenerDashboard(22L)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/dashboard")
                .with(authentication(autenticacion())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value("Usuario no encontrado"));
    }

    private UsernamePasswordAuthenticationToken autenticacion() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo("usuario22@ejemplo.pe");
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
