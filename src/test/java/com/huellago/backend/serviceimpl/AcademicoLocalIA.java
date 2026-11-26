package com.huellago.backend.serviceimpl;

import com.huellago.backend.HuellaGoBackendApplication;
import com.huellago.backend.controllers.RecomendacionController;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.*;
import com.huellago.backend.services.*;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import javax.sql.DataSource;
import java.util.*;

/** Composicion explicita del flujo ya existente. Sin escaneo de servicios/listeners productivos.
 * La clase principal sigue siendo la de IntelliJ; este origen solo se usa con perfil y permiso local. */
@Configuration(proxyBeanMethods=false)
@Profile(AcademicoLocalIA.PERFIL)
@EnableAutoConfiguration
@EnableTransactionManagement
@EnableJpaRepositories(basePackageClasses=UsuarioRepository.class)
@Import({AcademicoLocalIA.Web.class, AcademicoLocalIA.Flujo.class, OllamaConfiguration.class})
public class AcademicoLocalIA {
    public static final String PERFIL="academico-local-ia";
    static final String BASE="huellagodb_ia_integracion_test", ROL="huellago_ia119_test";
    static final String URL="jdbc:postgresql://localhost:5432/"+BASE;
    static final String CONFIG="classpath:/application-academico-local-ia.properties";

