package com.huellago.backend.serviceimpl;

import com.huellago.backend.repositories.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import javax.sql.DataSource;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Valida HQL contra las entidades reales sin abrir conexiones ni ejecutar SQL. */
class ReporteInstitucionalConsultasTest {
    @Test void consultasHqlSonValidasConHibernateYDialectPostgresqlSinBaseDeDatos() {
        DataSource prohibida = mock(DataSource.class, invocation -> {
            throw new AssertionError("Esta prueba no permite acceso a una base: " + invocation.getMethod());
        });
        var fabrica = new LocalContainerEntityManagerFactoryBean();
        fabrica.setDataSource(prohibida);
        fabrica.setPackagesToScan("com.huellago.backend.entities");
        fabrica.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        fabrica.setJpaPropertyMap(Map.of("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect",
                "hibernate.boot.allow_jdbc_metadata_access", "false", "hibernate.hbm2ddl.auto", "none",
                "jakarta.persistence.schema-generation.database.action", "none"));
        try {
            fabrica.afterPropertiesSet();
            try (EntityManager em = fabrica.getObject().createEntityManager()) {
                int consultas = 0;
                for (Class<?> repositorio : new Class<?>[] {RetoRepository.class, UsuarioRetoRepository.class,
                        ResultadoRetoColectivoRepository.class}) {
                    for (var metodo : repositorio.getDeclaredMethods()) {
                        Query query = metodo.getAnnotation(Query.class);
                        if (query != null && metodo.getName().contains("ParaReporte")) {
                            assertDoesNotThrow(() -> em.createQuery(query.value()), metodo.getName());
                            if (!query.countQuery().isEmpty()) assertDoesNotThrow(() -> em.createQuery(query.countQuery()));
                            consultas++;
                        }
                    }
                }
                // Las consultas con filtros opcionales ahora pertenecen a fragmentos Criteria.
                // Aquí permanecen los dos agregados HQL sin parámetros opcionales.
                assertEquals(2, consultas);
            }
        } finally { fabrica.destroy(); }
        verifyNoInteractions(prohibida);
    }
}
