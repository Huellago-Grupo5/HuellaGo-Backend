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
class HuellaEquivalenciasControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @MockitoBean HuellaCarbonoRepository huellas;
    @MockitoBean HabitoRepository habitos;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean UsuarioAuthorizationService autorizacion;
    @MockitoBean ReporteAmbientalService reportes;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"-1", "999999999999999999999"})
    void datosHistoricosInvalidosDevuelven404Controlado(String total) throws Exception {
        preparar(22L, total == null ? null : new BigDecimal(total));
        mvc.perform(get("/huella-carbono/equivalencias").with(authentication(autenticacion(22L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(total != null && !total.startsWith("-")
                        ? "La huella de carbono excede el rango admitido para mostrar equivalencias"
                        : "No hay una huella de carbono v\u00e1lida para mostrar equivalencias"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test void ceroConservaCamposDelContratoYAclaraLasEquivalencias() throws Exception {
        preparar(22L, BigDecimal.ZERO);
        mvc.perform(get("/huella-carbono/equivalencias").with(authentication(autenticacion(22L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(22))
                .andExpect(jsonPath("$.co2Total").value(0))
                .andExpect(jsonPath("$.arboles").value(0))
                .andExpect(jsonPath("$.bicicletaKm").value(0))
                .andExpect(jsonPath("$.reciclajeKg").value(0))
                .andExpect(jsonPath("$.periodoHuella").value("semanal"))
                .andExpect(jsonPath("$.explicacionArboles").exists())
                .andExpect(jsonPath("$.explicacionBicicletaKm").exists())
                .andExpect(jsonPath("$.explicacionReciclajeKg").exists());
    }

    @Test void usaSoloUsuarioJwtYSeparaResultadosDeDosUsuarios() throws Exception {
        preparar(22L, BigDecimal.ONE); preparar(23L, BigDecimal.TEN);
        mvc.perform(get("/huella-carbono/equivalencias").param("usuarioId", "23")
                        .with(authentication(autenticacion(22L))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuarioId").value(22))
                .andExpect(jsonPath("$.bicicletaKm").value(4));
        verify(huellas, never()).findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(23L);
        mvc.perform(get("/huella-carbono/equivalencias").with(authentication(autenticacion(23L))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuarioId").value(23))
                .andExpect(jsonPath("$.bicicletaKm").value(41));
        verifyNoInteractions(habitos);
    }

    @Test void sinJwtOConJwtInvalidoDevuelve401() throws Exception {
        mvc.perform(get("/huella-carbono/equivalencias")).andExpect(status().isUnauthorized());
        mvc.perform(get("/huella-carbono/equivalencias").header("Authorization", "Bearer invalido"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(huellas);
    }

    @Test void ausenciaDeRegistroDevuelve404() throws Exception {
        mvc.perform(get("/huella-carbono/equivalencias").with(authentication(autenticacion(22L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Huella de carbono no encontrada"));
    }

    @Test void infraestructuraConserva503SinDetallesInternos() throws Exception {
        when(huellas.findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(22L))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("detalle SQL interno"));
        mvc.perform(get("/huella-carbono/equivalencias").with(authentication(autenticacion(22L))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("No se pudo procesar la huella en este momento"));
    }

    private void preparar(long id, BigDecimal total) {
        Usuario usuario = new Usuario(); usuario.setId(id);
        HuellaCarbono h = new HuellaCarbono(); h.setUsuario(usuario); h.setCo2Total(total);
        when(huellas.findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(id)).thenReturn(h);
    }
    private UsernamePasswordAuthenticationToken autenticacion(long id) {
        Usuario u = new Usuario(); u.setId(id); u.setCorreo("usuario" + id + "@example.invalid");
        UserSecurity p = new UserSecurity(u);
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }
}
