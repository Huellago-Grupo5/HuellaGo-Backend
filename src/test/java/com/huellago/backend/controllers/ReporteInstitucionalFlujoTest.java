package com.huellago.backend.controllers;

import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.repositories.projections.*;
import com.huellago.backend.security.*;
import com.huellago.backend.serviceimpl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.*;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Integración MVC + seguridad + autorización + servicio + CSV reales; solo la persistencia se sustituye. */
@WebMvcTest(ReporteInstitucionalController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class, ReporteInstitucionalServiceImpl.class,
        AutorizacionInstitucionalService.class, ReporteInstitucionalCsv.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class ReporteInstitucionalFlujoTest {
    @Autowired MockMvc mvc;
    @MockitoBean InstitucionRepository instituciones;
    @MockitoBean UsuarioInstitucionRepository afiliaciones;
    @MockitoBean RetoRepository retos;
    @MockitoBean UsuarioRetoRepository participaciones;
    @MockitoBean ResultadoRetoColectivoRepository resultados;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;
    static final String URL = "/instituciones/1/reportes/desempeno-ambiental";

    @BeforeEach void datos() {
        Institucion institucion = new Institucion(); ReflectionTestUtils.setField(institucion, "id", 1L);
        institucion.setNombre("Institución de prueba");
        when(instituciones.findById(1L)).thenReturn(Optional.of(institucion));
        when(afiliaciones.existsByUsuario_IdAndInstitucion_IdAndRol(1L, 1L, RolInstitucional.ADMIN_INSTITUCIONAL)).thenReturn(true);
        CampanaReporteVista campana = mock(CampanaReporteVista.class);
        when(campana.getId()).thenReturn(6); when(campana.getTitulo()).thenReturn("Reciclaje en Equipo");
        when(campana.getDescripcion()).thenReturn("Reducir residuos"); when(campana.getActivo()).thenReturn(true);
        when(campana.getFechaInicio()).thenReturn(LocalDate.now()); when(campana.getFechaFin()).thenReturn(LocalDate.now().plusDays(10));
        when(retos.buscarParaReporte(eq(1L), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(campana)));
        ParticipacionReporteVista avance = mock(ParticipacionReporteVista.class);
        when(avance.getRetoId()).thenReturn(6); when(avance.getParticipantes()).thenReturn(1L);
        when(avance.getSumaProgreso()).thenReturn(new BigDecimal("50"));
        when(participaciones.resumirParaReporte(List.of(6))).thenReturn(List.of(avance));
        when(resultados.buscarParaReporte(List.of(6))).thenReturn(List.of());
        TotalesParticipacionVista totales = mock(TotalesParticipacionVista.class);
        when(totales.getTotal()).thenReturn(1L); when(totales.getUsuarios()).thenReturn(1L);
        when(participaciones.totalesParaReporte(eq(1L), any(), any(), any())).thenReturn(totales);
    }
    UsernamePasswordAuthenticationToken identidad(long id) {
        Usuario u = new Usuario(); u.setId(id); UserSecurity p = new UserSecurity(u);
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }
    @Test void datosLleganDelRepositorioAlJsonYCsvSinPerderElProgreso() throws Exception {
        mvc.perform(get(URL).with(authentication(identidad(1L))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.campanas[0].id").value(6))
                .andExpect(jsonPath("$.campanas[0].progresoColectivo").value(50))
                .andExpect(jsonPath("$.resumen.totalParticipacionesRegistradas").value(1))
                .andExpect(jsonPath("$.resumen.progresoGeneralPonderado").value(50));
        mvc.perform(get(URL + "/descargar").with(authentication(identidad(1L))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"Reciclaje en Equipo\"")))
                .andExpect(content().string(containsString("\"50.00\"")));
        verify(retos, never()).save(any()); verify(participaciones, never()).save(any()); verify(resultados, never()).save(any());
    }
    @Test void miembroYAdministradorAjenoSonRechazadosEnAmbasRutas() throws Exception {
        for (long usuario : List.of(2L, 3L)) for (String sufijo : List.of("", "/descargar")) {
            mvc.perform(get(URL + sufijo).with(authentication(identidad(usuario))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").exists());
        }
        verifyNoInteractions(retos, participaciones, resultados);
    }
    @Test void filtroCampanaAjena404YPeriodoInvertido400() throws Exception {
        mvc.perform(get(URL + "/descargar?campanaId=7").with(authentication(identidad(1L))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value("Campaña no encontrada en esta institución"));
        mvc.perform(get(URL + "?desde=2026-12-31&hasta=2026-01-01").with(authentication(identidad(1L))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").exists());
        verify(participaciones, never()).resumirParaReporte(any());
    }
    @Test void institucionInexistenteEs404Controlado() throws Exception {
        mvc.perform(get("/instituciones/999/reportes/desempeno-ambiental").with(authentication(identidad(1L))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value("Institución no encontrada"));
        verifyNoInteractions(afiliaciones, retos, participaciones, resultados);
    }
    @ParameterizedTest
    @ValueSource(strings = {"", "?desde=2026-11-02", "?hasta=2026-11-05",
            "?desde=2026-11-02&hasta=2026-11-05", "?campanaId=6",
            "?desde=2026-11-02&campanaId=6", "?hasta=2026-11-05&campanaId=6",
            "?desde=2026-11-02&hasta=2026-11-05&campanaId=6"})
    void filtrosOpcionalesFuncionanEnJsonYCsvSinCambiarContratos(String parametros) throws Exception {
        when(retos.existsByIdAndInstitucion_IdAndTipo(6, 1L, TipoReto.COLECTIVO)).thenReturn(true);
        mvc.perform(get(URL + parametros).with(authentication(identidad(1L))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.institucion.id").value(1))
                .andExpect(jsonPath("$.campanas[0].id").value(6))
                .andExpect(jsonPath("$.campanas[0].progresoColectivo").value(50));
        mvc.perform(get(URL + "/descargar" + parametros).with(authentication(identidad(1L))))
                .andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().string(containsString("\"50.00\"")));
        LocalDate d = parametros.contains("desde=") ? LocalDate.of(2026, 11, 2) : null;
        LocalDate h = parametros.contains("hasta=") ? LocalDate.of(2026, 11, 5) : null;
        Integer id = parametros.contains("campanaId=") ? 6 : null;
        verify(retos, times(2)).buscarParaReporte(eq(1L), eq(d), eq(h), eq(id), any());
        verify(participaciones, times(2)).totalesParaReporte(1L, d, h, id);
    }
}
