package com.huellago.backend.serviceimpl;

import com.huellago.backend.controllers.RecomendacionController;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.*;
import com.huellago.backend.services.*;
import jakarta.persistence.EntityManagerFactory;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import javax.sql.DataSource;
import java.nio.file.*;
import java.util.*;

/** SOLO test. Preparado, no ejecutar sin autorizacion posterior a PASO127B. */
public final class AplicacionDemoIA127 {
    private AplicacionDemoIA127() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=0) throw new IllegalArgumentException("La demostracion no acepta argumentos");
        ProteccionDemoIA127.entorno(System.getenv()); new RegistroEditorialPruebaInternaFixture().consultar();
        var tomcat=servidorSinArrancar(); tomcat.setBaseDir(Files.createTempDirectory("huellago-demo127-").toString());
        var ctx=tomcat.addContext("",Files.createTempDirectory("huellago-demo127-web-").toString());
        var spring=nuevoContexto(); spring.register(Infraestructura.class,Servicios.class,Web.class);
        if(!(ctx instanceof org.apache.catalina.core.StandardContext estandar)) throw new IllegalStateException("Contexto inesperado");
        estandar.addApplicationLifecycleListener(new ContextLoaderListener(spring));
        Tomcat.addServlet(ctx,"huellago-demo",new DispatcherServlet(spring)).setLoadOnStartup(1); ctx.addServletMappingDecoded("/","huellago-demo");
        var filtro=new FilterDef(); filtro.setFilterName("security"); filtro.setFilter(new DelegatingFilterProxy("springSecurityFilterChain",spring)); ctx.addFilterDef(filtro);
        var mapa=new FilterMap(); mapa.setFilterName("security"); mapa.addURLPattern("/*"); ctx.addFilterMap(mapa);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{ try { tomcat.stop(); tomcat.destroy(); } catch(Exception ignored) { } finally { spring.close(); } },"demo127-cierre"));
        try { tomcat.start(); if(!ctx.getState().isAvailable()) throw new IllegalStateException("Demostracion no disponible"); tomcat.getServer().await(); }
        finally { try { tomcat.stop(); tomcat.destroy(); } finally { spring.close(); } }
    }
    static Tomcat servidorSinArrancar() {
        var t=AplicacionHttpIA123.servidorSinArrancar(); t.setPort(8081); t.getConnector().setPort(8081); return t;
    }
    static AnnotationConfigWebApplicationContext nuevoContexto() {
        var c=new AnnotationConfigWebApplicationContext();
        byte[] clave=new byte[32]; new java.security.SecureRandom().nextBytes(clave);
        c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("aislamiento-demo127",Map.of(
                "huellago.jwt.secret",Base64.getEncoder().encodeToString(clave),"spring.sql.init.mode","never",
                "spring.jpa.hibernate.ddl-auto","validate","spring.flyway.enabled","false","spring.liquibase.enabled","false",
                "server.address","127.0.0.1","server.port","8081")));
        Arrays.fill(clave,(byte)0); return c;
    }
    @TestConfiguration(proxyBeanMethods=false) @EnableTransactionManagement @EnableJpaRepositories(basePackageClasses=UsuarioRepository.class)
    static class Infraestructura {
        @Bean EntornoHttpIA123 entorno() { return ProteccionDemoIA127.entorno(System.getenv()); }
        @Bean DataSource dataSource(EntornoHttpIA123 e) { return new FuenteHttpIA123(e); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) { return new AplicacionHttpIA123.Infraestructura().entityManagerFactory(ds); }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class Servicios {
        private final AplicacionHttpIA123.Servicios f=new AplicacionHttpIA123.Servicios();
        @Bean CatalogoAccionesAmbientalesService catalogo() { return f.catalogo(); }
        @Bean PresentacionRecomendacionesIAService presentador() { return f.presentador(); }
        @Bean PoliticaAutorizacionEditorialIAService editorial(CatalogoAccionesAmbientalesService c,PresentacionRecomendacionesIAService p) {
            return ProteccionDemoIA127.editorial(f.editorial(c,p));
        }
        @Bean ContextoRecomendacionesIAService constructor(HabitoRepository h,HuellaCarbonoRepository huellas) {
            return new ContextoRecomendacionesIAServiceImpl(h,huellas);
        }
        @Bean CapturaContextoAmbientalIAService capturaBase(UsuarioRepository u,UsuarioRecomendacionIARepository a,UsuarioRetoRepository r,ContextoRecomendacionesIAService c) { return f.captura(u,a,r,c); }
        @Bean @Primary CapturaContextoAmbientalIAService captura(@Qualifier("capturaBase") CapturaContextoAmbientalIAService c) { return ProteccionDemoIA127.captura(c); }
        @Bean PersistenciaRecomendacionesIAService escritor(UsuarioRepository u,RecomendacionIARepository r,UsuarioRecomendacionIARepository a,CategoriaHabitoRepository c,
                CapturaContextoAmbientalIAService captura,EntityManagerFactory emf,PoliticaAutorizacionEditorialIAService e) { return f.escritor(u,r,a,c,captura,emf,e); }
        @Bean GeneradorRecomendacionesIAService selector() { return f.selector(); }
        @Bean OrquestadorRecomendacionesIAService orquestador(GeneradorRecomendacionesIAService s,CatalogoAccionesAmbientalesService c,PresentacionRecomendacionesIAService p) { return f.orquestador(s,c,p); }
        @Bean GeneracionManualIAService generacion(CapturaContextoAmbientalIAService c,PoliticaAutorizacionEditorialIAService e,OrquestadorRecomendacionesIAService o,PersistenciaRecomendacionesIAService p) { return f.generacion(c,e,o,p); }
        @Bean EntradaDemostracionRecomendacionesIA entrada(GeneracionManualIAService g,UsuarioRecomendacionIARepository a,PlatformTransactionManager tm) { return new EntradaDemoIA127(g,a,tm); }
        @Bean RecomendacionService recomendaciones() { return new RecomendacionServiceImpl(); }
    }
    @TestConfiguration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity
    static class Web {
        @Bean RecomendacionController recomendacionesController() { return new RecomendacionController(); }
        @Bean JwtUtilService jwt(org.springframework.core.env.Environment entorno) {
            return new JwtUtilService(entorno.getRequiredProperty("huellago.jwt.secret"));
        }
        @Bean JwtRequestFilter jwtFilter() { return new JwtRequestFilter(); }
        @Bean UserDetailsService usuariosAutenticacion(UsuarioRepository u) { return correo->{
            if(!EntornoHttpIA123.CORREO.equals(correo)) throw new org.springframework.security.core.userdetails.UsernameNotFoundException("No autorizado");
            return AutenticacionDemoIA127.usuario(u);
        }; }
        @Bean AutenticacionDemoIA127 basicDemo(UsuarioRepository u) { return new AutenticacionDemoIA127(u); }
        @Bean AutenticacionDemoIA127.Emisor emisor(JwtUtilService jwt,SesionRepository s,PlatformTransactionManager tm) { return new AutenticacionDemoIA127.Emisor(jwt,s,tm); }
        @Bean SecurityFilterChain seguridad(HttpSecurity h,JwtRequestFilter jwt,AutenticacionDemoIA127 basic) throws Exception {
            h.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .requestCache(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
                    .exceptionHandling(e->e.authenticationEntryPoint((req,res,x)->res.sendError(401)))
                    .authorizeHttpRequests(a->a.anyRequest().authenticated())
                    .addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class).addFilterBefore(basic,JwtRequestFilter.class);
            return h.build();
        }
    }
}
