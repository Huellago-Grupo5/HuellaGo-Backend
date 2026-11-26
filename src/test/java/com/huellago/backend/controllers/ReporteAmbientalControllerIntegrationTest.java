package com.huellago.backend.controllers;

import com.huellago.backend.entities.Usuario;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HuellaCarbonoController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class ReporteAmbientalControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    HuellaCarbonoService huellaCarbonoService;

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
    void usuarioAutenticadoDescargaPdfConCabecerasCorrectas() throws Exception {
        byte[] pdf = "%PDF-1.7\nreporte\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(reporteAmbientalService.generarReporte(argThat(usuario -> usuario.getId().equals(22L))))
                .thenReturn(pdf);

        mvc.perform(get("/huella-carbono/reporte").with(authentication(autenticacion())))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("reporte-ambiental-huellago-22.pdf")))
                .andExpect(content().bytes(pdf));

        verify(reporteAmbientalService).generarReporte(
                argThat(usuario -> usuario.getId().equals(22L)));
    }

    @Test
    void solicitudSinJwtNoPuedeDescargarReporte() throws Exception {
        mvc.perform(get("/huella-carbono/reporte"))
                .andExpect(status().isUnauthorized());

        verify(reporteAmbientalService, never()).generarReporte(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void usuarioSinHuellasRecibe404() throws Exception {
        when(reporteAmbientalService.generarReporte(argThat(usuario -> usuario.getId().equals(22L))))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El usuario no tiene huellas de carbono registradas para exportar"));

        mvc.perform(get("/huella-carbono/reporte").with(authentication(autenticacion())))
                .andExpect(status().isNotFound());
    }

    private UsernamePasswordAuthenticationToken autenticacion() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setNombres("Carlos");
        usuario.setApellidos("Quispe");
        usuario.setCorreo("carlos@huellago.com");
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
