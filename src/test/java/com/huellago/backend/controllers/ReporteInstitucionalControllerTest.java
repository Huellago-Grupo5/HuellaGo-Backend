package com.huellago.backend.controllers;

import com.huellago.backend.dtos.ReporteInstitucionalDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.*;
import com.huellago.backend.serviceimpl.ReporteInstitucionalCsv;
import com.huellago.backend.services.ReporteInstitucionalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReporteInstitucionalController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class, JwtUtilService.class, ReporteInstitucionalCsv.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class ReporteInstitucionalControllerTest {
    static final String URL = "/instituciones/5/reportes/desempeno-ambiental";
    @Autowired MockMvc mvc;
    @MockitoBean ReporteInstitucionalService servicio;
    @MockitoSpyBean ReporteInstitucionalCsv csv;
    @Autowired JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;

    UsernamePasswordAuthenticationToken identidad() {
        Usuario usuario = new Usuario(); usuario.setId(77L);
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
    ReporteInstitucionalDTO reporte() {
        return new ReporteInstitucionalDTO(new ReporteInstitucionalDTO.InstitucionDatos(5L, "Universidad Perú"),
                LocalDateTime.of(2026, 10, 25, 12, 30), new ReporteInstitucionalDTO.Filtros(null, null, null),
                new ReporteInstitucionalDTO.Resumen(0, 0, 0, 0, 0, 0, 0, 0, 0, null, 0, 0, 0), List.of(), List.of("No hay campañas"));
    }
    void respuesta() { when(servicio.consultar(eq(5L), any(), any(), any(), any())).thenReturn(reporte()); }

    @Test void consultaJsonIdentificaUsuarioJwtNoIdDelCliente() throws Exception {
        respuesta();
        mvc.perform(get(URL + "?administradorId=999&usuarioId=999").with(authentication(identidad())))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.institucion.id").value(5)).andExpect(jsonPath("$.institucion.nombre").value("Universidad Perú"))
                .andExpect(jsonPath("$.resumen.totalCampanas").value(0)).andExpect(jsonPath("$.campanas").isEmpty())
                .andExpect(jsonPath("$.institucion.correoContacto").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(servicio).consultar(eq(5L), argThat(u -> u.getId().equals(77L)), isNull(), isNull(), isNull());
    }
    @Test void descargaArchivoRealConCabecerasNombreBomYContenido() throws Exception {
        respuesta(); byte[] esperado = new ReporteInstitucionalCsv().generar(reporte());
        mvc.perform(get(URL + "/descargar").with(authentication(identidad())))
                .andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"reporte-institucional-5-20261025-123000.csv\""))
                .andExpect(header().longValue("Content-Length", esperado.length))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Content-Disposition"))
                .andExpect(content().bytes(esperado));
    }
    @Test void filtrosSeInterpretanComoFechasIsoYSePropagan() throws Exception {
        respuesta();
        for (String sufijo : List.of("", "/descargar")) {
            mvc.perform(get(URL + sufijo + "?desde=2026-01-01&hasta=2026-12-31&campanaId=10").with(authentication(identidad())))
                    .andExpect(status().isOk());
        }
        verify(servicio, times(2)).consultar(eq(5L), any(), eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 12, 31)), eq(10));
    }
    @Test void fechasImposiblesEIdentificadoresMalformadosSon400() throws Exception {
        for (String consulta : List.of("?desde=2026-02-30", "?hasta=ayer", "?campanaId=texto", "?campanaId=99999999999")) {
            mvc.perform(get(URL + consulta).with(authentication(identidad())))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").exists());
        }
        mvc.perform(get("/instituciones/texto/reportes/desempeno-ambiental").with(authentication(identidad())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }
    @Test void sinJwtAmbasRutasRechazadasPorSeguridadExistente() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
        mvc.perform(get(URL + "/descargar")).andExpect(status().isForbidden());
        verifyNoInteractions(servicio, csv);
    }
    @Test void tokenInvalidoNoPermiteConsultarNiDescargar() throws Exception {
        mvc.perform(get(URL + "/descargar").header("Authorization", "Bearer invalido"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }
    @Test void jwtFirmadoSesionActivaEIdentidadRealDelFiltroPermitenDescarga() throws Exception {
        respuesta();
        Usuario usuario = new Usuario(); usuario.setId(77L); usuario.setCorreo("admin@pruebas.test");
        UserSecurity principal = new UserSecurity(usuario);
        String token = jwt.generateToken(principal);
        when(detalles.loadUserByUsername("admin@pruebas.test")).thenReturn(principal);
        when(sesiones.existsByTokenAndActivaTrueAndFechaExpiracionAfter(eq(token), any())).thenReturn(true);
        mvc.perform(get(URL + "/descargar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"));
        verify(servicio).consultar(eq(5L), argThat(u -> u.getId().equals(77L)), isNull(), isNull(), isNull());
        verify(sesiones, never()).save(any());
    }
    @Test void jwtFirmadoPeroSesionRevocadaEsRechazado() throws Exception {
        Usuario usuario = new Usuario(); usuario.setId(77L); usuario.setCorreo("admin@pruebas.test");
        UserSecurity principal = new UserSecurity(usuario);
        when(detalles.loadUserByUsername("admin@pruebas.test")).thenReturn(principal);
        mvc.perform(get(URL).header("Authorization", "Bearer " + jwt.generateToken(principal)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }
    @Test void erroresDeNegocioMantienenHttpYMensajeJsonNoCsv() throws Exception {
        for (HttpStatus codigo : List.of(HttpStatus.BAD_REQUEST, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND)) {
            doThrow(new ResponseStatusException(codigo, "Solicitud no permitida"))
                    .when(servicio).consultar(any(), any(), any(), any(), any());
            for (String sufijo : List.of("", "/descargar")) {
                mvc.perform(get(URL + sufijo).accept("text/csv").with(authentication(identidad())))
                        .andExpect(status().is(codigo.value())).andExpect(content().contentTypeCompatibleWith("application/json"))
                        .andExpect(jsonPath("$.mensaje").value("Solicitud no permitida"))
                        .andExpect(header().doesNotExist("Content-Disposition"));
            }
        }
    }
    @Test void falloDeLecturaEs500ControladoSinExponerSql() throws Exception {
        when(servicio.consultar(any(), any(), any(), any(), any())).thenThrow(new DataAccessResourceFailureException("SQL secreto"));
        mvc.perform(get(URL + "/descargar").with(authentication(identidad())))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.mensaje").value("No se pudo obtener el reporte. Intenta nuevamente más tarde"))
                .andExpect(content().string(not(containsString("SQL secreto"))));
    }
    @Test void falloDelFormateadorNoDevuelveArchivoParcial() throws Exception {
        respuesta(); doThrow(new IllegalStateException("Detalles internos")).when(csv).generar(any());
        mvc.perform(get(URL + "/descargar").with(authentication(identidad())))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.mensaje").value("No se pudo generar el archivo del reporte"))
                .andExpect(header().doesNotExist("Content-Disposition"));
    }
    @Test void preflightPermiteFrontendYExponeNombreDelArchivo() throws Exception {
        mvc.perform(options(URL + "/descargar").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Content-Disposition"));
        verifyNoInteractions(servicio);
    }
}
