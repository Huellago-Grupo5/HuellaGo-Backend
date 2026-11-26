package com.huellago.backend.serviceimpl;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import java.nio.file.*;
import java.util.*;

/** Herramienta MANUAL futura: exporta definiciones JPA, sin URL, DataSource ni datos.
 * No es un test y no se invoca por Maven test. Su salida es .sql.txt para revisión. */
public final class EsquemaIA119Offline {
    public static final class SinConexion extends org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl {
        @Override public java.sql.Connection getConnection() throws java.sql.SQLException {
            throw new java.sql.SQLException("La exportación offline prohíbe toda conexión");
        }
    }
    public static void main(String[] args) throws Exception {
        if(!"true".equals(System.getenv("HUELLAGO_IA_EXPORTAR_DDL")))
            throw new IllegalStateException("Exportación offline no habilitada");
        Path destino=Path.of("target/paso119/esquema-jpa-propuesto.sql.txt").toAbsolutePath().normalize();
        Files.createDirectories(destino.getParent());
        Map<String,Object> opciones=new HashMap<>();
        opciones.put("hibernate.dialect","org.hibernate.dialect.PostgreSQLDialect");
        opciones.put("hibernate.default_schema","public");
        opciones.put("hibernate.boot.allow_jdbc_metadata_access",false);
        opciones.put("hibernate.connection.provider_class",SinConexion.class);
        opciones.put("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl");
        opciones.put("jakarta.persistence.schema-generation.database.action","none");
        opciones.put("jakarta.persistence.schema-generation.scripts.action","create");
        opciones.put("jakarta.persistence.schema-generation.scripts.create-target",destino.toString());
        opciones.put("hibernate.hbm2ddl.delimiter",";");
        opciones.put("hibernate.hbm2ddl.halt_on_error",true);
        var registro=new StandardServiceRegistryBuilder().applySettings(opciones).build();
        try {
            var fuentes=new MetadataSources(registro);
            var scanner=new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(jakarta.persistence.Entity.class));
            for(var bean:scanner.findCandidateComponents("com.huellago.backend.entities"))
                fuentes.addAnnotatedClass(Class.forName(bean.getBeanClassName()));
            SchemaManagementToolCoordinator.process(fuentes.buildMetadata(),registro,opciones,accion->{
                throw new IllegalStateException("No se admite una acción diferida sobre base de datos");
            });
        } finally { StandardServiceRegistryBuilder.destroy(registro); }
    }
}
