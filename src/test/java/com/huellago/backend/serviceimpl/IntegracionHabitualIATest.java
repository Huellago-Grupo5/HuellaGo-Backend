package com.huellago.backend.serviceimpl;

import com.huellago.backend.controllers.RecomendacionController;
import com.huellago.backend.dtos.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.*;
import com.huellago.backend.services.*;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.math.BigDecimal;
import java.util.*;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Configuracion y seguridad habituales, respuestas y permisos exclusivamente sinteticos; sin JDBC/HTTP real. */
class IntegracionHabitualIATest {
    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity @EnableTransactionManagement
    @Import({RecomendacionController.class,SecurityConfiguration.class,JwtRequestFilter.class,JwtUtilService.class})
    static class Web { }
    @Configuration(proxyBeanMethods=false)
    @ComponentScan(basePackageClasses=CatalogoAccionesAmbientalesServiceImpl.class,useDefaultFilters=false,
            includeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes={
                    CatalogoAccionesAmbientalesServiceImpl.class,ContextoRecomendacionesIAServiceImpl.class,
                    CapturaContextoAmbientalIAServiceImpl.class}))
    static class EscaneoServiciosIA { }
    FixtureDemoIA127 f; AnnotationConfigWebApplicationContext c; MockMvc mvc; String token;
    RecomendacionService legado=mock(RecomendacionService.class);
    void preparar(boolean habilitada,boolean permisosSimulados) {
        f=new FixtureDemoIA127(); f.usuario.setId(77L); f.usuario.setCorreo("usuario-local-77@example.invalid"); f.datos.getFirst().setUnidad("Bus");
        when(f.usuarios.existsById(77L)).thenReturn(true); when(f.usuarios.findById(77L)).thenReturn(Optional.of(f.usuario));
        when(f.usuarios.bloquearParaRecomendaciones(77L)).thenAnswer(i->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());return Optional.of(77L);});
        when(f.habitos.findByUsuario_Id(77L)).thenAnswer(i->List.copyOf(f.datos));
        when(f.asociaciones.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(eq(77L),anyString()))
                .thenAnswer(i->f.guardadas.stream().filter(a->a.getEstado().equalsIgnoreCase(i.getArgument(1))).toList());
        when(f.asociaciones.findByUsuario_IdAndReferenciaContextoAndMetodoGeneracion(eq(77L),anyString(),anyString()))
                .thenAnswer(i->f.guardadas.stream().filter(a->a.getReferenciaContexto().equals(i.getArgument(1))).toList());
        when(f.asociaciones.findByIdAndUsuario_Id(anyInt(),eq(77L))).thenAnswer(i->f.guardadas.stream().filter(a->a.getId().equals(i.getArgument(0))).findFirst());
        when(f.selector.generarV2ConAlcanceInterno(any(),anyList(),anySet())).thenAnswer(i->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            var p=new Propuesta("Transporte",Decision.PROPONER,new Accion(ProteccionAcademicaIA.ACCION,"1",Map.of()),List.of(
                    new Evidencia("/transporte/medio",new Texto("Bus"),Ambito.NO_APLICA),new Evidencia("/transporte/kmSemana",new Numero(new BigDecimal("100")),Ambito.NO_APLICA),
                    new Evidencia("/transporte/diasSemana",new Numero(new BigDecimal("5")),Ambito.NO_APLICA)),null,null,null,List.of(),null);
            var dto=new RecomendacionFundamentadaIADTO("2",List.of(p));
            return new ResultadoGeneracionFundamentadaIADTO(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(dto),dto,
                    new ResultadoEvaluacionRecomendacionIADTO(ResultadoEvaluacionRecomendacionIADTO.Estado.ACEPTADA_ESTRUCTURALMENTE,List.of(),List.of()));
        });
        c=new AnnotationConfigWebApplicationContext(); c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("local-unitario",Map.of(
                "huellago.ollama.enabled",Boolean.toString(habilitada),"server.address","127.0.0.1",
                "huellago.jwt.secret","AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")));
        var sesiones=mock(SesionRepository.class); var emf=mock(EntityManagerFactory.class);
        when(emf.createEntityManager()).thenReturn(f.em); when(emf.createEntityManager(anyMap())).thenReturn(f.em);
        UserDetailsService detalles=correo->{if(!f.usuario.getCorreo().equals(correo))throw new org.springframework.security.core.userdetails.UsernameNotFoundException("No encontrado");return new UserSecurity(f.usuario);};
        c.addBeanFactoryPostProcessor(b->{
            b.registerSingleton("usuarios",f.usuarios); b.registerSingleton("habitos",f.habitos); b.registerSingleton("asociaciones",f.asociaciones);
            b.registerSingleton("retosUsuario",f.retos); b.registerSingleton("acciones",f.acciones); b.registerSingleton("categorias",f.categorias);
            b.registerSingleton("huellas",mock(HuellaCarbonoRepository.class)); b.registerSingleton("transactionManager",f.tm);
            b.registerSingleton("entityManagerFactory",emf); b.registerSingleton("selector",f.selector); b.registerSingleton("sesiones",sesiones);
            b.registerSingleton("detalles",detalles); b.registerSingleton("legado",legado);
            b.registerSingleton("notificaciones",mock(NotificacionService.class));
            if(permisosSimulados) {
                var cat=new CatalogoAccionesAmbientalesServiceImpl(); var pre=new PresentacionRecomendacionesIAServiceImpl();
                b.registerSingleton("editorial",new PoliticaAutorizacionEditorialIAServiceImpl(cat,pre,new RegistroEditorialPruebaInternaFixture(pre)));
            }
        });
        c.register(Web.class,EscaneoServiciosIA.class,OllamaConfiguration.IntegracionLocal.class); c.refresh();
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(c).apply(springSecurity()).build();
        token=c.getBean(JwtUtilService.class).generateToken(new UserSecurity(f.usuario));
        when(sesiones.existsByTokenAndActivaTrueAndFechaExpiracionAfter(eq(token),any())).thenReturn(true);
    }
    @AfterEach void cerrar() { TransactionSynchronizationManager.clear(); if(c!=null)c.close(); }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder solicitud() {
        return post("/usuarios/me/recomendaciones/generar").header("Authorization","Bearer "+token)
                .with(r->{r.setLocalAddr("127.0.0.1");r.setRemoteAddr("127.0.0.1");r.setLocalPort(8081);return r;});
    }
    @Test void autorizacionLocalRealPermiteAlUsuarioHabitualSinPublicar() throws Exception {
        preparar(true,false); mvc.perform(solicitud()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PROPUESTA_GUARDADA_USO_UNIVERSITARIO_LOCAL"))
                .andExpect(jsonPath("$.finalidad").value("USO_UNIVERSITARIO_LOCAL")).andExpect(jsonPath("$.publicable").value(false));
        assertEquals(1,c.getBeansOfType(CatalogoAccionesAmbientalesService.class).size());
        assertEquals(1,c.getBeansOfType(PresentacionRecomendacionesIAService.class).size());
        assertEquals(1,c.getBeansOfType(PoliticaAutorizacionEditorialIAService.class).size());
        assertEquals(1,c.getBeansOfType(OrquestadorRecomendacionesIAService.class).size());
        assertEquals(1,c.getBeansOfType(PersistenciaRecomendacionesIAService.class).size());
        assertEquals(1,c.getBeansOfType(GeneracionManualIAService.class).size());
        assertEquals(1,c.getBeansOfType(EntradaDemostracionRecomendacionesIA.class).size());
        assertEquals(1,f.guardadas.size()); assertEquals("TR_PLANIFICAR_DESPLAZAMIENTOS",f.guardadas.getFirst().getRecomendacionIA().getAccionId());
        verify(f.selector).generarV2ConAlcanceInterno(any(),anyList(),anySet()); verifyNoInteractions(legado);
    }
    @Test void endpointIaConviveConPendienteDeterministaDelMismoUsuarioYCategoria() throws Exception {
        preparar(true,false);
        var legado=new com.huellago.backend.entities.UsuarioRecomendacionIA();
        legado.setId(41); legado.setUsuario(f.usuario); legado.setEstado("Pendiente");
        when(f.asociaciones.findByUsuario_IdAndEstadoIgnoreCaseAndRecomendacionIA_Categoria_Id(77L,"pendiente",f.categoria.getId()))
                .thenReturn(List.of(legado));
        mvc.perform(solicitud()).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PROPUESTA_GUARDADA_USO_UNIVERSITARIO_LOCAL"))
                .andExpect(jsonPath("$.publicable").value(false));
        assertEquals(1,f.guardadas.size()); assertEquals("Pendiente",legado.getEstado());
    }
    @Test void usuarioHabitualBusGeneraYReutilizaConPermisosSoloSimulados() throws Exception {
        preparar(true,true); mvc.perform(solicitud()).andExpect(status().isOk()).andExpect(jsonPath("$.usuarioId").value(77))
                .andExpect(jsonPath("$.estado").value("PROPUESTA_GUARDADA_USO_UNIVERSITARIO_LOCAL"))
                .andExpect(jsonPath("$.finalidad").value("USO_UNIVERSITARIO_LOCAL")).andExpect(jsonPath("$.publicable").value(false));
        var texto=f.guardadas.getFirst().getTituloInstantanea(); var fecha=f.guardadas.getFirst().getFechaGeneracion();
        mvc.perform(solicitud()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RESULTADO_REUTILIZADO"));
        assertEquals(1,f.guardadas.size()); assertEquals(texto,f.guardadas.getFirst().getTituloInstantanea()); assertEquals(fecha,f.guardadas.getFirst().getFechaGeneracion());
        assertEquals(0,f.usuario.getEcoPuntos()); verifyNoInteractions(legado);
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(c.getBean(PersistenciaRecomendacionesIAService.class)));
        assertTrue(c.getBeansOfType(javax.sql.DataSource.class).isEmpty());
    }
    @Test void desactivadoConservaLegadoSinRutaIa() throws Exception {
        preparar(false,false); when(legado.generar(f.usuario)).thenReturn(new GeneracionRecomendacionesDTO());
        mvc.perform(solicitud()).andExpect(status().isOk()).andExpect(jsonPath("$.finalidad").doesNotExist());
        verify(legado).generar(f.usuario); verifyNoInteractions(f.selector);
        assertTrue(c.getBeansOfType(EntradaDemostracionRecomendacionesIA.class).isEmpty());
    }
    @Test void jwtPropiedadYPublicacionSiguenProtegidos() throws Exception {
        preparar(true,true); mvc.perform(post("/usuarios/me/recomendaciones/generar")).andExpect(status().isUnauthorized());
        mvc.perform(solicitud().content("{\"usuarioId\":91,\"publicable\":true}")).andExpect(status().isBadRequest());
        mvc.perform(solicitud().with(r->{r.setRemoteAddr("192.0.2.1");return r;})).andExpect(status().isForbidden());
        var a=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(new UserSecurity(f.usuario),null,List.of());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->c.getBean(GeneracionManualIAService.class).generarParaPublicacionAutorizada(a));
        verifyNoInteractions(f.selector,legado); assertTrue(f.guardadas.isEmpty());
    }
    @Test void catalogoExactoDeRevisionContieneLasTreintaDefiniciones() throws Exception {
        var definiciones=new PresentacionRecomendacionesIAServiceImpl().definicionesParaRevision();
        assertEquals(30,definiciones.size()); assertEquals(10,definiciones.stream().map(DefinicionPresentacionIADTO::accionId).distinct().count());
        // Cotejo local opcional del entregable existente; sin crear otro documento de revision.
        var archivo=java.nio.file.Path.of("target/paso113/catalogo-editorial-completo.md");
        if(java.nio.file.Files.exists(archivo)) {
            String original=java.nio.file.Files.readString(archivo);
            for(var d:definiciones) { assertTrue(original.contains(d.titulo())); assertTrue(original.contains(d.descripcion())); assertTrue(original.contains(d.motivo())); }
        }
    }
    @Test void lasTreintaHuellasAprobadasSonExactasYElRegistroEsInmutable() {
        var presentador=new PresentacionRecomendacionesIAServiceImpl();
        var registro=new RegistroEditorialUniversitarioLocal(presentador);
        var permisos=registro.consultar();
        assertEquals(30,permisos.size());
        assertEquals(10,permisos.stream().map(PoliticaAutorizacionEditorialIAService.Autorizacion::accionId).distinct().count());
        var politica=new PoliticaAutorizacionEditorialIAServiceImpl();
        for(var d:presentador.definicionesParaRevision()) {
            assertTrue(permisos.stream().anyMatch(p->p.accionId().equals(d.accionId())
                    && p.versionAccion().equals(d.versionAccion()) && p.enfoque().equals(d.enfoque())
                    && p.versionPresentacion().equals(d.versionPresentacion())
                    && p.sha256().equals(politica.huellaDefinicion(d))
                    && p.camposContextoPermitidos().equals(d.camposContextoPermitidos())));
        }
        assertThrows(UnsupportedOperationException.class,permisos::clear);
    }
    @Test void cambioDeDefinicionInvalidaElRegistroUniversitarioCompleto() {
        var fuente=new PresentacionRecomendacionesIAServiceImpl().definicionesParaRevision();
        var presentador=mock(PresentacionRecomendacionesIAService.class);
        for(String cambio:List.of("titulo","descripcion","motivo","campos","version","falta")) {
            var original=fuente.getFirst();
            var alterada=new DefinicionPresentacionIADTO(original.accionId(),original.versionAccion(),original.enfoque(),
                    cambio.equals("version")?"2":original.versionPresentacion(),
                    cambio.equals("titulo")?original.titulo()+" ":original.titulo(),
                    cambio.equals("descripcion")?original.descripcion()+" ":original.descripcion(),
                    cambio.equals("motivo")?original.motivo()+" ":original.motivo(),
                    cambio.equals("campos")?Set.of("/campo/ajeno"):original.camposContextoPermitidos());
            var lista=new ArrayList<>(fuente); lista.set(0,alterada);
            if(cambio.equals("falta")) lista.removeFirst();
            when(presentador.definicionesParaRevision()).thenReturn(lista);
            assertThrows(IllegalStateException.class,()->new RegistroEditorialUniversitarioLocal(presentador),cambio);
        }
    }
    @Test void ejecucionHabitualConservaInicializacionYEsquemaAnteriores() throws Exception {
        var p=new java.util.Properties(); try(var r=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of("src/main/resources/application.properties"))) { p.load(r); }
        assertEquals("true",p.getProperty("huellago.ollama.enabled")); assertEquals("127.0.0.1",p.getProperty("server.address"));
        assertEquals("never",p.getProperty("spring.sql.init.mode")); assertEquals("update",p.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("src/main/java/com/huellago/backend/config/PostgresProjectInitializer.java")));
        assertNull(p.getProperty("spring.flyway.enabled")); assertNull(p.getProperty("spring.liquibase.enabled"));
        assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of("src/main/resources/application-academico-local-ia.properties")));
        for(String nombre:List.of("AcademicoLocalIA","ProteccionAcademicaIA","AutorizacionAcademicaIA","AutenticacionAcademicaIA","FuenteAcademicaIA","EntradaAcademicaIA"))
            assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of("target/classes/com/huellago/backend/serviceimpl/"+nombre+".class")));
    }
    @Test void activacionNoPermiteExposicionPublicaDelUsoLocal() {
        var e=new org.springframework.mock.env.MockEnvironment().withProperty("server.address","0.0.0.0");
        var b=new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        assertThrows(IllegalStateException.class,()->OllamaConfiguration.IntegracionLocal.soloUsoLocal(e).postProcessBeanFactory(b));
    }
}
