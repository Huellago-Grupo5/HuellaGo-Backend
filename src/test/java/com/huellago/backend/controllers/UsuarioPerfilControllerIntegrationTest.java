package com.huellago.backend.controllers;

import com.huellago.backend.dtos.UsuarioPerfilDTO;
import com.huellago.backend.dtos.UsuarioRespuestaDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.JwtRequestFilter;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.SecurityConfiguration;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.SesionService;
import com.huellago.backend.services.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsuarioController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class UsuarioPerfilControllerIntegrationTest {

    private static final String CORREO_ACTUAL = "actual@huellago.com";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UsuarioService usuarioService;

    @MockitoBean
    com.huellago.backend.services.AccesoCuentaService accesoCuentaService;

    @MockitoBean
    SesionService sesionService;

    @MockitoBean
    AuthenticationManager authenticationManager;

    @MockitoBean
    JwtUtilService jwtUtilService;

    @MockitoBean
    UserDetailsService userDetailsService;

    @MockitoBean
    SesionRepository sesionRepository;

    @Test
    void usuarioAutenticadoActualizaSolamenteSuPerfil() throws Exception {
        when(usuarioService.actualizarPerfil(
                org.mockito.ArgumentMatchers.eq(CORREO_ACTUAL), any(UsuarioPerfilDTO.class)))
                .thenReturn(respuesta("nuevo@huellago.com"));

        mvc.perform(put("/auth/perfil")
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("""
                                {
                                  "id": 99,
                                  "nombres": "Carlos Alberto",
                                  "apellidos": "Quispe Soto",
                                  "correo": "nuevo@huellago.com",
                                  "ecoPuntos": 99999,
                                  "nivel": 100
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Reauthentication-Required", "true"))
                .andExpect(jsonPath("$.id").value(22))
                .andExpect(jsonPath("$.correo").value("nuevo@huellago.com"));

        verify(usuarioService).actualizarPerfil(
                org.mockito.ArgumentMatchers.eq(CORREO_ACTUAL),
                argThat(dto -> dto.getNombres().equals("Carlos Alberto")
                        && dto.getApellidos().equals("Quispe Soto")
                        && dto.getCorreo().equals("nuevo@huellago.com")));
    }

    @Test
    void rechazaDatosVaciosOInvalidos() throws Exception {
        mvc.perform(put("/auth/perfil")
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("""
                                {
                                  "nombres": " ",
                                  "apellidos": "Q1",
                                  "correo": "correo-invalido"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(usuarioService, never()).actualizarPerfil(any(), any());
    }

    @Test
    void correoDuplicadoDevuelve409() throws Exception {
        when(usuarioService.actualizarPerfil(
                org.mockito.ArgumentMatchers.eq(CORREO_ACTUAL), any(UsuarioPerfilDTO.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "El correo ya está registrado"));

        mvc.perform(put("/auth/perfil")
                        .with(authentication(autenticacion()))
                        .contentType("application/json")
                        .content("""
                                {
                                  "nombres": "Carlos",
                                  "apellidos": "Quispe",
                                  "correo": "duplicado@huellago.com"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void solicitudSinJwtNoPuedeEditarPerfil() throws Exception {
        mvc.perform(put("/auth/perfil")
                        .contentType("application/json")
                        .content("""
                                {
                                  "nombres": "Carlos",
                                  "apellidos": "Quispe",
                                  "correo": "nuevo@huellago.com"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verify(usuarioService, never()).actualizarPerfil(any(), any());
    }

    private UsernamePasswordAuthenticationToken autenticacion() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo(CORREO_ACTUAL);
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private UsuarioRespuestaDTO respuesta(String correo) {
        return new UsuarioRespuestaDTO(22L, "Carlos Alberto", "Quispe Soto", correo,
                80, 2, true, false, LocalDateTime.now().minusMonths(1), LocalDateTime.now());
    }
}
