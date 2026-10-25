package com.huellago.backend.controllers;

import com.huellago.backend.dtos.ActualizarProgresoDTO;
import com.huellago.backend.dtos.RetoColectivoDTO;
import com.huellago.backend.dtos.UsuarioRetoRespuestaDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.JwtRequestFilter;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.SecurityConfiguration;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.RetoColectivoService;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RetoColectivoController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class RetoColectivoControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean RetoColectivoService servicio;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;

    @Test
    void consultaCatalogoYResultadoSinEscrituras() throws Exception {
        RetoColectivoDTO dto = new RetoColectivoDTO(7, "Reto grupal", LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1), 3, new BigDecimal("75.00"), "ACTIVO", null);
        when(servicio.listarDisponibles(22L)).thenReturn(List.of(dto));
        when(servicio.obtener(7, 22L)).thenReturn(dto);

        mvc.perform(get("/retos/colectivos").with(authentication(autenticacion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].retoId").value(7));
        mvc.perform(get("/retos/colectivos/7").with(authentication(autenticacion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progresoColectivo").value(75));
        verify(servicio, never()).cerrarVencido(any());
        verify(servicio, never()).unirse(any(), any());
    }

    @Test
    void unionYProgresoUsanExclusivamenteElJwt() throws Exception {
        UsuarioRetoRespuestaDTO respuesta = new UsuarioRetoRespuestaDTO(51, 22L, 7, "activo",
                BigDecimal.ZERO, null, null);
        when(servicio.unirse(eq(7), argThat(u -> u.getId().equals(22L))))
                .thenReturn(respuesta);
        when(servicio.actualizarProgreso(eq(51), eq(22L),
                argThat(dto -> dto.getProgreso().compareTo(BigDecimal.valueOf(50)) == 0)))
                .thenReturn(respuesta);

        mvc.perform(post("/retos/colectivos/7/unirse")
                        .with(authentication(autenticacion())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId").value(22));
        mvc.perform(put("/retos/colectivos/participaciones/51/progreso")
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("{\"progreso\":50,\"usuarioId\":99}"))
                .andExpect(status().isOk());
        verify(servicio).actualizarProgreso(eq(51), eq(22L), any(ActualizarProgresoDTO.class));
    }

    @Test
    void sinJwtNoAccedeNiModifica() throws Exception {
        mvc.perform(get("/retos/colectivos")).andExpect(status().isForbidden());
        mvc.perform(post("/retos/colectivos/7/unirse")).andExpect(status().isForbidden());
        mvc.perform(put("/retos/colectivos/participaciones/51/progreso")
                        .contentType("application/json").content("{\"progreso\":50}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    private UsernamePasswordAuthenticationToken autenticacion() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo("prueba@ejemplo.pe");
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
