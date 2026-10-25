package com.huellago.backend.controllers;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.*;
import com.huellago.backend.services.CampanaInstitucionalService;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CampanaInstitucionalController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class CampanaInstitucionalControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CampanaInstitucionalService servicio;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;

    private UsernamePasswordAuthenticationToken identidad() {
        Usuario usuario = new Usuario();
        usuario.setId(77L);
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private String json() {
        return "{\"nombre\":\"Movilidad limpia\",\"objetivo\":\"Agrupar trayectos\","
                + "\"fechaInicio\":\"" + LocalDate.now() + "\",\"fechaFin\":\"" + LocalDate.now().plusDays(6)
                + "\",\"administradorId\":99}";
    }

    @Test
    void creaConIdentidadJwtYDevuelve201LocationYEstado() throws Exception {
        var dto = new CampanaRespuestaDTO(50, 5L, "Movilidad limpia", "Agrupar trayectos", LocalDate.now(),
                LocalDate.now().plusDays(6), 7, true, "ACTIVA", null);
        when(servicio.crear(eq(5L), argThat(u -> u.getId().equals(77L)), any())).thenReturn(dto);
        mvc.perform(post("/instituciones/5/campanas").with(authentication(identidad()))
                        .contentType("application/json").content(json()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/instituciones/5/campanas/50"))
                .andExpect(jsonPath("$.institucionId").value(5))
                .andExpect(jsonPath("$.publicada").value(true))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
        verify(servicio).crear(eq(5L), argThat(u -> u.getId().equals(77L)), any());
    }

    @Test
    void consultaInstitucionalYPropiaUsanUsuarioAutenticado() throws Exception {
        when(servicio.listar(eq(5L), argThat(u -> u.getId().equals(77L)), eq(0), eq(20))).thenReturn(List.of());
        when(servicio.misCampanas(argThat(u -> u.getId().equals(77L)), eq(0), eq(20))).thenReturn(List.of());
        mvc.perform(get("/instituciones/5/campanas").with(authentication(identidad())))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/usuarios/me/campanas?usuarioId=99").with(authentication(identidad())))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void datosObligatoriosYFechasImposiblesSon400ConMensaje() throws Exception {
        for (String json : List.of("{}", "{\"nombre\":\"  \",\"objetivo\":\"  \"}",
                "{\"nombre\":\"Nombre\",\"objetivo\":\"Objetivo\",\"fechaInicio\":\"2027-02-30\",\"fechaFin\":\"2027-03-01\"}")) {
            mvc.perform(post("/instituciones/5/campanas").with(authentication(identidad()))
                            .contentType("application/json").content(json))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").exists());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void sinAutenticacionNoPuedeCrearNiConsultar() throws Exception {
        mvc.perform(post("/instituciones/5/campanas").contentType("application/json").content(json()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/instituciones/5/campanas")).andExpect(status().isForbidden());
        mvc.perform(get("/instituciones/5/campanas/50")).andExpect(status().isForbidden());
        mvc.perform(get("/usuarios/me/campanas")).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    void autorizacionInexistenciaYDuplicadosConservanCodigos() throws Exception {
        for (HttpStatus estado : List.of(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.CONFLICT)) {
            doThrow(new ResponseStatusException(estado, "Solicitud no permitida"))
                    .when(servicio).crear(any(), any(), any());
            mvc.perform(post("/instituciones/5/campanas").with(authentication(identidad()))
                            .contentType("application/json").content(json()))
                    .andExpect(status().is(estado.value()))
                    .andExpect(jsonPath("$.mensaje").value("Solicitud no permitida"));
        }
    }

    @Test
    void persistenciaEs500YRestriccionUnica409SinDetallesPrivados() throws Exception {
        doThrow(new DataAccessResourceFailureException("SQL secreto"))
                .when(servicio).crear(any(), any(), any());
        mvc.perform(post("/instituciones/5/campanas").with(authentication(identidad()))
                        .contentType("application/json").content(json()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensaje").value("No se pudo procesar la campaña. Intenta nuevamente más tarde"));
        doThrow(new DataIntegrityViolationException("SQL secreto"))
                .when(servicio).crear(any(), any(), any());
        mvc.perform(post("/instituciones/5/campanas").with(authentication(identidad()))
                        .contentType("application/json").content(json()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void identificadorMalformadoEs400() throws Exception {
        mvc.perform(get("/instituciones/texto/campanas").with(authentication(identidad())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").exists());
    }
}
