package com.huellago.backend.controllers;

import com.huellago.backend.dtos.InsigniaRespuestaDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.JwtRequestFilter;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.SecurityConfiguration;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.InsigniaService;
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InsigniaController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class InsigniaControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean InsigniaService insignias;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;

    @Test
    void devuelveObtenidasYBloqueadasSoloDelUsuarioAutenticado() throws Exception {
        when(insignias.obtenerInsignias(argThat(u -> u.getId().equals(22L))))
                .thenReturn(List.of(
                        new InsigniaRespuestaDTO(1, "Obtenida", "", "", 10,
                                LocalDateTime.of(2026, 10, 20, 10, 0), true),
                        new InsigniaRespuestaDTO(2, "Bloqueada", "", "", 50,
                                null, false)));

        mvc.perform(get("/usuarios/me/insignias").with(authentication(autenticacion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].desbloqueada").value(true))
                .andExpect(jsonPath("$[1].desbloqueada").value(false))
                .andExpect(jsonPath("$[1].fechaObtencion").doesNotExist());
        verify(insignias).obtenerInsignias(argThat(u -> u.getId().equals(22L)));
    }

    @Test
    void sinJwtNoExponeLaColeccion() throws Exception {
        mvc.perform(get("/usuarios/me/insignias")).andExpect(status().isUnauthorized());
        verifyNoInteractions(insignias);
    }

    private UsernamePasswordAuthenticationToken autenticacion() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo("usuario22@ejemplo.pe");
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
