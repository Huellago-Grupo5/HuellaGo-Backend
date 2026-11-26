package com.huellago.backend.controllers;

import com.huellago.backend.dtos.ReporteInstitucionalDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.*;
import com.huellago.backend.services.ReporteInstitucionalService;
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

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardInstitucionalController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class DashboardInstitucionalControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ReporteInstitucionalService reportes;
    @MockitoBean JwtUtilService jwtUtilService;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean SesionRepository sesionRepository;

    @Test void exigeAutenticacionEnAmbasConsultas() throws Exception {
        mvc.perform(get("/instituciones/5/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/instituciones/5/impacto-colectivo")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reportes);
    }

    @Test void delegaAlReporteAutorizadoSinExponerHuellasIndividuales() throws Exception {
        var huella = new ReporteInstitucionalDTO.HuellaPromedio(4, 3, 3, null,
                "kg CO2e", "semanal", "COHORTE_INSUFICIENTE", "Limitación");
        var impacto = new ReporteInstitucionalDTO.SeguimientoImpacto(0, 0, null, null,
                "DATOS_INSUFICIENTES", "Limitación");
        var reporte = new ReporteInstitucionalDTO(new ReporteInstitucionalDTO.InstitucionDatos(5L, "Universidad"),
                LocalDateTime.of(2026, 11, 25, 0, 0), new ReporteInstitucionalDTO.Filtros(null, null, null),
                new ReporteInstitucionalDTO.Resumen(0, 0, 0, 0, 0, 0, 0, 0, 0, null, 0, 0, 0),
                List.of(), List.of(), huella, impacto);
        var auth = autenticacion();
        when(reportes.consultar(eq(5L), any(Usuario.class), isNull(), isNull(), isNull())).thenReturn(reporte);

        mvc.perform(get("/instituciones/5/dashboard").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.huellaPromedio.miembrosInstitucion").value(4))
                .andExpect(jsonPath("$.huellaPromedio.promedioKgCo2eSemanal").doesNotExist());
        mvc.perform(get("/instituciones/5/impacto-colectivo").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DATOS_INSUFICIENTES"));
        verify(reportes, times(2)).consultar(eq(5L), argThat(u -> u.getId().equals(70L)),
                isNull(), isNull(), isNull());
    }

    private UsernamePasswordAuthenticationToken autenticacion() {
        var usuario = new Usuario(); usuario.setId(70L); usuario.setCorreo("sintetico@example.invalid");
        var principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
