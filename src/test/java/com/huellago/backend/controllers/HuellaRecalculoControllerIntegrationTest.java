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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HuellaCarbonoController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class,
        HuellaCarbonoServiceImpl.class, HuellaCarbonoErrorHandler.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class HuellaRecalculoControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean HuellaCarbonoRepository huellas;
    @MockitoBean HabitoRepository habitos;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean UsuarioAuthorizationService autorizacion;
    @MockitoBean ReporteAmbientalService reportes;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;

    @Test
    void recalculaSoloParaElJwtYPermiteConsultarElResultado() throws Exception {
        Usuario usuario = usuario22();
        when(usuarios.findById(22L)).thenReturn(Optional.of(usuario));
        when(usuarios.existsById(22L)).thenReturn(true);
        when(habitos.findByUsuario_Id(22L)).thenReturn(completos(new BigDecimal("80")));
        AtomicReference<HuellaCarbono> guardada = new AtomicReference<>();
        when(huellas.save(any())).thenAnswer(invocacion -> {
            HuellaCarbono huella = invocacion.getArgument(0);
            guardada.set(huella);
            return huella;
        });
        when(huellas.findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(22L))
                .thenAnswer(invocacion -> guardada.get());
        when(huellas.findByUsuario_IdOrderByFechaCalculoDescIdDesc(22L))
                .thenAnswer(invocacion -> List.of(guardada.get()));

        mvc.perform(post("/huella-carbono/recalcular")
                        .with(authentication(autenticacion(usuario))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(22))
                .andExpect(jsonPath("$.co2Transporte").value(4))
                .andExpect(jsonPath("$.co2Total").value(8.5));

        mvc.perform(get("/huella-carbono/22").with(authentication(autenticacion(usuario))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.co2Total").value(8.5));
        mvc.perform(get("/huella-carbono/historial")
                        .with(authentication(autenticacion(usuario))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].co2Total").value(8.5));

        verify(habitos).findByUsuario_Id(22L);
        verify(habitos, never()).findByUsuario_Id(99L);
        verify(huellas).save(any(HuellaCarbono.class));
    }

    @Test
    void incompletoDevuelve400SinGuardar() throws Exception {
        Usuario usuario = usuario22();
        when(usuarios.findById(22L)).thenReturn(Optional.of(usuario));
        when(habitos.findByUsuario_Id(22L)).thenReturn(List.of(
                habito("Transporte", "medio", BigDecimal.ZERO, "Bus")));
        mvc.perform(post("/huella-carbono/recalcular")
                        .with(authentication(autenticacion(usuario))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
        verify(huellas, never()).save(any());
    }

    @Test
    void falloDeBaseDeDatosDevuelve503Controlado() throws Exception {
        Usuario usuario = usuario22();
        when(usuarios.findById(22L)).thenReturn(Optional.of(usuario));
        when(habitos.findByUsuario_Id(22L)).thenReturn(completos(new BigDecimal("80")));
        when(huellas.save(any())).thenThrow(new DataIntegrityViolationException("dato interno"));
        mvc.perform(post("/huella-carbono/recalcular")
                        .with(authentication(autenticacion(usuario))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("No se pudo procesar la huella en este momento"));
    }

    @Test
    void sinJwtOConTokenInvalidoNoAccede() throws Exception {
        mvc.perform(post("/huella-carbono/recalcular")).andExpect(status().isForbidden());
        mvc.perform(post("/huella-carbono/recalcular")
                        .header("Authorization", "Bearer invalido"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(usuarios, habitos, huellas);
    }

    private Usuario usuario22() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo("usuario22@ejemplo.pe");
        return usuario;
    }

    private UsernamePasswordAuthenticationToken autenticacion(Usuario usuario) {
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private List<Habito> completos(BigDecimal km) {
        return List.of(
                habito("Transporte", "medio", BigDecimal.ZERO, "Bus"),
                habito("Transporte", "kmSemana", km, "km/semana"),
                habito("Transporte", "diasSemana", new BigDecimal("5"), "dias/semana"),
                habito("Energia", "vivienda", BigDecimal.ZERO, "Casa"),
                habito("Energia", "personas", new BigDecimal("3"), "personas"),
                habito("Energia", "fuente", BigDecimal.ZERO, "Electricidad"),
                habito("Alimentacion", "tipo", BigDecimal.ZERO, "Mixta"),
                habito("Residuos", "plasticos", BigDecimal.ZERO, "Medio"),
                habito("Residuos", "reciclas", BigDecimal.ONE, "boolean"));
    }

    private Habito habito(String categoria, String nombre, BigDecimal valor, String unidad) {
        CategoriaHabito tipo = new CategoriaHabito();
        tipo.setNombre(categoria);
        Habito habito = new Habito();
        habito.setCategoria(tipo);
        habito.setNombre(nombre);
        habito.setValor(valor);
        habito.setUnidad(unidad);
        return habito;
    }
}
