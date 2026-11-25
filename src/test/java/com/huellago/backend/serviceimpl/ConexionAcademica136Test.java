package com.huellago.backend.serviceimpl;

import com.huellago.backend.repositories.*;
import com.huellago.backend.services.*;
import com.huellago.backend.security.*;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import java.util.*;

/** Configuracion IA de src/main y proxies reales; JDBC, EM y selector simulados. Sin servidor. */
class ConexionAcademica136Test {
    FixtureDemoIA127 f; AnnotationConfigWebApplicationContext c; MockMvc mvc; String token;
    @BeforeEach void preparar() {
        f=new FixtureDemoIA127(); c=new AnnotationConfigWebApplicationContext(); c.setServletContext(new MockServletContext());
        c.getEnvironment().setActiveProfiles(AcademicoLocalIA.PERFIL);
        var sesiones=mock(SesionRepository.class); var emf=mock(EntityManagerFactory.class);
        when(emf.createEntityManager()).thenReturn(f.em); when(emf.createEntityManager(anyMap())).thenReturn(f.em);
        c.addBeanFactoryPostProcessor(b->{
            b.registerSingleton("usuarios",f.usuarios); b.registerSingleton("habitos",f.habitos); b.registerSingleton("asociaciones",f.asociaciones);
            b.registerSingleton("retosUsuario",f.retos); b.registerSingleton("retos",mock(RetoRepository.class));
            b.registerSingleton("acciones",f.acciones); b.registerSingleton("categorias",f.categorias); b.registerSingleton("sesiones",sesiones);
            b.registerSingleton("huellas",mock(HuellaCarbonoRepository.class)); b.registerSingleton("transactionManager",f.tm);
            b.registerSingleton("entityManagerFactory",emf); b.registerSingleton("selector",f.selector);
            b.registerSingleton("notificaciones",mock(NotificacionService.class));
            b.registerSingleton("politicaMedicionInicial",mock(PoliticaMedicionInicial.class));
        });
        c.register(AcademicoLocalIA.Flujo.class,AcademicoLocalIA.Web.class); c.refresh();
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(c).apply(springSecurity()).build();
        token=c.getBean(JwtUtilService.class).generateToken(new UserSecurity(f.usuario));
        when(sesiones.existsByTokenAndActivaTrueAndFechaExpiracionAfter(eq(token),any())).thenReturn(true);
    }
    @AfterEach void cerrar() { TransactionSynchronizationManager.clear(); if(c!=null)c.close(); }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder solicitud() {
        return post("/usuarios/me/recomendaciones/generar").header("Authorization","Bearer "+token)
                .with(r->{r.setLocalAddr("127.0.0.1");r.setRemoteAddr("127.0.0.1");r.setLocalPort(8081);return r;});
    }
    @Test void beansRealesPorProxyYEndpointHabitualGeneranYReutilizan() throws Exception {
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(c.getBean("capturaBase")));
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(c.getBean(PersistenciaRecomendacionesIAService.class)));
        assertFalse(c.containsBean("refrescoRecomendacionesListener")); assertTrue(c.getBeansOfType(javax.sql.DataSource.class).isEmpty());
        mvc.perform(solicitud()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PROPUESTA_GUARDADA_PRUEBA_INTERNA"))
                .andExpect(jsonPath("$.finalidad").value("DEMO_ACADEMICA_LOCAL")).andExpect(jsonPath("$.publicable").value(false));
        f.enfoque="AGRUPACION";
        mvc.perform(solicitud()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RESULTADO_REUTILIZADO"));
        assertEquals(1,f.guardadas.size()); assertEquals(0,f.usuario.getEcoPuntos());
    }
    @Test void jwtRealObligatorioYProduccionNoHabilitada() throws Exception {
        mvc.perform(post("/usuarios/me/recomendaciones/generar")).andExpect(status().isUnauthorized());
        mvc.perform(solicitud().with(r->{r.removeHeader("Authorization");r.addHeader("Authorization","Bearer invalido");return r;})).andExpect(status().isUnauthorized());
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(new UserSecurity(f.usuario),null,List.of());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->c.getBean(GeneracionManualIAService.class).generarParaPublicacionAutorizada(auth));
        verifyNoInteractions(f.selector); assertTrue(f.guardadas.isEmpty());
    }
    @Test void medioNoAutorizadoNoInfiereNiEscribe() throws Exception {
        f.datos.getFirst().setUnidad("Bus"); mvc.perform(solicitud()).andExpect(status().isForbidden());
        verifyNoInteractions(f.selector); assertTrue(f.guardadas.isEmpty());
    }
    @Test void registroAcademicoRestringeTresVariantesYElRestoDeRutas() throws Exception {
        var politica=c.getBean(PoliticaAutorizacionEditorialIAService.class);
        var contexto=c.getBean(CapturaContextoAmbientalIAService.class).capturar(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(new UserSecurity(f.usuario),null,List.of()));
        var r=politica.evaluar(contexto.contexto().contexto(),contexto.realizadas());
        assertEquals(List.of(ProteccionAcademicaIA.ACCION),r.elegibles().stream().map(a->a.definicion().id()).toList());
        mvc.perform(get("/auth/register").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        verifyNoInteractions(f.selector);
    }
}
