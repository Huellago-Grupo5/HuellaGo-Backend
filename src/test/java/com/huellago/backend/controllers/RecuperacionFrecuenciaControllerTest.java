package com.huellago.backend.controllers;

import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.*;
import com.huellago.backend.serviceimpl.*;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.userdetails.UserDetailsService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=UsuarioController.class,properties={"huellago.recovery.rate.ip-limit=2"})
@Import({SecurityConfiguration.class,JwtRequestFilter.class,CuentaErrorHandler.class,
        RecuperacionContrasenaServiceImpl.class,LimiteRecuperacionServiceImpl.class,
        OrigenRecuperacion.class,LimiteRecuperacionConfiguration.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class,ServletWebSecurityAutoConfiguration.class,SecurityFilterAutoConfiguration.class})
class RecuperacionFrecuenciaControllerTest {
    @Autowired MockMvc mvc;
    @Autowired RecuperacionContrasenaService recuperacion;
    @MockitoBean UsuarioService usuarios;
    @MockitoBean AccesoCuentaService acceso;
    @MockitoBean UsuarioRepository usuariosRepo;
    @MockitoBean RecuperacionContrasenaRepository recuperaciones;
    @MockitoBean SesionRepository sesiones;
    @MockitoBean CorreoRecuperacionService correo;
    @MockitoBean jakarta.persistence.EntityManager entityManager;
    @MockitoBean JwtUtilService jwt;
    @MockitoBean UserDetailsService detalles;
    @MockitoSpyBean(name="recoveryRateClock") Clock reloj;
    static final AtomicLong VENTANA=new AtomicLong();
    AtomicReference<Instant> ahora=new AtomicReference<>();
    @BeforeEach void preparar(){
        ahora.set(Instant.parse("2026-10-25T12:00:00Z").plusSeconds(VENTANA.addAndGet(1800)));
        doAnswer(i->ahora.get()).when(reloj).instant();
        when(usuarios.solicitarRecuperacion(any())).thenAnswer(i->recuperacion.solicitar(i.getArgument(0)));
        when(usuariosRepo.findByCorreoNormalizado(anyString())).thenAnswer(i->{var u=new Usuario();u.setId(22L);u.setCorreo(i.getArgument(0));return u;});
        when(usuariosRepo.bloquearParaRecomendaciones(22L)).thenReturn(Optional.of(22L));
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder solicitud(String email,String ip){
        return post("/auth/recuperar-contrasena").with(r->{r.setRemoteAddr(ip);return r;})
                .contentType("application/json").content("{\"correo\":\""+email+"\"}");
    }
    @Test void limitePorCorreoEs429ConRetryAfterSinSegundoEnvioNiInvalidacion()throws Exception{
        mvc.perform(solicitud("cuenta@example.invalid","192.0.2.1")).andExpect(status().isOk());
        mvc.perform(solicitud(" CUENTA@EXAMPLE.INVALID ","192.0.2.2"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"))
                .andExpect(jsonPath("$.mensaje").exists()).andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.correo").doesNotExist());
        verify(correo,times(1)).enviar(any(),any());verify(recuperaciones,times(1)).eliminarDelUsuario(22L);
        verify(recuperaciones,times(1)).saveAndFlush(any());
    }
    @Test void cuotaIpNoSeEludeConXForwardedFor()throws Exception{
        for(int i=0;i<2;i++)mvc.perform(solicitud("cuenta"+i+"@example.invalid","192.0.2.1")
                .header("X-Forwarded-For","198.51.100."+i)).andExpect(status().isOk());
        mvc.perform(solicitud("otra@example.invalid","192.0.2.1").header("X-Forwarded-For","203.0.113.10"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","900"));
        verify(correo,times(2)).enviar(any(),any());
    }
    @Test void ventanaExpiradaPermiteRecuperacionPosterior()throws Exception{
        mvc.perform(solicitud("cuenta@example.invalid","192.0.2.1")).andExpect(status().isOk());
        mvc.perform(solicitud("cuenta@example.invalid","192.0.2.1")).andExpect(status().isTooManyRequests());
        ahora.updateAndGet(t->t.plusSeconds(900));
        mvc.perform(solicitud("cuenta@example.invalid","192.0.2.1")).andExpect(status().isOk());
        verify(correo,times(2)).enviar(any(),any());
    }
    @Test void inexistenteConserva404DentroDeCuotaYSeLimitaDespues()throws Exception{
        when(usuariosRepo.findByCorreoNormalizado("ausente@example.invalid")).thenReturn(null);
        mvc.perform(solicitud("ausente@example.invalid","192.0.2.1")).andExpect(status().isNotFound());
        mvc.perform(solicitud("ausente@example.invalid","192.0.2.1")).andExpect(status().isTooManyRequests());
        verify(correo,never()).enviar(any(),any());verifyNoInteractions(recuperaciones);
    }
    @Test void peticionesHttpSimultaneasSoloDisparanUnEnvio()throws Exception{
        CountDownLatch inicio=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(6)){
            var futuros=new ArrayList<Future<Integer>>();
            for(int i=0;i<12;i++)futuros.add(pool.submit(()->{inicio.await();return mvc.perform(solicitud("cuenta@example.invalid","192.0.2.1")).andReturn().getResponse().getStatus();}));
            inicio.countDown();int exitos=0,rechazos=0;
            for(var f:futuros){int estado=f.get(10,TimeUnit.SECONDS);if(estado==200)exitos++;if(estado==429)rechazos++;}
            assertEquals(1,exitos);assertEquals(11,rechazos);verify(correo,times(1)).enviar(any(),any());
            verify(recuperaciones,times(1)).eliminarDelUsuario(22L);
        }
    }
    @Test void preflightExponeRetryAfterAlFrontend()throws Exception{
        mvc.perform(options("/auth/recuperar-contrasena").header("Origin","http://localhost:5173")
                .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Expose-Headers",
                        org.hamcrest.Matchers.containsString("Retry-After")));
        verifyNoInteractions(correo,recuperaciones);
    }
}
