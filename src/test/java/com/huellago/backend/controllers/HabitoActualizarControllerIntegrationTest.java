package com.huellago.backend.controllers;

import com.huellago.backend.dtos.HabitoRespuestaDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.*;
import com.huellago.backend.services.HabitoService;
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

@WebMvcTest(HabitoController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class, HabitoErrorHandler.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class HabitoActualizarControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean HabitoService servicio;
    @MockitoBean UsuarioAuthorizationService autorizacion;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService usuarios;
    @MockitoBean SesionRepository sesiones;

    @Test
    void actualizaConIdentidadDelJwtSinAceptarUsuarioIdDelBody() throws Exception {
        when(servicio.actualizar(eq(10L), any(), any())).thenReturn(
                new HabitoRespuestaDTO(10L, 22L, 12L, "reciclas", BigDecimal.ONE,
                        "boolean", LocalDateTime.now().minusDays(1), LocalDateTime.now()));
        mvc.perform(put("/habitos/10").with(authentication(autenticacion()))
                .contentType("application/json")
                .content("{\"usuarioId\":99,\"nombre\":\"reciclas\",\"valor\":1,\"unidad\":\"boolean\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.usuarioId").value(22));
        verify(servicio).actualizar(eq(10L), argThat(usuario -> usuario.getId().equals(22L)), any());
    }

    @Test
    void rechazaSolicitudSinJwtOConJwtInvalido() throws Exception {
        mvc.perform(put("/habitos/10").contentType("application/json")
                .content("{\"nombre\":\"medio\",\"valor\":0,\"unidad\":\"Bus\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/habitos/10").header("Authorization", "Bearer invalido")
                .contentType("application/json")
                .content("{\"nombre\":\"medio\",\"valor\":0,\"unidad\":\"Bus\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test
    void responde400ControladoAnteJsonMalformado() throws Exception {
        mvc.perform(put("/habitos/10").with(authentication(autenticacion()))
                .contentType("application/json").content("{\"valor\":\"no-es-numero\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("JSON o tipos de datos inválidos"));
        verifyNoInteractions(servicio);
    }

    private UsernamePasswordAuthenticationToken autenticacion() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo("usuario22@ejemplo.pe");
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
