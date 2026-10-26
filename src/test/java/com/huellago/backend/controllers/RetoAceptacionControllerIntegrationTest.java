package com.huellago.backend.controllers;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RetoController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class RetoAceptacionControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean RetoService retos;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;

    @Test
    void aceptaParaUsuarioDelJwtYDevuelveConfirmacion() throws Exception {
        when(retos.aceptarReto(eq(7), anyUsuario22())).thenReturn(new UsuarioRetoRespuestaDTO(
                51, 22L, 7, "activo", BigDecimal.ZERO, LocalDateTime.now(), null));
        mvc.perform(post("/retos/7/aceptar").with(authentication(autenticacion())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(51))
                .andExpect(jsonPath("$.usuarioId").value(22))
                .andExpect(jsonPath("$.retoId").value(7))
                .andExpect(jsonPath("$.estado").value("activo"));
        verify(retos).aceptarReto(eq(7), anyUsuario22());
    }

    @Test
    void retoDuplicadoOInexistenteDevuelveMensajeClaro() throws Exception {
        when(retos.aceptarReto(eq(7), anyUsuario22())).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "El usuario ya aceptó este reto"));
        when(retos.aceptarReto(eq(999), anyUsuario22())).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Reto no encontrado"));
        mvc.perform(post("/retos/7/aceptar").with(authentication(autenticacion())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("El usuario ya aceptó este reto"));
        mvc.perform(post("/retos/999/aceptar").with(authentication(autenticacion())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Reto no encontrado"));
    }

    @Test
    void sinJwtOConJwtInvalidoNoAccede() throws Exception {
        mvc.perform(post("/retos/7/aceptar")).andExpect(status().isUnauthorized());
        mvc.perform(post("/retos/7/aceptar").header("Authorization", "Bearer invalido"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(retos);
    }

    private Usuario anyUsuario22() {
        return argThat(usuario -> usuario != null && Long.valueOf(22).equals(usuario.getId()));
    }

    private UsernamePasswordAuthenticationToken autenticacion() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo("usuario22@ejemplo.pe");
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
