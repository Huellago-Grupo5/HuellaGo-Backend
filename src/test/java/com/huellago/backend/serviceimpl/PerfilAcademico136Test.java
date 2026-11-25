package com.huellago.backend.serviceimpl;

import com.huellago.backend.HuellaGoBackendApplication;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Preflight y seleccion Spring, sin ejecutar SpringApplication.run ni abrir JDBC. */
class PerfilAcademico136Test {
    MockEnvironment entorno() throws Exception {
        var p=new Properties(); try(var r=Files.newBufferedReader(Path.of("src/test/resources/application-academico-local-ia.properties"))) { p.load(r); }
        var e=solicitud(); e.setActiveProfiles(AcademicoLocalIA.PERFIL);
        var mapa=new HashMap<String,Object>(); p.forEach((k,v)->mapa.put(k.toString(),v));
        e.getPropertySources().addLast(new MapPropertySource("perfil-academico",mapa));
        e.setProperty("spring.config.location",AcademicoLocalIA.CONFIG);
        e.setProperty("HUELLAGO_IA127_CONFIRMAR_BASE",AcademicoLocalIA.BASE);
        e.setProperty("HUELLAGO_IA127_PG_CLAVE","SINTETICA-unitario-no-usar"); return e;
    }
    MockEnvironment solicitud() {
        return new MockEnvironment().withProperty("spring.profiles.active",AcademicoLocalIA.PERFIL)
                .withProperty("HUELLAGO_IA127_ACTIVAR","true").withProperty("HUELLAGO_IA127_MODO","DEMO_ACADEMICA_LOCAL");
    }
    @Test void legadoPredeterminadoNoRegistraComposicionAcademica() {
        assertEquals(Set.of(HuellaGoBackendApplication.class),AcademicoLocalIA.aplicacion(new MockEnvironment()).getAllSources());
        try(var c=new AnnotationConfigApplicationContext()) {
            c.register(AcademicoLocalIA.class); c.refresh();
            assertFalse(c.containsBean("dataSource")); assertFalse(c.containsBean("entrada")); assertFalse(c.containsBean("editorial"));
        }
    }
    @Test void perfilSeleccionaSoloLaComposicionExplicitaSinArrancarla() {
        assertEquals(Set.of(AcademicoLocalIA.class),AcademicoLocalIA.aplicacion(solicitud()).getAllSources());
    }
    @ParameterizedTest @ValueSource(strings={"HUELLAGO_IA127_ACTIVAR","HUELLAGO_IA127_MODO"})
    void perfilSoloNoEsAutorizacion(String clave) {
        var e=solicitud(); e.setProperty(clave,"false"); assertThrows(IllegalStateException.class,()->AcademicoLocalIA.aplicacion(e));
    }
    @ParameterizedTest @ValueSource(strings={"spring.config.location","spring.config.additional-location","spring.config.import","spring.config.name","spring.profiles.include"})
    void noImportaConfiguracionHabitual(String propiedad) {
        var e=solicitud(); e.setProperty(propiedad,"no-autorizado"); assertThrows(IllegalStateException.class,()->AcademicoLocalIA.aplicacion(e));
    }
    @Test void noMezclaPerfilesNiAceptaFlagSinPerfil() {
        var e=solicitud(); e.setProperty("spring.profiles.active","academico-local-ia,prod");
        assertThrows(IllegalStateException.class,()->AcademicoLocalIA.aplicacion(e));
        e.setProperty("spring.profiles.active",""); assertThrows(IllegalStateException.class,()->AcademicoLocalIA.aplicacion(e));
    }
    @Test void propiedadesRealesDelPerfilSonSegurasSinSecretos() throws Exception {
        var e=entorno(); assertDoesNotThrow(()->AcademicoLocalIA.validar(e));
        assertEquals("never",e.getProperty("spring.sql.init.mode")); assertEquals("validate",e.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("127.0.0.1",e.getProperty("server.address")); assertEquals("8081",e.getProperty("server.port"));
        assertEquals("",e.getProperty("huellago.jwt.secret"));
    }
    @Test void cargadorRealDeSpringLeeSoloElArchivoAcademicoSinCrearContexto() {
        var e=solicitud(); e.setProperty("spring.config.location",AcademicoLocalIA.CONFIG);
        e.setProperty("HUELLAGO_IA127_CONFIRMAR_BASE",AcademicoLocalIA.BASE);
        e.setProperty("HUELLAGO_IA127_PG_CLAVE","SINTETICA-unitario-no-usar");
        org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor.applyTo(e);
        assertDoesNotThrow(()->AcademicoLocalIA.validar(e));
        assertEquals(AcademicoLocalIA.URL,e.getProperty("spring.datasource.url"));
        assertTrue(java.util.stream.StreamSupport.stream(e.getPropertySources().spliterator(),false)
                .noneMatch(p->p.getName().contains("application.properties")));
    }
    @ParameterizedTest @ValueSource(strings={"spring.datasource.url","spring.datasource.username","server.address","server.port",
            "spring.jpa.hibernate.ddl-auto","spring.sql.init.mode","spring.flyway.enabled","spring.liquibase.enabled",
            "huellago.ollama.model","huellago.ollama.base-url","HUELLAGO_IA127_CONFIRMAR_BASE","spring.config.location"})
    void destinoOInicializacionIncorrectosAbortanAntesDeCrearFuente(String propiedad) throws Exception {
        var e=entorno(); e.setProperty(propiedad,"NO_AUTORIZADO");
        assertThrows(IllegalStateException.class,()->new AcademicoLocalIA().dataSource(e));
    }
    @Test void noAceptaSecretosJwtExternosNiFaltaDeClavePostgres() throws Exception {
        var e=entorno(); e.setProperty("huellago.jwt.secret","secreto-sintetico-no-productivo");
        assertThrows(IllegalStateException.class,()->AcademicoLocalIA.validar(e));
        e.setProperty("huellago.jwt.secret",""); e.setProperty("HUELLAGO_IA127_PG_CLAVE","");
        assertThrows(IllegalStateException.class,()->AcademicoLocalIA.validar(e));
    }
    @Test void noPermiteEscaneoProductivoJuntoAComposicionAcademica() {
        var b=new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        b.registerBeanDefinition("habitual",new org.springframework.beans.factory.support.RootBeanDefinition(HuellaGoBackendApplication.class));
        assertThrows(IllegalStateException.class,()->AcademicoLocalIA.composicionExclusiva().postProcessBeanFactory(b));
    }
}
