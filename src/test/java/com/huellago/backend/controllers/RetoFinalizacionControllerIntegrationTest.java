package com.huellago.backend.controllers;

import com.huellago.backend.dtos.ActualizarProgresoDTO;
import com.huellago.backend.dtos.UsuarioRetoRespuestaDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.JwtRequestFilter;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.SecurityConfiguration;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.RetoService;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RetosActivosController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class RetoFinalizacionControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean RetoService retos;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;

    @Test
    void progresoYFinalizacionUsanElUsuarioDelJwt() throws Exception {
        when(retos.actualizarProgreso(eq(29), eq(22L), anyProgresoCien()))
                .thenReturn(respuesta("activo", null));
        when(retos.completarReto(29, 22L))
                .thenReturn(respuesta("completado", LocalDateTime.now()));

        mvc.perform(put("/retos-activos/29/progreso")
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("{\"progreso\":100,\"usuarioId\":99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(22))
                .andExpect(jsonPath("$.estado").value("activo"));
        mvc.perform(put("/retos-activos/29/completar")
                        .with(authentication(autenticacion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(29))
                .andExpect(jsonPath("$.retoId").value(18))
                .andExpect(jsonPath("$.usuarioId").value(22))
                .andExpect(jsonPath("$.estado").value("completado"))
                .andExpect(jsonPath("$.fechaCompletado").exists());
        verify(retos).actualizarProgreso(eq(29), eq(22L), anyProgresoCien());
        verify(retos).completarReto(29, 22L);
    }

    @Test
    void incompletoOAjenoDevuelveErrorClaro() throws Exception {
        when(retos.completarReto(29, 22L)).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "El progreso debe ser 100 para completar el reto"));
        when(retos.completarReto(30, 22L)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Participación no encontrada"));
        mvc.perform(put("/retos-activos/29/completar")
                        .with(authentication(autenticacion())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("El progreso debe ser 100 para completar el reto"));
        mvc.perform(put("/retos-activos/30/completar")
                        .with(authentication(autenticacion())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Participación no encontrada"));
    }

    @Test
    void sinJwtOConTokenInvalidoNoAccede() throws Exception {
        mvc.perform(put("/retos-activos/29/completar")).andExpect(status().isForbidden());
        mvc.perform(put("/retos-activos/29/completar")
                        .header("Authorization", "Bearer invalido"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(retos);
    }

    @Test
    void jsonDeProgresoInvalidoDevuelve400() throws Exception {
        mvc.perform(put("/retos-activos/29/progreso")
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("{\"progreso\":\"no-numero\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("JSON o progreso inválido"));
        verifyNoInteractions(retos);
    }

    private ActualizarProgresoDTO anyProgresoCien() {
        return argThat(dto -> dto != null && dto.getProgreso() != null
                && dto.getProgreso().compareTo(BigDecimal.valueOf(100)) == 0);
    }

    private UsuarioRetoRespuestaDTO respuesta(String estado, LocalDateTime fecha) {
        return new UsuarioRetoRespuestaDTO(29, 22L, 18, estado, BigDecimal.valueOf(100),
                LocalDateTime.of(2026, 10, 20, 10, 0), fecha);
    }

    private UsernamePasswordAuthenticationToken autenticacion() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo("usuario22@ejemplo.pe");
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
