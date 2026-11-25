package com.huellago.backend.controllers;

import com.huellago.backend.entities.HuellaCarbono;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.HabitoRepository;
import com.huellago.backend.repositories.HuellaCarbonoRepository;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.repositories.UsuarioRepository;
import com.huellago.backend.security.JwtRequestFilter;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.SecurityConfiguration;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.security.UsuarioAuthorizationService;
import com.huellago.backend.serviceimpl.HuellaCarbonoServiceImpl;
import com.huellago.backend.services.ReporteAmbientalService;
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
import java.math.BigDecimal;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HuellaCarbonoController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class,
        HuellaCarbonoServiceImpl.class, com.huellago.backend.serviceimpl.PoliticaMedicionInicial.class,
        com.huellago.backend.serviceimpl.ConsultaHuellaConfiguration.class,
        HuellaCarbonoErrorHandler.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class HuellaConsultaFechaControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @MockitoBean HuellaCarbonoRepository huellas;
    @MockitoBean HabitoRepository habitos;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean UsuarioAuthorizationService autorizacion;
    @MockitoBean ReporteAmbientalService reportes;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;

    @MockitoBean(name = "huellaConsultaClock") java.time.Clock reloj;

    @org.junit.jupiter.api.BeforeEach void relojFijo() {
        when(reloj.withZone(java.time.ZoneId.of("America/Lima"))).thenReturn(java.time.Clock.fixed(
                java.time.Instant.parse("2026-11-08T02:00:00Z"), java.time.ZoneId.of("America/Lima")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"2026-11-07", "2026-01-31", "2026-12-31", "2024-02-29"})
    void fechaExplicitaDevuelveUltimoYListaOrdenadaConLimitesYPropietario(String texto) throws Exception {
        var fecha = java.time.LocalDate.parse(texto);
        var datos = java.util.List.of(
                huella(5, 68, fecha.atTime(22, 35), "26.71"),
                huella(6, 68, fecha.atTime(22, 35), "40.06"),
                huella(4, 68, fecha.atStartOfDay(), "1.00"),
                huella(3, 68, fecha.minusDays(1).atTime(23, 59), "2.00"),
                huella(7, 68, fecha.plusDays(1).atStartOfDay(), "88.00"),
                huella(8, 99, fecha.atTime(22, 35), "99.00"));
        simularConsulta(datos);
        mvc.perform(consulta("diario", texto).param("usuarioId", "99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.co2Total").value(5.72))
                .andExpect(jsonPath("$.usuarioId").value(68))
                .andExpect(jsonPath("$.periodo").value("diario (promedio estimado)"))
                .andExpect(jsonPath("$.metodologia").value(org.hamcrest.Matchers.containsString("no es una medición")));
        mvc.perform(consulta("historial", texto))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].co2Total").value(40.06))
                .andExpect(jsonPath("$[1].co2Total").value(26.71))
                .andExpect(jsonPath("$[2].co2Total").value(1))
                .andExpect(jsonPath("$[2].periodo").value("semanal"));
        verify(huellas).findTopByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
                68L, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay());
        verify(huellas).findByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
                68L, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay());
        verify(huellas, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(habitos, usuarios);
    }

    @Test void fechaOmitidaUsaHoyEnLima() throws Exception {
        var fecha = java.time.LocalDate.of(2026, 11, 7);
        when(huellas.findTopByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
                68L, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay()))
                .thenReturn(huella(6, 68, fecha.atTime(20, 0), "40.06"));
        mvc.perform(consulta("diario", null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaCalculo").value("2026-11-07T20:00:00"));
    }

    @Test void historialSinFiltroConservaOperacionCompletaYOrdenRecibido() throws Exception {
        when(huellas.findByUsuario_IdOrderByFechaCalculoDescIdDesc(68L)).thenReturn(java.util.List.of(
                huella(6, 68, java.time.LocalDateTime.of(2026, 11, 7, 22, 35), "40.06"),
                huella(1, 68, java.time.LocalDateTime.of(2026, 10, 1, 10, 0), "10.50")));
        mvc.perform(consulta("historial", null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[1].co2Total").value(10.5));
        verify(huellas).findByUsuario_IdOrderByFechaCalculoDescIdDesc(68L);
        verifyNoMoreInteractions(huellas);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"2026-02-29", "2026-13-01", "2026-04-31", "07-11-2026", "2026-1-01", "", " ", "2026-11-07T00:00:00", "+999999999-12-31", "0000-01-01"})
    void fechaInvalidaEs400SinAccederADatos(String fecha) throws Exception {
        for (String endpoint : java.util.List.of("diario", "historial")) {
            mvc.perform(consulta(endpoint, fecha)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").exists()).andExpect(jsonPath("$.trace").doesNotExist());
        }
        verifyNoInteractions(huellas);
    }

    @Test void fechaSinDatosEs404EnDiarioY200VacioEnHistorial() throws Exception {
        mvc.perform(consulta("diario", "2026-11-07")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No hay c\u00e1lculos de huella de carbono registrados para la fecha seleccionada."));
        mvc.perform(consulta("historial", "2026-11-07")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("[]"));
    }

    @Test void sinJwtOJwtInvalido401EnAmbasOperaciones() throws Exception {
        for (String endpoint : java.util.List.of("diario", "historial")) {
            mvc.perform(get("/huella-carbono/" + endpoint).param("fecha", "2026-11-07"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/huella-carbono/" + endpoint).header("Authorization", "Bearer invalido"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(huellas);
    }

    @Test void consultasDeOtroUsuarioSoloUsanSuPropietario() throws Exception {
        mvc.perform(get("/huella-carbono/historial").param("fecha", "2026-11-07").param("usuarioId", "68")
                .with(authentication(autenticacion(99L)))).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("[]"));
        verify(huellas).findByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
                99L, java.time.LocalDate.of(2026, 11, 7).atStartOfDay(), java.time.LocalDate.of(2026, 11, 8).atStartOfDay());
        verifyNoMoreInteractions(huellas);
    }

    @Test void fallosDePersistenciaMantienen503SinDetallesInternos() throws Exception {
        when(huellas.findTopByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("SQL privado"));
        when(huellas.findByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("SQL privado"));
        for (String endpoint : java.util.List.of("diario", "historial")) {
            mvc.perform(consulta(endpoint, "2026-11-07")).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.mensaje").value("No se pudo procesar la huella en este momento"));
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder consulta(String endpoint, String fecha) {
        var peticion = get("/huella-carbono/" + endpoint).with(authentication(autenticacion(68L)));
        return fecha == null ? peticion : peticion.param("fecha", fecha);
    }
    private HuellaCarbono huella(long id, long propietario, java.time.LocalDateTime fecha, String total) {
        Usuario u = new Usuario(); u.setId(propietario);
        HuellaCarbono h = new HuellaCarbono(); h.setId(id); h.setUsuario(u); h.setFechaCalculo(fecha);
        h.setCo2Total(new BigDecimal(total)); return h;
    }
    private UsernamePasswordAuthenticationToken autenticacion(long id) {
        Usuario u = new Usuario(); u.setId(id); u.setCorreo("usuario" + id + "@example.invalid");
        UserSecurity p = new UserSecurity(u);
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    // Repositorio simulado: el parser PartTree se prueba por separado; pendiente ejecucion PostgreSQL.
    private void simularConsulta(java.util.List<HuellaCarbono> datos) {
        when(huellas.findTopByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(i -> filtrar(datos, i.getArgument(0), i.getArgument(1), i.getArgument(2)).stream().findFirst().orElse(null));
        when(huellas.findByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(i -> filtrar(datos, i.getArgument(0), i.getArgument(1), i.getArgument(2)));
    }
    private java.util.List<HuellaCarbono> filtrar(java.util.List<HuellaCarbono> datos, Long id,
            java.time.LocalDateTime inicio, java.time.LocalDateTime fin) {
        return datos.stream().filter(h -> h.getUsuario().getId().equals(id)
                        && !h.getFechaCalculo().isBefore(inicio) && h.getFechaCalculo().isBefore(fin))
                .sorted(java.util.Comparator.comparing(HuellaCarbono::getFechaCalculo).reversed()
                        .thenComparing(HuellaCarbono::getId, java.util.Comparator.reverseOrder())).toList();
    }
}
