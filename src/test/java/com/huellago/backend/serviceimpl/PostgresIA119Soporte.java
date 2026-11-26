package com.huellago.backend.serviceimpl;

import com.huellago.backend.repositories.*;
import com.huellago.backend.services.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;

/** Solo test. No carga application.properties, autoconfiguración Boot, data.sql ni listeners. */
final class PostgresIA119Soporte {
    static final String BASE="huellagodb_ia_integracion_test";
    static final String ACTIVACION="HUELLAGO_IA_PG_REAL_TEST";
    static String validarUrl(String url) {
        if(url==null || !url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[1-9][0-9]{0,4}/"+BASE))
            throw new IllegalArgumentException("URL de prueba rechazada; se exige destino local exclusivo IA119 sin parámetros");
        int puerto=Integer.parseInt(url.substring(url.lastIndexOf(':')+1,url.lastIndexOf('/')));
        if(puerto>65535) throw new IllegalArgumentException("Puerto de prueba inválido");
        return url;
    }
    static void comprobarBase(String nombre) throws SQLException {
        if(!BASE.equals(nombre)) throw new SQLException("El servidor no confirmó la base exclusiva IA119","08004");
    }
    static String variable(String nombre) {
        String v=System.getenv(nombre);
        if(v==null || v.isBlank()) throw new IllegalStateException("Falta variable exclusiva de pruebas: "+nombre);
        return v;
    }
    static final class FuenteProtegida extends AbstractDataSource {
        private final String url;
        private final Properties propiedades=new Properties();
        final Map<String,Integer> pids=new ConcurrentHashMap<>();
        FuenteProtegida(String url,String usuario,String clave) {
            this.url=validarUrl(url);
            if(usuario==null || usuario.isBlank() || clave==null || clave.isBlank())
                throw new IllegalArgumentException("Se requieren credenciales exclusivas de prueba desde el entorno");
            propiedades.setProperty("user",usuario); propiedades.setProperty("password",clave);
            propiedades.setProperty("connectTimeout","5"); propiedades.setProperty("socketTimeout","25");
            propiedades.setProperty("ApplicationName","HuellaGo-IA119-sintetico");
            propiedades.setProperty("options","-c search_path=public -c lock_timeout=8000 -c statement_timeout=20000");
        }
        @Override public Connection getConnection() throws SQLException {
            if(!"true".equals(System.getenv(ACTIVACION))) throw new SQLException("Pruebas PostgreSQL IA119 deshabilitadas","08004");
            validarUrl(url); Connection c=null;
            try {
                c=DriverManager.getConnection(url,propiedades);
                try(var s=c.createStatement();var r=s.executeQuery("SELECT current_database(), pg_backend_pid()")) {
                    if(!r.next()) throw new SQLException("No se pudo verificar el destino","08004");
                    comprobarBase(r.getString(1));
                    if(Thread.currentThread().getName().startsWith("ia119-")) pids.put(Thread.currentThread().getName(),r.getInt(2));
                }
                try(var s=c.createStatement();var r=s.executeQuery("SELECT rolsuper OR rolcreatedb OR rolcreaterole FROM pg_roles WHERE rolname=current_user")) {
                    if(!r.next() || r.getBoolean(1)) throw new SQLException("Se requiere rol de prueba sin privilegios administrativos","08004");
                }
                return c;
            } catch(SQLException e) {
                if(c!=null) try { c.close(); } catch(SQLException ignored) { }
                // No propagar mensajes de autenticación que puedan imprimir usuario o configuración.
                throw new SQLException("No se pudo abrir/verificar conexión exclusiva IA119",e.getSQLState(),e.getErrorCode());
            }
        }
        @Override public Connection getConnection(String u,String p) throws SQLException {
            throw new SQLException("No se admite sustituir credenciales por llamada","08004");
        }
        @Override public String toString() { return "FuenteProtegida[IA119, credenciales omitidas]"; }
    }
    /** Barreras/fallo SQL solo de test; los repositorios y transacciones siguen siendo reales. */
    static final class Control {
        volatile String pausarHilo;
        volatile CountDownLatch dentro=new CountDownLatch(1), liberar=new CountDownLatch(1);
        final ThreadLocal<Boolean> fallarAsociacion=ThreadLocal.withInitial(()->false);
        final AtomicBoolean canonicaInsertadaAntesDelFallo=new AtomicBoolean();
        void pausar(String hilo) { pausarHilo=hilo; dentro=new CountDownLatch(1); liberar=new CountDownLatch(1); }
        void antesDeComprobar() throws InterruptedException {
            if(Thread.currentThread().getName().equals(pausarHilo)) {
                dentro.countDown();
                if(!liberar.await(15,TimeUnit.SECONDS)) throw new IllegalStateException("Barrera IA119 vencida");
            }
        }
    }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses=UsuarioRepository.class)
    static class Configuracion {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f=new LocalContainerEntityManagerFactoryBean(); f.setDataSource(ds);
            f.setPackagesToScan("com.huellago.backend.entities"); f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate",
                    "hibernate.dialect","org.hibernate.dialect.PostgreSQLDialect",
                    "hibernate.default_schema","public",
                    "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl",
                    "hibernate.show_sql","false","hibernate.format_sql","false",
                    "hibernate.cache.use_second_level_cache","false"));
            return f;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean ContextoRecomendacionesIAService constructor(HabitoRepository h) { return new ContextoRecomendacionesIAServiceImpl(h); }
        @Bean CapturaContextoAmbientalIAService captura(UsuarioRepository u,UsuarioRecomendacionIARepository a,UsuarioRetoRepository r,ContextoRecomendacionesIAService c) {
            return new CapturaContextoAmbientalIAServiceImpl(u,a,r,c);
        }
        @Bean RecomendacionService recomendaciones() { return new RecomendacionServiceImpl(); }
        @Bean HabitoService habitos() { return new HabitoServiceImpl(); }
        @Bean PoliticaAutorizacionEditorialIAService editorial() {
            var p=new PresentacionRecomendacionesIAServiceImpl();
            return new PoliticaAutorizacionEditorialIAServiceImpl(new CatalogoAccionesAmbientalesServiceImpl(),p,
                    new RegistroEditorialPruebaInternaFixture(p));
        }
        @Bean PersistenciaRecomendacionesIAService escritor(UsuarioRepository u,RecomendacionIARepository r,
                UsuarioRecomendacionIARepository a,CategoriaHabitoRepository c,CapturaContextoAmbientalIAService captura,
                EntityManagerFactory emf,PoliticaAutorizacionEditorialIAService editorial,Control control) {
            EntityManager em=SharedEntityManagerCreator.createSharedEntityManager(emf);
            var pc=new ProxyFactory(captura);
            pc.addAdvice((org.aopalliance.intercept.MethodInterceptor)i->{
                if(i.getMethod().getName().equals("comprobarVigenciaEnTransaccion")) control.antesDeComprobar();
                return i.proceed();
            });
            var pa=new ProxyFactory(a);
            pa.addAdvice((org.aopalliance.intercept.MethodInterceptor)i->{
                if(i.getMethod().getName().equals("saveAndFlush") && control.fallarAsociacion.get()) {
                    Number n=(Number)em.createNativeQuery("SELECT count(*) FROM public.recomendaciones_ia WHERE accion_id='TR_PLANIFICAR_DESPLAZAMIENTOS'").getSingleResult();
                    control.canonicaInsertadaAntesDelFallo.set(n.longValue()==1);
                    em.createNativeQuery("SELECT 1/0").getSingleResult(); // Fallo PostgreSQL real dentro de la unidad de escritura.
                }
                return i.proceed();
            });
            return new PersistenciaRecomendacionesIAServiceImpl(u,r,(UsuarioRecomendacionIARepository)pa.getProxy(),c,
                    (CapturaContextoAmbientalIAService)pc.getProxy(),em,editorial,true);
        }
    }
    static AnnotationConfigApplicationContext abrir(FuenteProtegida ds,Control control) {
        if(!"true".equals(System.getenv(ACTIVACION))) throw new IllegalStateException("Activación IA119 ausente");
        var contexto=new AnnotationConfigApplicationContext();
        contexto.registerBean(DataSource.class,()->ds); contexto.registerBean(Control.class,()->control);
        contexto.register(Configuracion.class);
        try { contexto.refresh(); return contexto; } catch(RuntimeException e) { contexto.close(); throw e; }
    }
}
