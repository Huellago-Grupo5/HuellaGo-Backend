package com.huellago.backend.controllers;

import com.huellago.backend.dtos.PreferenciasVisualesDTO;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PreferenciasVisualesController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class})
class PreferenciasVisualesControllerIntegrationTest {
    private static final String URL = "/usuarios/me/preferencias/visualizacion";
    @Autowired MockMvc mvc;
    @MockitoBean UsuarioService usuarioService;
    @MockitoBean JwtUtilService jwtUtilService;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean SesionRepository sesionRepository;

    @Test void soloLaIdentidadAutenticadaPuedeConsultarYActualizar() throws Exception {
        when(usuarioService.obtenerPreferenciasVisuales(any())).thenReturn(new PreferenciasVisualesDTO("claro", "metric"));
        when(usuarioService.actualizarPreferenciasVisuales(any(), any())).thenReturn(new PreferenciasVisualesDTO("oscuro", "imperial"));
        mvc.perform(get(URL).with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tema").value("claro"));
        mvc.perform(put(URL).with(authentication(auth())).contentType("application/json")
                .content("{\"tema\":\"oscuro\",\"unidades\":\"imperial\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unidades").value("imperial"));
        verify(usuarioService).actualizarPreferenciasVisuales(argThat(u -> u.getId().equals(22L)),
                argThat(dto -> dto.tema().equals("oscuro") && dto.unidades().equals("imperial")));
    }

    @Test void noAdmitePeticionesSinAutenticacion() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(put(URL).contentType("application/json").content("{\"tema\":\"claro\",\"unidades\":\"metric\"}"))
                .andExpect(status().isUnauthorized());
        verify(usuarioService, never()).actualizarPreferenciasVisuales(any(), any());
    }

    private UsernamePasswordAuthenticationToken auth() {
        Usuario usuario = new Usuario(); usuario.setId(22L); usuario.setCorreo("perfil22@example.test");
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
