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
    @org.junit.jupiter.api.BeforeEach void simularBloqueoUsuario() { when(usuarios.bloquearParaRecomendaciones(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(i -> Optional.of(i.getArgument(0))); }

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

    @Test
    void consultaYActualizaIdObtenidoYRechazaPropietarioAjeno() throws Exception {
        var u = propietario(); var h = habito(u);
        when(habitos.findByUsuario_IdOrderByIdAsc(22L)).thenReturn(java.util.List.of(h));
        var consulta = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/habitos")
                .param("usuarioId", "99").with(authentication(autenticacion(u))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].categoriaNombre").value("Transporte"))
                .andExpect(jsonPath("$[0].usuarioId").value(22)).andReturn();
        long id = new tools.jackson.databind.ObjectMapper().readTree(consulta.getResponse().getContentAsString()).get(0).get("id").asLong();
        when(habitos.findById(id)).thenReturn(Optional.of(h)); when(habitos.save(h)).thenReturn(h);
        mvc.perform(put("/habitos/" + id).with(authentication(autenticacion(u)))
                .contentType("application/json").content("{\"nombre\":\"diasSemana\",\"valor\":6,\"unidad\":\"dias/semana\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valor").value(6));
        var ajeno = propietario(); ajeno.setId(99L); h.setUsuario(ajeno);
        mvc.perform(put("/habitos/" + id).with(authentication(autenticacion(u)))
                .contentType("application/json").content("{\"nombre\":\"diasSemana\",\"valor\":7,\"unidad\":\"dias/semana\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").exists());
        assertEquals(new BigDecimal("6"), h.getValor());
        verify(habitos, never()).findByUsuario_IdOrderByIdAsc(99L);
    }

    @Test void consultaFalloPersistenciaControlado() throws Exception {
        when(habitos.findByUsuario_IdOrderByIdAsc(22L)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("interno"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/habitos")
                .with(authentication(autenticacion(propietario()))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.mensaje").exists());
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
