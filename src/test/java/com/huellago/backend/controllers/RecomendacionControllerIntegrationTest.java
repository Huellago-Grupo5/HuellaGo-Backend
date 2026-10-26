package com.huellago.backend.controllers;

import com.huellago.backend.dtos.GeneracionRecomendacionesDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.JwtRequestFilter;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.SecurityConfiguration;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.RecomendacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecomendacionController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class, JwtUtilService.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class RecomendacionControllerIntegrationTest {

    private static final String URL = "/usuarios/me/recomendaciones/generar";
    @Autowired MockMvc mvc;
    @Autowired JwtUtilService jwtUtilService;
    @MockitoBean RecomendacionService recomendacionService;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean SesionRepository sesionRepository;

    @Test
    void jwtRealDeterminaElUsuarioAunqueElClienteEnvieOtroId() throws Exception {
        String token = autenticar(true);
        when(recomendacionService.generar(argThat(u -> u.getId().equals(22L))))
                .thenReturn(respuesta("GENERADAS", List.of()));

        mvc.perform(post(URL).param("usuarioId", "23")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"usuarioId\":23}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(22))
                .andExpect(jsonPath("$.metodo").value("REGLAS_DETERMINISTAS"));

        verify(recomendacionService).generar(argThat(u -> u.getId().equals(22L)));
        verify(recomendacionService, never()).generar(argThat(u -> u.getId().equals(23L)));
    }

    @Test
    void datosInsuficientesTienenRespuestaInformativa200() throws Exception {
        String token = autenticar(true);
        when(recomendacionService.generar(any()))
                .thenReturn(respuesta("DATOS_INSUFICIENTES", List.of("Transporte.medio: registra un medio")));

        mvc.perform(post(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DATOS_INSUFICIENTES"))
                .andExpect(jsonPath("$.datosFaltantes[0]").value("Transporte.medio: registra un medio"))
                .andExpect(jsonPath("$.recomendaciones").isEmpty());
    }

    @Test
    void sinJwtNoPermiteGenerar() throws Exception {
        mvc.perform(post(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(recomendacionService);
    }

    @Test
    void jwtManipuladoEsRechazado() throws Exception {
        mvc.perform(post(URL).header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(recomendacionService);
    }

    @Test
    void jwtDeSesionCerradaEsRechazado() throws Exception {
        String token = autenticar(false);
        mvc.perform(post(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(recomendacionService);
    }

    @Test
    void usuarioInexistenteDevuelve404() throws Exception {
        String token = autenticar(true);
        when(recomendacionService.generar(any())).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        mvc.perform(post(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private String autenticar(boolean sesionActiva) {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo("usuario22@huellago.com");
        UserSecurity principal = new UserSecurity(usuario);
        String token = jwtUtilService.generateToken(principal);
        when(userDetailsService.loadUserByUsername(principal.getUsername())).thenReturn(principal);
        when(sesionRepository.existsByTokenAndActivaTrueAndFechaExpiracionAfter(eq(token), any()))
                .thenReturn(sesionActiva);
        return token;
    }

    private GeneracionRecomendacionesDTO respuesta(String estado, List<String> faltantes) {
        return new GeneracionRecomendacionesDTO(22L, "REGLAS_DETERMINISTAS", estado,
                "Resultado de personalización", "HABITOS_REGISTRADOS", null, null,
                null, null, faltantes, List.of(), List.of());
    }
}
