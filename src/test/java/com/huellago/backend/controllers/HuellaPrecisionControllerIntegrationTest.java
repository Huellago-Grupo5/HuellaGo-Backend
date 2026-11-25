package com.huellago.backend.controllers;

import com.huellago.backend.entities.CategoriaHabito;
import com.huellago.backend.entities.Habito;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HuellaCarbonoController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class,
        HuellaCarbonoServiceImpl.class, com.huellago.backend.serviceimpl.PoliticaMedicionInicial.class,
        com.huellago.backend.serviceimpl.ConsultaHuellaConfiguration.class,
        HuellaCarbonoErrorHandler.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class HuellaPrecisionControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @MockitoBean HuellaCarbonoRepository huellas;
    @MockitoBean HabitoRepository habitos;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean UsuarioAuthorizationService autorizacion;
    @MockitoBean ReporteAmbientalService reportes;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;

    @Autowired com.huellago.backend.serviceimpl.PoliticaMedicionInicial politica;

    @org.junit.jupiter.api.BeforeEach void factorSoloDePrueba() {
        org.springframework.test.util.ReflectionTestUtils.setField(politica, "factorElectricidad", "0.23825");
        org.springframework.test.util.ReflectionTestUtils.setField(politica, "referenciaElectricidad", "Factor provisional exclusivo de pruebas");
    }
    @org.junit.jupiter.api.AfterEach void restaurarPoliticaDelContextoDePrueba() {
        org.springframework.test.util.ReflectionTestUtils.setField(politica, "factorElectricidad", "");
        org.springframework.test.util.ReflectionTestUtils.setField(politica, "referenciaElectricidad", "");
    }

    @Test void calcularYRecalcularDevuelvenMismosImportesQueLaEntidadYLasConsultas() throws Exception {
        Usuario u = new Usuario(); u.setId(68L); u.setCorreo("prueba68@example.invalid");
        when(usuarios.findById(68L)).thenReturn(Optional.of(u));
        when(usuarios.bloquearParaRecomendaciones(68L)).thenReturn(Optional.of(68L));
        var datos = List.of(
                habito("Transporte", "medio", "0", "Bus"),
                habito("Transporte", "kmSemana", "80", "km/semana"),
                habito("Transporte", "diasSemana", "5", "dias/semana"),
                habito("Energia", "vivienda", "0", "Casa"),
                habito("Energia", "personas", "3", "personas"),
                habito("Energia", "fuente", "0", "Electricidad"),
                habito("Energia", "consumoKwhDiario", "12", "kWh/dia"),
                habito("Alimentacion", "tipo", "0", "Mixta"),
                habito("Residuos", "plasticos", "0", "Medio"),
                habito("Residuos", "reciclas", "1", "boolean"));
        when(habitos.findByUsuario_Id(68L)).thenReturn(datos);
        var guardadas = new java.util.ArrayList<HuellaCarbono>();
        when(huellas.save(any())).thenAnswer(i -> {
            HuellaCarbono h = i.getArgument(0); h.setId((long) guardadas.size()+1); guardadas.add(h); return h;
        });
        when(huellas.findByUsuario_IdOrderByFechaCalculoDescIdDesc(68L)).thenAnswer(i -> guardadas.reversed());
        for (String endpoint : List.of("calcular", "recalcular")) {
            if (endpoint.equals("recalcular")) datos.get(6).setValor(new BigDecimal("20"));
            BigDecimal total = new BigDecimal(endpoint.equals("calcular") ? "26.71" : "40.06");
            BigDecimal energia = new BigDecimal(endpoint.equals("calcular") ? "20.01" : "33.36");
            mvc.perform(post("/huella-carbono/" + endpoint).with(authentication(autenticacion(u))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.co2Total").value(total.doubleValue()))
                    .andExpect(jsonPath("$.co2Energia").value(energia.doubleValue()))
                    .andExpect(jsonPath("$.co2Transporte").value(4))
                    .andExpect(jsonPath("$.co2Alimentacion").value(2))
                    .andExpect(jsonPath("$.co2Residuos").value(0.7))
                    .andExpect(jsonPath("$.nivelImpacto").value(endpoint.equals("calcular") ? "BAJO" : "MEDIO"))
                    .andExpect(jsonPath("$.periodo").value("semanal"));
            var h = guardadas.getLast();
            org.junit.jupiter.api.Assertions.assertEquals(total, h.getCo2Total());
            org.junit.jupiter.api.Assertions.assertEquals(energia, h.getCo2Energia());
            org.junit.jupiter.api.Assertions.assertEquals(total, h.getCo2Transporte().add(h.getCo2Energia())
                    .add(h.getCo2Alimentacion()).add(h.getCo2Residuos()));
            mvc.perform(get("/huella-carbono/historial").with(authentication(autenticacion(u))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].co2Total").value(total.doubleValue()));
        }
        verify(huellas, times(2)).save(any());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("26.71"), guardadas.getFirst().getCo2Total());
        verify(habitos, never()).save(any());
    }
    private Habito habito(String categoria, String nombre, String valor, String unidad) {
        CategoriaHabito c = new CategoriaHabito(); c.setNombre(categoria);
        Habito h = new Habito(); h.setCategoria(c); h.setNombre(nombre);
        h.setValor(new BigDecimal(valor)); h.setUnidad(unidad); return h;
    }
    private UsernamePasswordAuthenticationToken autenticacion(Usuario u) {
        UserSecurity p = new UserSecurity(u);
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }
}
