package com.huellago.backend.security;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import java.sql.DriverManager;

/** Se ejecuta ANTES de crear el datasource: jamás prueba una URL distinta a la permitida. */
public class BasePruebasGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public static final String URL="jdbc:postgresql://localhost:5432/huellagodb_hu29_test";
    @Override public void initialize(ConfigurableApplicationContext contexto) {
        var e=contexto.getEnvironment();
        if(!URL.equals(e.getProperty("spring.datasource.url"))
                || !"never".equals(e.getProperty("spring.sql.init.mode"))
                || !"validate".equals(e.getProperty("spring.jpa.hibernate.ddl-auto")))
            throw new IllegalStateException("Configuración de prueba no autorizada; no se abrirá conexión");
        if(e.getProperty("spring.datasource.jndi-name")!=null
                || e.getProperty("spring.datasource.hikari.jdbc-url")!=null
                && !URL.equals(e.getProperty("spring.datasource.hikari.jdbc-url")))
            throw new IllegalStateException("Datasource alternativo no autorizado; no se abrirá conexión");
        try(var c=DriverManager.getConnection(URL,e.getRequiredProperty("spring.datasource.username"),e.getRequiredProperty("spring.datasource.password"));
            var s=c.createStatement();var r=s.executeQuery("SELECT current_database()")) {
            if(!r.next() || !"huellagodb_hu29_test".equals(r.getString(1)))throw new IllegalStateException("Base efectiva no autorizada");
        } catch(java.sql.SQLException error){throw new IllegalStateException("No se pudo verificar la base de prueba",error);}
    }
}
