package com.huellago.backend.controllers;

import com.huellago.backend.dtos.RankingComunitarioDTO;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RankingComunitarioController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class RankingComunitarioControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean UsuarioService usuarios;
    @MockitoBean JwtUtilService jwtUtilService;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean SesionRepository sesionRepository;

    @Test void requiereAutenticacion() throws Exception {
        mvc.perform(get("/comunidad/ranking")).andExpect(status().isUnauthorized());
        verifyNoInteractions(usuarios);
    }

    @Test void soloExponeDatosDelRankingYPosicionDelAutenticado() throws Exception {
        when(usuarios.obtenerRankingComunitario(argThat(u -> u != null && u.getId().equals(70L))))
                .thenReturn(new RankingComunitarioDTO(List.of(
                        new RankingComunitarioDTO.Participante(1, "Ana", 80, 2),
                        new RankingComunitarioDTO.Participante(2, "Usuario 70", 40, 1)), 2, null));

        mvc.perform(get("/comunidad/ranking").with(authentication(autenticacion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.miPosicion").value(2))
                .andExpect(jsonPath("$.participantes[0].nombreVisible").value("Ana"))
                .andExpect(jsonPath("$.participantes[1].ecoPuntos").value(40))
                .andExpect(jsonPath("$.participantes[0].correo").doesNotExist())
                .andExpect(jsonPath("$.participantes[0].contrasena").doesNotExist());
        verify(usuarios).obtenerRankingComunitario(argThat(u -> u.getId().equals(70L)));
    }

    private UsernamePasswordAuthenticationToken autenticacion() {
        var usuario = new Usuario(); usuario.setId(70L); usuario.setCorreo("usuario@example.invalid");
        var principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
