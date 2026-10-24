package com.huellago.backend.controllers;

import com.huellago.backend.entities.CategoriaHabito;
import com.huellago.backend.entities.Habito;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.CategoriaHabitoRepository;
import com.huellago.backend.repositories.HabitoRepository;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.repositories.UsuarioRepository;
import com.huellago.backend.security.JwtRequestFilter;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.SecurityConfiguration;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.security.UsuarioAuthorizationService;
import com.huellago.backend.serviceimpl.HabitoServiceImpl;
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
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HabitoController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class,
        HabitoServiceImpl.class, HabitoErrorHandler.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class HabitoDiasSemanaIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean HabitoRepository habitos;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean CategoriaHabitoRepository categorias;
    @MockitoBean UsuarioAuthorizationService autorizacion;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;

    @Test
    void putHabito64ConDiasSemana9Devuelve400SinCambiarValorNiFecha() throws Exception {
        Usuario propietario = propietario();
        Habito almacenado = habito(propietario);
        LocalDateTime fechaOriginal = almacenado.getFechaActualizacion();
        when(habitos.findById(64L)).thenReturn(Optional.of(almacenado));

        mvc.perform(put("/habitos/64").with(authentication(autenticacion(propietario)))
                        .contentType("application/json")
                        .content("""
                                {"nombre":"diasSemana","valor":9,"unidad":"dias/semana"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("diasSemana debe ser un número entero entre 1 y 7"));

        assertEquals(new BigDecimal("5"), almacenado.getValor());
        assertEquals(fechaOriginal, almacenado.getFechaActualizacion());
        verify(habitos, never()).save(any(Habito.class));
    }

    @Test
    void putValidoMantieneLaActualizacion() throws Exception {
        Usuario propietario = propietario();
        Habito almacenado = habito(propietario);
        when(habitos.findById(64L)).thenReturn(Optional.of(almacenado));
        when(habitos.save(almacenado)).thenReturn(almacenado);

        mvc.perform(put("/habitos/64").with(authentication(autenticacion(propietario)))
                        .contentType("application/json")
                        .content("""
                                {"nombre":"diasSemana","valor":6,"unidad":"dias/semana"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(6));

        assertEquals(new BigDecimal("6"), almacenado.getValor());
        verify(habitos).save(almacenado);
    }

    private Usuario propietario() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo("usuario22@ejemplo.pe");
        return usuario;
    }

    private Habito habito(Usuario propietario) {
        CategoriaHabito categoria = new CategoriaHabito();
        categoria.setId(12L);
        categoria.setNombre("Transporte");
        Habito habito = new Habito();
        habito.setId(64L);
        habito.setUsuario(propietario);
        habito.setCategoria(categoria);
        habito.setNombre("diasSemana");
        habito.setValor(new BigDecimal("5"));
        habito.setUnidad("dias/semana");
        habito.setFechaRegistro(LocalDateTime.of(2026, 10, 1, 12, 0));
        habito.setFechaActualizacion(LocalDateTime.of(2026, 10, 20, 12, 0));
        return habito;
    }

    private UsernamePasswordAuthenticationToken autenticacion(Usuario usuario) {
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
