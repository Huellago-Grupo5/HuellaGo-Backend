package com.huellago.backend.controllers;

import com.huellago.backend.dtos.InstitucionRespuestaDTO;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.*;
import com.huellago.backend.services.InstitucionService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InstitucionController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class, InstitucionErrorHandler.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class InstitucionControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean InstitucionService servicio;
    @MockitoBean JwtUtilService jwtUtilService;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean SesionRepository sesiones;

    @Test
    void creaUniversidadSoloParaElUsuarioJwt() throws Exception {
        when(servicio.registrar(eq(22L), any())).thenReturn(respuesta(TipoInstitucion.UNIVERSIDAD));
        mvc.perform(post("/instituciones").with(authentication(autenticacion(22L)))
                .contentType("application/json").content(json("UNIVERSIDAD")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/instituciones/7/administracion"))
                .andExpect(jsonPath("$.representanteId").value(22))
                .andExpect(jsonPath("$.rol").value("ADMIN_INSTITUCIONAL"));
        verify(servicio).registrar(eq(22L), any());
    }

    @Test
    void creaEmpresa() throws Exception {
        when(servicio.registrar(eq(22L), any())).thenReturn(respuesta(TipoInstitucion.EMPRESA));
        mvc.perform(post("/instituciones").with(authentication(autenticacion(22L)))
                .contentType("application/json").content(json("EMPRESA")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("EMPRESA"));
    }

    @Test
    void ignoraIntentoDeElegirOtroRepresentanteORol() throws Exception {
        when(servicio.registrar(eq(22L), any())).thenReturn(respuesta(TipoInstitucion.UNIVERSIDAD));
        mvc.perform(post("/instituciones").with(authentication(autenticacion(22L)))
                .contentType("application/json")
                .content(json("UNIVERSIDAD").replace("\"tipo\"", "\"representanteId\":99,\"rol\":\"ADMIN_GLOBAL\",\"tipo\"")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.representanteId").value(22));
        verify(servicio).registrar(eq(22L), any());
    }

    @Test
    void rechazaCamposFaltantesYFormatosInvalidos() throws Exception {
        mvc.perform(post("/instituciones").with(authentication(autenticacion(22L)))
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores").isArray());
        mvc.perform(post("/instituciones").with(authentication(autenticacion(22L)))
                .contentType("application/json").content(json("UNIVERSIDAD").replace("contacto@ejemplo.pe", "invalido")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/instituciones").with(authentication(autenticacion(22L)))
                .contentType("application/json").content(json("ASOCIACION")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").exists());
        verifyNoInteractions(servicio);
    }

    @Test
    void exigeJwtValidoParaRegistroYAdministracion() throws Exception {
        mvc.perform(post("/instituciones").contentType("application/json").content(json("UNIVERSIDAD")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/instituciones/7/administracion")).andExpect(status().isForbidden());
        mvc.perform(get("/instituciones/7/administracion").header("Authorization", "Bearer invalido"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    void consultaAdministrativaUsaSoloLaIdentidadAutenticada() throws Exception {
        when(servicio.consultarAdministracion(22L, 7L)).thenReturn(respuesta(TipoInstitucion.UNIVERSIDAD));
        mvc.perform(get("/instituciones/7/administracion").with(authentication(autenticacion(22L))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7));
        verify(servicio).consultarAdministracion(22L, 7L);
    }

    private UsernamePasswordAuthenticationToken autenticacion(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setCorreo("usuario@ejemplo.pe");
        UserSecurity principal = new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private InstitucionRespuestaDTO respuesta(TipoInstitucion tipo) {
        return new InstitucionRespuestaDTO(7L, tipo, "Universidad Verde", "PE", "20123456789",
                "contacto@ejemplo.pe", LocalDateTime.now(), 22L, RolInstitucional.ADMIN_INSTITUCIONAL);
    }

    private String json(String tipo) {
        return "{\"tipo\":\"" + tipo + "\",\"nombre\":\"Universidad Verde\","
                + "\"pais\":\"PE\",\"identificadorFiscal\":\"20123456789\","
                + "\"correoContacto\":\"contacto@ejemplo.pe\"}";
    }
}
