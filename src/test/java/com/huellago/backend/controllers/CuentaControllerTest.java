package com.huellago.backend.controllers;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.*;
import com.huellago.backend.services.AccesoCuentaService;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@WebMvcTest({UsuarioController.class,UsuarioPerfilController.class})
@Import({SecurityConfiguration.class,JwtRequestFilter.class,CuentaErrorHandler.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,ServletWebSecurityAutoConfiguration.class,SecurityFilterAutoConfiguration.class})
class CuentaControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UsuarioService usuarios; @MockitoBean SesionService sesiones; @MockitoBean AccesoCuentaService acceso;
    @MockitoBean JwtUtilService jwt; @MockitoBean UserDetailsService detalles; @MockitoBean SesionRepository sesionesRepo;
    String perfil="""
        {"nombres":"Carlos","apellidos":"Quispe","correo":"nuevo@example.invalid"}
        """;
    UsernamePasswordAuthenticationToken identidad() {
        var u=new Usuario();u.setId(22L);u.setCorreo("actual@example.invalid");var p=new UserSecurity(u);
        return new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities());
    }
    @ParameterizedTest @ValueSource(strings={"/auth/perfil","/usuarios/perfil"})
    void rutasDePerfilIgualesValidanNulosEspaciosYCorreo(String ruta) throws Exception {
        for(String body:new String[]{"{}","{\"nombres\":null}","{\"nombres\":\" \",\"apellidos\":\"Q1\",\"correo\":\"mal\"}"})
            mvc.perform(put(ruta).with(authentication(identidad())).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").exists());
        verifyNoInteractions(usuarios);
    }
    @ParameterizedTest @ValueSource(strings={"/auth/perfil","/usuarios/perfil"})
    void rutasDePerfilIgualesUsanPropietarioYCabecera(String ruta) throws Exception {
        when(usuarios.actualizarPerfil(eq("actual@example.invalid"),any())).thenReturn(new UsuarioRespuestaDTO(22L,"Carlos","Quispe","nuevo@example.invalid",0,1,true,true,LocalDateTime.now(),LocalDateTime.now()));
        mvc.perform(put(ruta).with(authentication(identidad())).contentType("application/json").content(perfil))
                .andExpect(status().isOk()).andExpect(header().string("X-Reauthentication-Required","true")).andExpect(jsonPath("$.id").value(22));
        verify(usuarios).actualizarPerfil(eq("actual@example.invalid"),any());
    }
    @ParameterizedTest @ValueSource(strings={"/auth/perfil","/usuarios/perfil"})
    void rutasDePerfilRequierenAutenticacion(String ruta) throws Exception {
        mvc.perform(put(ruta).contentType("application/json").content(perfil)).andExpect(status().isUnauthorized());verifyNoInteractions(usuarios);
    }
    @ParameterizedTest @ValueSource(strings={"/auth/register","/auth/login","/auth/recuperar-contrasena","/auth/restablecer-contrasena"})
    void endpointsPublicosValidanSolicitudVacia(String ruta) throws Exception {
        mvc.perform(post(ruta).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").exists());verifyNoInteractions(usuarios,acceso);
    }
    @Test void recuperacionNoExponeTokenNiCuenta() throws Exception {
        when(usuarios.solicitarRecuperacion(any())).thenReturn(new RecuperacionRespuestaDTO("Enlace enviado"));
        mvc.perform(post("/auth/recuperar-contrasena").contentType("application/json").content("{\"correo\":\"cuenta@example.invalid\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").doesNotExist()).andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.correo").doesNotExist());
    }
    @Test void loginDevuelveUnicamenteTokenIdYCorreo() throws Exception {
        when(acceso.login(any())).thenReturn(new TokenDTO("jwt-de-prueba",22L,"cuenta@example.invalid"));
        mvc.perform(post("/auth/login").contentType("application/json").content("{\"correo\":\"cuenta@example.invalid\",\"contrasena\":\"ClaveValida\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"token\":\"jwt-de-prueba\",\"id\":22,\"correo\":\"cuenta@example.invalid\"}", true))
                .andExpect(jsonPath("$.configuracionInicialCompleta").doesNotExist())
                .andExpect(jsonPath("$.categoriasPendientes").doesNotExist());
    }
    @ParameterizedTest @ValueSource(strings={"/auth/perfil","/usuarios/perfil"})
    void jwtInvalidoOExpiradoDevuelve401(String ruta) throws Exception {
        when(jwt.extractUsername("invalido")).thenThrow(new IllegalArgumentException("detalle privado"));
        when(jwt.extractUsername("expirado")).thenThrow(new io.jsonwebtoken.ExpiredJwtException(null,null,"detalle privado"));
        for (String token : new String[]{"invalido","expirado"}) {
            mvc.perform(put(ruta).header("Authorization","Bearer " + token).contentType("application/json").content(perfil))
                    .andExpect(status().isUnauthorized()).andExpect(content().string(""));
        }
        verifyNoInteractions(usuarios,sesionesRepo,detalles);
    }
    @ParameterizedTest @ValueSource(strings={"/auth/perfil","/usuarios/perfil"})
    void jwtRevocadoTrasLogoutOCambioDeCorreoDevuelve401(String ruta) throws Exception {
        var principal=(UserSecurity) identidad().getPrincipal();
        when(jwt.extractUsername("revocado")).thenReturn(principal.getUsername());
        when(detalles.loadUserByUsername(principal.getUsername())).thenReturn(principal);
        when(jwt.validateToken("revocado",principal)).thenReturn(true);
        when(sesionesRepo.existsByTokenAndActivaTrueAndFechaExpiracionAfter(eq("revocado"),any())).thenReturn(false);
        mvc.perform(put(ruta).header("Authorization","Bearer revocado").contentType("application/json").content(perfil))
                .andExpect(status().isUnauthorized());
        // Tras cambiar el correo, el subject antiguo tambien puede dejar de existir.
        when(detalles.loadUserByUsername(principal.getUsername())).thenThrow(
                new org.springframework.security.core.userdetails.UsernameNotFoundException("detalle privado"));
        mvc.perform(put(ruta).header("Authorization","Bearer revocado").contentType("application/json").content(perfil))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(usuarios);
    }
    @ParameterizedTest @ValueSource(strings={"/auth/perfil","/usuarios/perfil"})
    void jwtValidoPermitePerfilYSinPermisosConserva403(String ruta) throws Exception {
        var principal=(UserSecurity) identidad().getPrincipal();
        when(jwt.extractUsername("valido")).thenReturn(principal.getUsername());
        when(detalles.loadUserByUsername(principal.getUsername())).thenReturn(principal);
        when(jwt.validateToken("valido",principal)).thenReturn(true);
        when(sesionesRepo.existsByTokenAndActivaTrueAndFechaExpiracionAfter(eq("valido"),any())).thenReturn(true);
        when(usuarios.actualizarPerfil(eq(principal.getUsername()),any())).thenReturn(
                new UsuarioRespuestaDTO(22L,"Carlos","Quispe","nuevo@example.invalid",0,1,true,true,LocalDateTime.now(),LocalDateTime.now()));
        mvc.perform(put(ruta).header("Authorization","Bearer valido").contentType("application/json").content(perfil))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(22));
        when(usuarios.actualizarPerfil(eq(principal.getUsername()),any())).thenThrow(
                new org.springframework.security.access.AccessDeniedException("detalle privado"));
        mvc.perform(put(ruta).header("Authorization","Bearer valido").contentType("application/json").content(perfil))
                .andExpect(status().isForbidden());
    }
    @Test void falloDeCredencialesEs401SinDetalles() throws Exception {
        when(acceso.login(any())).thenThrow(new org.springframework.security.authentication.BadCredentialsException("detalle privado"));
        mvc.perform(post("/auth/login").contentType("application/json").content("{\"correo\":\"cuenta@example.invalid\",\"contrasena\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.mensaje").value("Credenciales inválidas"));
    }
    @Test void registroValidoEs201() throws Exception {
        mvc.perform(post("/auth/register").contentType("application/json").content("{\"nombres\":\"Carlos\",\"apellidos\":\"Quispe\",\"correo\":\" CUENTA@EXAMPLE.INVALID \",\"contrasena\":\"Registro!2026Seguro\"}"))
                .andExpect(status().isCreated());verify(usuarios).registrar(argThat(d->d.getCorreo().equals("cuenta@example.invalid")));
    }
    @Test void smtpAusenteEs503SinFalsaConfirmacionNiToken() throws Exception {
        when(usuarios.solicitarRecuperacion(any())).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"La recuperación por correo no está disponible; no se confirmó ningún envío"));
        mvc.perform(post("/auth/recuperar-contrasena").contentType("application/json").content("{\"correo\":\"cuenta@example.invalid\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.mensaje").value("La recuperación por correo no está disponible; no se confirmó ningún envío"));
    }
}