    /** Seleccion previa a cargar configuracion, crear conexiones o inicializar JPA. No arranca la aplicacion. */
    public static SpringApplication aplicacion(String[] args) {
        var e=new StandardEnvironment(); e.getPropertySources().addFirst(new SimpleCommandLinePropertySource(args));
        return aplicacion(e);
    }
    static SpringApplication aplicacion(Environment e) {
        var perfiles=Arrays.stream(e.getProperty("spring.profiles.active","").split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();
        if(!perfiles.contains(PERFIL)) {
            if("true".equals(e.getProperty("HUELLAGO_IA127_ACTIVAR"))) throw new IllegalStateException("Falta perfil academico explicito");
            return new SpringApplication(HuellaGoBackendApplication.class);
        }
        if(!perfiles.equals(List.of(PERFIL))) throw new IllegalStateException("No mezclar perfiles academicos y habituales");
        exigir(e,"HUELLAGO_IA127_ACTIVAR","true"); exigir(e,"HUELLAGO_IA127_MODO",ProteccionAcademicaIA.FINALIDAD);
        for(String propiedad:List.of("spring.config.location","spring.config.additional-location","spring.config.import","spring.config.name",
                "spring.profiles.include","spring.profiles.default"))
            if(e.getProperty(propiedad)!=null) throw new IllegalStateException("No se permiten configuraciones externas en el modo academico");
        var app=new SpringApplication(AcademicoLocalIA.class);
        // No cargar application.properties, data.sql ni secretos del arranque habitual.
        app.setDefaultProperties(Map.of("spring.config.location",CONFIG));
        app.addInitializers(c->validar(c.getEnvironment()));
        return app;
    }
    static void validar(ConfigurableEnvironment e) {
        if(!Arrays.equals(e.getActiveProfiles(),new String[]{PERFIL})) throw new IllegalStateException("Perfil academico exclusivo requerido");
        exigir(e,"HUELLAGO_IA127_ACTIVAR","true"); exigir(e,"HUELLAGO_IA127_MODO",ProteccionAcademicaIA.FINALIDAD);
        exigir(e,"HUELLAGO_IA127_CONFIRMAR_BASE",BASE);
        exigir(e,"spring.config.location",CONFIG);
        exigir(e,"spring.datasource.url",URL); exigir(e,"spring.datasource.username",ROL);
        exigir(e,"server.address","127.0.0.1"); exigir(e,"server.port","8081");
        exigir(e,"spring.jpa.hibernate.ddl-auto","validate"); exigir(e,"spring.sql.init.mode","never");
        exigir(e,"spring.flyway.enabled","false"); exigir(e,"spring.liquibase.enabled","false");
        exigir(e,"huellago.ollama.base-url","http://localhost:11434"); exigir(e,"huellago.ollama.model","qwen3:4b-instruct");
        if(e.getProperty("spring.datasource.password","").isBlank()) throw new IllegalStateException("Falta clave PostgreSQL academica");
        if(!e.getProperty("huellago.jwt.secret","").isBlank()) throw new IllegalStateException("No reutilizar secretos JWT externos");
        if(!e.getProperty("server.servlet.context-path","").isEmpty() || Boolean.parseBoolean(e.getProperty("server.ssl.enabled","false")))
            throw new IllegalStateException("Servidor academico no autorizado");
        // Los metadatos de usuario/rol real se verifican despues por la fuente, antes de exponer cada conexion.
    }
    private static void exigir(Environment e,String nombre,String valor) {
        if(!valor.equals(e.getProperty(nombre))) throw new IllegalStateException("Configuracion academica rechazada: "+nombre);
    }

    @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor composicionExclusiva() {
        return b->{
            for(String nombre:b.getBeanDefinitionNames())
                if(HuellaGoBackendApplication.class.getName().equals(b.getBeanDefinition(nombre).getBeanClassName()))
                    throw new IllegalStateException("Use la clase principal con el perfil academico; no mezcle el escaneo habitual");
        };
    }
    @Bean DataSource dataSource(ConfigurableEnvironment e) {
        validar(e); return new FuenteAcademicaIA(e.getRequiredProperty("spring.datasource.password"));
    }
    @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
        var f=new LocalContainerEntityManagerFactoryBean(); f.setDataSource(ds);
        f.setPackagesToScan("com.huellago.backend.entities"); f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.dialect","org.hibernate.dialect.PostgreSQLDialect",
                "hibernate.default_schema","public","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl",
                "hibernate.show_sql","false","hibernate.cache.use_second_level_cache","false"));
        return f;
    }
    @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
    @Configuration(proxyBeanMethods=false) @Profile(PERFIL) @EnableTransactionManagement
    public static class Flujo {
    @Bean CatalogoAccionesAmbientalesService catalogo() { return new CatalogoAccionesAmbientalesServiceImpl(); }
    @Bean PresentacionRecomendacionesIAService presentador() { return new PresentacionRecomendacionesIAServiceImpl(); }
    @Bean PoliticaAutorizacionEditorialIAService editorial(CatalogoAccionesAmbientalesService c,PresentacionRecomendacionesIAService p) {
        var r=new AutorizacionAcademicaIA(p); r.consultar();
        return ProteccionAcademicaIA.editorial(new PoliticaAutorizacionEditorialIAServiceImpl(c,p,r));
    }
    @Bean ContextoRecomendacionesIAService constructor(HabitoRepository h,HuellaCarbonoRepository huellas) { return new ContextoRecomendacionesIAServiceImpl(h,huellas); }
    @Bean CapturaContextoAmbientalIAService capturaBase(UsuarioRepository u,UsuarioRecomendacionIARepository a,UsuarioRetoRepository r,ContextoRecomendacionesIAService c) {
        return new CapturaContextoAmbientalIAServiceImpl(u,a,r,c);
    }
    @Bean @Primary CapturaContextoAmbientalIAService captura(@Qualifier("capturaBase") CapturaContextoAmbientalIAService real) {
        return ProteccionAcademicaIA.captura(real);
    }
    @Bean PersistenciaRecomendacionesIAService escritor(UsuarioRepository u,RecomendacionIARepository r,UsuarioRecomendacionIARepository a,CategoriaHabitoRepository c,
            CapturaContextoAmbientalIAService captura,EntityManagerFactory emf,PoliticaAutorizacionEditorialIAService e) {
        return new PersistenciaRecomendacionesIAServiceImpl(u,r,a,c,captura,SharedEntityManagerCreator.createSharedEntityManager(emf),e,true);
    }
    @Bean OrquestadorRecomendacionesIAService orquestador(GeneradorRecomendacionesIAService s,CatalogoAccionesAmbientalesService c,PresentacionRecomendacionesIAService p) {
        return new OrquestadorRecomendacionesIAServiceImpl(s,c,p);
    }
    @Bean GeneracionManualIAService generacion(CapturaContextoAmbientalIAService c,PoliticaAutorizacionEditorialIAService e,
            OrquestadorRecomendacionesIAService o,PersistenciaRecomendacionesIAService p,CatalogoAccionesAmbientalesService catalogo) {
        return new GeneracionManualIAServiceImpl(c,e,o,p,catalogo);
    }
    @Bean EntradaDemostracionRecomendacionesIA entrada(GeneracionManualIAService g,UsuarioRecomendacionIARepository a,PlatformTransactionManager tm) {
        return new EntradaAcademicaIA(g,a,tm);
    }
    @Bean RecomendacionService recomendaciones() { return new RecomendacionServiceImpl(); }
    }

    @Configuration(proxyBeanMethods=false) @Profile(PERFIL) @EnableWebMvc @EnableWebSecurity
    public static class Web {
        @Bean RecomendacionController recomendacionesController() { return new RecomendacionController(); }
        @Bean JwtUtilService jwt() {
            byte[] clave = new byte[32];
            new java.security.SecureRandom().nextBytes(clave);
            return new JwtUtilService(Base64.getEncoder().encodeToString(clave));
        }
        @Bean JwtRequestFilter jwtFilter() { return new JwtRequestFilter(); }
        @Bean UserDetailsService usuariosAutenticacion(UsuarioRepository u) { return correo->{
            if(!ProteccionAcademicaIA.CORREO.equals(correo)) throw new org.springframework.security.core.userdetails.UsernameNotFoundException("No autorizado");
            return AutenticacionAcademicaIA.usuario(u);
        }; }
        @Bean AutenticacionAcademicaIA basicAcademico(UsuarioRepository u) { return new AutenticacionAcademicaIA(u); }
        @Bean AutenticacionAcademicaIA.Emisor emisor(JwtUtilService j,SesionRepository s,PlatformTransactionManager tm) {
            return new AutenticacionAcademicaIA.Emisor(j,s,tm);
        }
        @Bean FilterRegistrationBean<JwtRequestFilter> registroJwt(JwtRequestFilter f) { var r=new FilterRegistrationBean<>(f); r.setEnabled(false); return r; }
        @Bean FilterRegistrationBean<AutenticacionAcademicaIA> registroBasic(AutenticacionAcademicaIA f) { var r=new FilterRegistrationBean<>(f); r.setEnabled(false); return r; }
        @Bean SecurityFilterChain seguridad(HttpSecurity h,JwtRequestFilter jwt,AutenticacionAcademicaIA basic) throws Exception {
            h.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .requestCache(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
                    .exceptionHandling(e->e.authenticationEntryPoint((req,res,x)->res.sendError(401)))
                    .authorizeHttpRequests(a->a.requestMatchers(AutenticacionAcademicaIA.TOKEN,"/usuarios/me/recomendaciones/**").authenticated().anyRequest().denyAll())
                    .addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class).addFilterBefore(basic,JwtRequestFilter.class);
            return h.build();
        }
    }
}
