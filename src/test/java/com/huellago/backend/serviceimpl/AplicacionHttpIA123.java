package com.huellago.backend.serviceimpl;

import com.huellago.backend.repositories.*;
import com.huellago.backend.services.*;
import jakarta.persistence.EntityManagerFactory;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import javax.sql.DataSource;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;

/** Ejecutable manual SOLO de test. No SpringApplication, auto-configuracion ni component scan.
 * Preparado en PASO123: no ejecutar sin nueva autorizacion. */
public final class AplicacionHttpIA123 {
    private AplicacionHttpIA123() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=0) throw new IllegalArgumentException("IA123 no acepta argumentos");
        EntornoHttpIA123.validar(System.getenv()); // antes de crear servidor/contexto/JDBC
        new RegistroEditorialPruebaInternaFixture().consultar();
        var tomcat=servidorSinArrancar();
        tomcat.setBaseDir(Files.createTempDirectory("huellago-ia123-tomcat-").toString());
        var ctx=tomcat.addContext("",Files.createTempDirectory("huellago-ia123-docbase-").toString());
        var spring=new AnnotationConfigWebApplicationContext();
        spring.register(Infraestructura.class,Servicios.class,Web.class);
        if(!(ctx instanceof org.apache.catalina.core.StandardContext estandar))
            throw new IllegalStateException("Se requiere contexto Tomcat explicito IA123");
        estandar.addApplicationLifecycleListener(new ContextLoaderListener(spring));
        var servlet=Tomcat.addServlet(ctx,"ia123",new DispatcherServlet(spring)); servlet.setLoadOnStartup(1);
        ctx.addServletMappingDecoded("/","ia123");
        var filtro=new FilterDef(); filtro.setFilterName("seguridadIA123");
        filtro.setFilter(new DelegatingFilterProxy("springSecurityFilterChain",spring)); ctx.addFilterDef(filtro);
        var mapa=new FilterMap(); mapa.setFilterName("seguridadIA123"); mapa.addURLPattern("/*"); ctx.addFilterMap(mapa);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            try { tomcat.stop(); tomcat.destroy(); } catch(Exception ignored) { } finally { spring.close(); }
        },"ia123-cierre"));
        try {
            tomcat.start();
            if(!ctx.getState().isAvailable()) throw new IllegalStateException("Contexto IA123 no disponible; detener servidor");
            tomcat.getServer().await();
        } finally {
            try { tomcat.stop(); tomcat.destroy(); } finally { spring.close(); }
        }
    }

    static Tomcat servidorSinArrancar() {
        var tomcat=new Tomcat(); tomcat.setPort(EntornoHttpIA123.PUERTO);
        if(!tomcat.getConnector().setProperty("address",EntornoHttpIA123.DIRECCION))
            throw new IllegalStateException("No se pudo limitar la interfaz IA123");
        tomcat.getConnector().setProperty("maxPostSize","4096"); tomcat.getServer().setPort(-1);
        return tomcat;
    }

    @TestConfiguration(proxyBeanMethods=false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses=UsuarioRepository.class)
    static class Infraestructura {
        @Bean EntornoHttpIA123 entorno() { return EntornoHttpIA123.validar(System.getenv()); }
        @Bean DataSource dataSource(EntornoHttpIA123 entorno) { return new FuenteHttpIA123(entorno); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f=new LocalContainerEntityManagerFactoryBean(); f.setDataSource(ds);
            f.setPackagesToScan("com.huellago.backend.entities"); f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(EntornoHttpIA123.propiedadesJpa()); return f;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class Servicios {
        @Bean CatalogoAccionesAmbientalesService catalogo() { return new CatalogoAccionesAmbientalesServiceImpl(); }
        @Bean PresentacionRecomendacionesIAService presentador() { return new PresentacionRecomendacionesIAServiceImpl(); }
        @Bean PoliticaAutorizacionEditorialIAService editorial(CatalogoAccionesAmbientalesService c,PresentacionRecomendacionesIAService p) {
            var registro=new RegistroEditorialPruebaInternaFixture(p); registro.consultar(); // falla antes de habilitar la familia
            return new PoliticaAutorizacionEditorialIAServiceImpl(c,p,registro);
        }
        @Bean ContextoRecomendacionesIAService constructor(HabitoRepository h) { return new ContextoRecomendacionesIAServiceImpl(h); }
        @Bean CapturaContextoAmbientalIAService captura(UsuarioRepository u,UsuarioRecomendacionIARepository a,UsuarioRetoRepository r,ContextoRecomendacionesIAService c) {
            return new CapturaContextoAmbientalIAServiceImpl(u,a,r,c);
        }
        @Bean PersistenciaRecomendacionesIAService escritor(UsuarioRepository u,RecomendacionIARepository r,
                UsuarioRecomendacionIARepository a,CategoriaHabitoRepository c,CapturaContextoAmbientalIAService captura,
                EntityManagerFactory emf,PoliticaAutorizacionEditorialIAService editorial) {
            return new PersistenciaRecomendacionesIAServiceImpl(u,r,a,c,captura,SharedEntityManagerCreator.createSharedEntityManager(emf),editorial,true);
        }
        @Bean GeneradorRecomendacionesIAService selector() {
            var ajustes=new OllamaConfiguration.Ajustes(URI.create("http://localhost:11434"),"qwen3:4b-instruct",
                    Duration.ofSeconds(3),Duration.ofSeconds(120),65536);
            return new OllamaRecomendacionesServiceImpl(new OllamaConfiguration().ollamaRestClient(ajustes),ajustes);
        }
        @Bean OrquestadorRecomendacionesIAService orquestador(GeneradorRecomendacionesIAService s,CatalogoAccionesAmbientalesService c,PresentacionRecomendacionesIAService p) {
            return new OrquestadorRecomendacionesIAServiceImpl(s,c,p);
        }
        @Bean GeneracionManualIAService generacion(CapturaContextoAmbientalIAService c,PoliticaAutorizacionEditorialIAService e,
                OrquestadorRecomendacionesIAService o,PersistenciaRecomendacionesIAService p) { return new GeneracionManualIAServiceImpl(c,e,o,p); }
    }
    @TestConfiguration(proxyBeanMethods=false)
    @EnableWebMvc @EnableWebSecurity
    static class Web {
        @Bean SeguridadHttpIA123 seguridad(UsuarioRepository u,SesionRepository s) { return new SeguridadHttpIA123(u,s); }
        @Bean ControladorHttpIA123 controlador(GeneracionManualIAService g,SeguridadHttpIA123 s,PlatformTransactionManager tm) {
            return new ControladorHttpIA123(g,s,new TransactionTemplate(tm));
        }
        @Bean SecurityFilterChain cadenaIA123(HttpSecurity http,SeguridadHttpIA123 filtro) throws Exception {
            http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .requestCache(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
                    .exceptionHandling(e->e.authenticationEntryPoint((req,res,x)->res.sendError(401)))
                    .authorizeHttpRequests(a->a.requestMatchers(org.springframework.http.HttpMethod.POST,EntornoHttpIA123.RUTA,EntornoHttpIA123.TOKEN)
                            .authenticated().anyRequest().denyAll())
                    .addFilterBefore(filtro,UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }
    }
}
