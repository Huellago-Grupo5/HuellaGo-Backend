package com.huellago.backend.controllers;

import com.huellago.backend.dtos.ComparacionPromedioDTO;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.JwtRequestFilter;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.SecurityConfiguration;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.security.UsuarioAuthorizationService;
import com.huellago.backend.services.HuellaCarbonoService;
import com.huellago.backend.services.ReporteAmbientalService;
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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HuellaCarbonoController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class ComparacionPromedioControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    HuellaCarbonoService service;

    @MockitoBean
    ReporteAmbientalService reporteAmbientalService;

    @MockitoBean
    UsuarioAuthorizationService authorizationService;

    @MockitoBean
    JwtUtilService jwtUtilService;

    @MockitoBean
    UserDetailsService userDetailsService;

    @MockitoBean
    SesionRepository sesionRepository;

    @Test
    void usuarioAutenticadoObtieneComparacion() throws Exception {
        when(service.compararConPromedio(22L)).thenReturn(new ComparacionPromedioDTO(
                true, new BigDecimal("12.00"), new BigDecimal("15.00"),
                "POR_DEBAJO_DEL_PROMEDIO", new BigDecimal("3.00"),
                new BigDecimal("20.00"), 3L, "kg CO2e", "semanal",
                "Tu impacto ambiental es menor que el promedio general"));

        mvc.perform(get("/huella-carbono/comparacion-promedio")
                        .with(authentication(autenticacion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparacionDisponible").value(true))
                .andExpect(jsonPath("$.huellaUsuario").value(12.00))
                .andExpect(jsonPath("$.promedioGeneral").value(15.00))
                .andExpect(jsonPath("$.posicion").value("POR_DEBAJO_DEL_PROMEDIO"))
                .andExpect(jsonPath("$.usuariosConDatos").value(3))
                .andExpect(jsonPath("$.usuarioId").doesNotExist());
    }

    @Test
    void usuarioSinHuellaRecibe404YNo403() throws Exception {
        when(service.compararConPromedio(22L)).thenThrow(new ResponseStatusException(
                HttpStatus.NOT_FOUND, "El usuario no tiene una huella de carbono calculada"));

        mvc.perform(get("/huella-carbono/comparacion-promedio")
                        .with(authentication(autenticacion())))
                .andExpect(status().isNotFound());
    }

    @Test
    void solicitudSinJwtNoPuedeConsultarLaComparacion() throws Exception {
        mvc.perform(get("/huella-carbono/comparacion-promedio"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).compararConPromedio(22L);
    }

    private UsernamePasswordAuthenticationToken autenticacion() {
        com.huellago.backend.entities.Usuario usuario = new com.huellago.backend.entities.Usuario();
        usuario.setId(22L);
        usuario.setCorreo("usuario@huellago.com");
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
