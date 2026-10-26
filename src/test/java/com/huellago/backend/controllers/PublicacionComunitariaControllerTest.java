package com.huellago.backend.controllers;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.*;
import com.huellago.backend.services.PublicacionComunitariaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PublicacionComunitariaController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class PublicacionComunitariaControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PublicacionComunitariaService servicio;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;

    private UsernamePasswordAuthenticationToken identidad() {
        Usuario usuario = new Usuario();
        usuario.setId(77L);
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void publicacionConJwtTieneConfirmacion201EIgnoraAutorEnviado() throws Exception {
        var publicacion = new PublicacionComunitariaDTO(1L, 77L, "Ana", "INSIGNIA", 8,
                "Primer paso verde", LocalDateTime.of(2026, 10, 24, 10, 0));
        when(servicio.compartir(argThat(u -> u.getId().equals(77L)),
                eq(new CompartirLogroDTO(CompartirLogroDTO.Tipo.INSIGNIA, 8))))
                .thenReturn(new PublicacionCreadaDTO("Logro publicado en la comunidad", publicacion));
        mvc.perform(post("/comunidad/publicaciones").with(authentication(identidad()))
                        .contentType("application/json")
                        .content("{\"tipo\":\"INSIGNIA\",\"origenId\":8,\"usuarioId\":99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Logro publicado en la comunidad"))
                .andExpect(jsonPath("$.publicacion.autorId").value(77))
                .andExpect(jsonPath("$.publicacion.fechaPublicacion").exists())
                .andExpect(jsonPath("$.publicacion.correo").doesNotExist())
                .andExpect(jsonPath("$.publicacion.contrasena").doesNotExist());
    }

    @Test
    void consultaComunitariaDevuelveListaConNoStore() throws Exception {
        when(servicio.listar(argThat(u -> u.getId().equals(77L)), eq(0), eq(20))).thenReturn(List.of());
        mvc.perform(get("/comunidad/publicaciones").with(authentication(identidad())))
                .andExpect(status().isOk()).andExpect(content().json("[]"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void sinJwtOTokenInvalidoNoPublicaNiConsulta() throws Exception {
        mvc.perform(post("/comunidad/publicaciones").contentType("application/json")
                        .content("{\"tipo\":\"RETO\",\"origenId\":9}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/comunidad/publicaciones")).andExpect(status().isUnauthorized());
        when(jwt.extractUsername("invalido")).thenThrow(new IllegalArgumentException("token inválido"));
        mvc.perform(get("/comunidad/publicaciones").header("Authorization", "Bearer invalido"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test
    void camposOmitidosTiposEIdsInvalidosSon400() throws Exception {
        for (String json : List.of("{}", "{\"tipo\":\"INSIGNIA\",\"origenId\":0}",
                "{\"tipo\":\"INSIGNIA\",\"origenId\":1.5}",
                "{\"tipo\":\"OTRO\",\"origenId\":8}", "{malformado")) {
            mvc.perform(post("/comunidad/publicaciones").with(authentication(identidad()))
                            .contentType("application/json").content(json))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").exists());
        }
        mvc.perform(get("/comunidad/publicaciones?pagina=texto").with(authentication(identidad())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").exists());
        verifyNoInteractions(servicio);
    }

    @Test
    void erroresDeDominioConservanCodigoYMensaje() throws Exception {
        for (HttpStatus estado : List.of(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.CONFLICT)) {
            doThrow(new ResponseStatusException(estado, "No se puede compartir"))
                    .when(servicio).compartir(any(), any());
            mvc.perform(post("/comunidad/publicaciones").with(authentication(identidad()))
                            .contentType("application/json").content("{\"tipo\":\"RETO\",\"origenId\":9}"))
                    .andExpect(status().is(estado.value()))
                    .andExpect(jsonPath("$.mensaje").value("No se puede compartir"));
        }
    }

    @Test
    void restriccionUnicaEs409Controlado() throws Exception {
        when(servicio.compartir(any(), any())).thenThrow(new DataIntegrityViolationException("SQL privado"));
        mvc.perform(post("/comunidad/publicaciones").with(authentication(identidad()))
                        .contentType("application/json").content("{\"tipo\":\"RETO\",\"origenId\":9}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void falloPersistenciaEs500SinDetallesInternos() throws Exception {
        when(servicio.compartir(any(), any())).thenThrow(new DataAccessResourceFailureException("credenciales secretas"));
        mvc.perform(post("/comunidad/publicaciones").with(authentication(identidad()))
                        .contentType("application/json").content("{\"tipo\":\"INSIGNIA\",\"origenId\":8}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensaje").value("No se pudo procesar la publicación en este momento. Intenta nuevamente más tarde"));
    }
}
