package com.huellago.backend.controllers;

import com.huellago.backend.dtos.ObjetivoAmbientalDTO;
import com.huellago.backend.security.JwtRequestFilter;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.SecurityConfiguration;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.services.ObjetivoAmbientalService;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ObjetivoAmbientalController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class ObjetivoAmbientalControllerIntegrationTest {

    private static final String CORREO = "usuario@huellago.com";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ObjetivoAmbientalService service;

    @MockitoBean
    JwtUtilService jwtUtilService;

    @MockitoBean
    UserDetailsService userDetailsService;

    @MockitoBean
    SesionRepository sesionRepository;

    @Test
    void seleccionVaciaDevuelve400YNoModificaLaSeleccion() throws Exception {
        mvc.perform(put("/usuarios/me/objetivos")
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("{\"objetivoIds\":[]}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).actualizarSeleccion(any(), any());
    }

    @Test
    void objetivoInexistenteDevuelve404() throws Exception {
        when(service.actualizarSeleccion(eq(CORREO), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El objetivo ambiental no existe: 99999"));

        mvc.perform(put("/usuarios/me/objetivos")
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("{\"objetivoIds\":[99999]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void seleccionValidaDevuelve200() throws Exception {
        when(service.actualizarSeleccion(eq(CORREO), any())).thenReturn(List.of(
                new ObjetivoAmbientalDTO(2L, "Objetivo 2", "Descripcion"),
                new ObjetivoAmbientalDTO(4L, "Objetivo 4", "Descripcion")));

        mvc.perform(put("/usuarios/me/objetivos")
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("{\"objetivoIds\":[2,4]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[1].id").value(4));
    }

    @Test
    void solicitudNormalSinJwtNoPuedeModificarObjetivos() throws Exception {
        mvc.perform(put("/usuarios/me/objetivos")
                        .contentType("application/json")
                        .content("{\"objetivoIds\":[2,4]}"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).actualizarSeleccion(any(), any());
    }

    @Test
    void despachoInternoDeErrorNoEsBloqueadoPorSeguridad() throws Exception {
                mvc.perform(put("/error")
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            return request;
                        })
                        .requestAttr("jakarta.servlet.error.status_code", 400)
                        .requestAttr("jakarta.servlet.error.request_uri", "/usuarios/me/objetivos"))
                .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken autenticacion() {
        com.huellago.backend.entities.Usuario usuario = new com.huellago.backend.entities.Usuario();
        usuario.setId(22L);
        usuario.setCorreo(CORREO);
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
