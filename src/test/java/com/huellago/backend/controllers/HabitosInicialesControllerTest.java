package com.huellago.backend.controllers;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.*;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.*;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({HabitoController.class,HuellaCarbonoController.class})
@Import({SecurityConfiguration.class,JwtRequestFilter.class,UsuarioAuthorizationService.class,
        HabitoErrorHandler.class,HuellaCarbonoErrorHandler.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,ServletWebSecurityAutoConfiguration.class,SecurityFilterAutoConfiguration.class})
class HabitosInicialesControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean HabitoService habitos;
    @MockitoBean HuellaCarbonoService huellas;
    @MockitoBean ReporteAmbientalService reportes;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesiones;

    UsernamePasswordAuthenticationToken identidad() {
        var usuario=new Usuario(); usuario.setId(9001L); usuario.setCorreo("hu08@example.invalid");
        var principal=new UserSecurity(usuario);
        return new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities());
    }
    String datos(String ruta,long id) {
        return switch (ruta) {
            case "transporte" -> "{\"usuarioId\":"+id+",\"medio\":\"Bus\",\"kmSemana\":120,\"diasSemana\":5}";
            case "energia" -> "{\"usuarioId\":"+id+",\"vivienda\":\"Casa\",\"personas\":3,\"fuente\":\"Electricidad\"}";
            default -> "{\"usuarioId\":"+id+",\"tipo\":\"Mixta\",\"plasticos\":\"Medio\",\"reciclas\":true}";
        };
    }
    @ParameterizedTest @ValueSource(strings={"transporte","energia","alimentacion-residuos"})
    void noPermiteRegistrarParaOtroUsuario(String ruta) throws Exception {
        mvc.perform(post("/habitos/"+ruta).with(authentication(identidad())).contentType("application/json").content(datos(ruta,9002L)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(habitos);
    }
    @ParameterizedTest @ValueSource(strings={"transporte","energia","alimentacion-residuos"})
    void requiereJwt(String ruta) throws Exception {
        mvc.perform(post("/habitos/"+ruta).contentType("application/json").content(datos(ruta,9001L)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(habitos);
    }
    @ParameterizedTest @ValueSource(strings={"transporte","energia","alimentacion-residuos"})
    void validaFormularioIncompleto(String ruta) throws Exception {
        mvc.perform(post("/habitos/"+ruta).with(authentication(identidad())).contentType("application/json").content("{\"usuarioId\":9001}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores").isArray());
        verifyNoInteractions(habitos);
    }
    @Test void postTransporteRechazaDiasMayoresASiete() throws Exception {
        mvc.perform(post("/habitos/transporte").with(authentication(identidad())).contentType("application/json")
                        .content(datos("transporte",9001L).replace("\"diasSemana\":5","\"diasSemana\":8")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(habitos);
    }
    @Test void alimentacionInformaPendientesEnSuPropiaRespuesta() throws Exception {
        var respuesta=new AlimentacionResiduosRespuestaDTO(9001L,3L,4L,"Mixta","Medio",true);
        respuesta.setCategoriasPendientes(List.of("Transporte","Energía"));
        when(habitos.registrarAlimentacionResiduos(any())).thenReturn(respuesta);
        mvc.perform(post("/habitos/alimentacion-residuos").with(authentication(identidad())).contentType("application/json").content(datos("alimentacion-residuos",9001L)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.configuracionInicialCompleta").value(false))
                .andExpect(jsonPath("$.categoriasPendientes.length()").value(2));
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "transporte,transporte", "energia,energía", "alimentacion-residuos,alimentación", "alimentacion-residuos,residuos"
    })
    void duplicadosConservan409YJsonConMensajeAmigable(String ruta, String categoria) throws Exception {
        String mensaje = "Ya registraste tus hábitos de " + categoria
                + ". Puedes actualizar esta información cuando lo necesites.";
        var error = new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, mensaje);
        switch (ruta) {
            case "transporte" -> when(habitos.registrarTransporte(any())).thenThrow(error);
            case "energia" -> when(habitos.registrarEnergia(any())).thenThrow(error);
            default -> when(habitos.registrarAlimentacionResiduos(any())).thenThrow(error);
        }
        mvc.perform(post("/habitos/" + ruta).with(authentication(identidad()))
                        .contentType("application/json").content(datos(ruta,9001L)))
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"mensaje\":\"" + mensaje + "\"}", true));
    }

    @Test void calculoUsaExclusivamenteUsuarioAutenticado() throws Exception {
        mvc.perform(post("/huella-carbono/calcular").param("usuarioId","9002").with(authentication(identidad())))
                .andExpect(status().isOk());
        verify(huellas).calcularHuellaInicial(9001L); verifyNoMoreInteractions(huellas);
    }
}
