package com.huellago.backend.controllers;

import com.huellago.backend.dtos.ConversionRecomendacionDTO;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecomendacionController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class ConversionRecomendacionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean RecomendacionService servicio;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;

    @Test
    void confirmaConversionConIdentidadDelJwt() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        UserSecurity principal = new UserSecurity(usuario);
        when(servicio.convertirEnReto(eq(3), argThat(u -> u.getId().equals(22L))))
                .thenReturn(new ConversionRecomendacionDTO("Recomendación convertida en reto personal",
                        3, 43, 63, "activo", LocalDateTime.of(2026, 10, 24, 10, 0)));
        mvc.perform(post("/usuarios/me/recomendaciones/3/convertir-en-reto")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.retoId").value(43))
                .andExpect(jsonPath("$.participacionId").value(63))
                .andExpect(jsonPath("$.estado").value("activo"));
    }

    @Test
    void sinJwtNoConvierte() throws Exception {
        mvc.perform(post("/usuarios/me/recomendaciones/3/convertir-en-reto"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }
}
