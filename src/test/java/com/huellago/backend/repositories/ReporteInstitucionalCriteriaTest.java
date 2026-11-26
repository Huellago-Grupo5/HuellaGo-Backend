package com.huellago.backend.repositories;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.*;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Hibernate real genera SQL PostgreSQL y enlaza parámetros sobre JDBC simulado, sin red ni base real. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReporteInstitucionalCriteriaTest {
    LocalContainerEntityManagerFactoryBean fabrica;
    final List<String> sentencias = new ArrayList<>();
    final List<Object> enlaces = new ArrayList<>();
    final List<String> setters = new ArrayList<>();
    Connection conexion;
    boolean sinFilas;
    final LocalDate desde = LocalDate.of(2026, 11, 2), hasta = LocalDate.of(2026, 11, 5);

    @BeforeAll void iniciarSinBaseDeDatos() throws Exception {
        conexion = mock(Connection.class, inv -> {
            if (inv.getMethod().getName().equals("prepareStatement")) return preparar(inv.getArgument(0));
            return RETURNS_DEFAULTS.answer(inv);
        });
        when(conexion.getAutoCommit()).thenReturn(true);
        DataSource fuente = mock(DataSource.class);
        when(fuente.getConnection()).thenReturn(conexion); // Solo objetos Mockito, nunca DriverManager/JDBC real.
        fabrica = new LocalContainerEntityManagerFactoryBean();
        fabrica.setDataSource(fuente);
        fabrica.setPackagesToScan("com.huellago.backend.entities");
        fabrica.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        fabrica.setJpaPropertyMap(Map.of("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect",
                "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl",
                "hibernate.boot.allow_jdbc_metadata_access", "false", "hibernate.hbm2ddl.auto", "none",
                "jakarta.persistence.schema-generation.database.action", "none"));
        fabrica.afterPropertiesSet();
    }
    @BeforeEach void limpiar() { sentencias.clear(); enlaces.clear(); setters.clear(); sinFilas = false; }
    @AfterAll void cerrar() { if (fabrica != null) fabrica.destroy(); }

    PreparedStatement preparar(String sql) throws Exception {
        sentencias.add(sql.toLowerCase(Locale.ROOT));
        boolean agregado = sql.toLowerCase(Locale.ROOT).contains("count(");
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(agregado || !sinFilas, false);
        when(rs.getLong(anyInt())).thenReturn(sinFilas ? 0L : 1L);
        when(rs.getInt(1)).thenReturn(6);
        when(rs.getString(2)).thenReturn("Reciclaje en equipo");
        when(rs.getString(3)).thenReturn("Reducir residuos");
        when(rs.getDate(anyInt())).thenAnswer(inv -> java.sql.Date.valueOf(inv.<Integer>getArgument(0) == 4 ? desde : hasta));
        when(rs.getObject(anyInt(), eq(LocalDate.class))).thenAnswer(inv -> inv.<Integer>getArgument(0) == 4 ? desde : hasta);
        when(rs.getBoolean(6)).thenReturn(true);
        PreparedStatement ps = mock(PreparedStatement.class, inv -> {
            String nombre = inv.getMethod().getName();
            if (nombre.startsWith("set") && inv.getArguments().length >= 2) {
                setters.add(nombre); enlaces.add(inv.getArgument(1));
            }
            return RETURNS_DEFAULTS.answer(inv);
        });
        when(ps.executeQuery()).thenReturn(rs);
        return ps;
    }

    @ParameterizedTest(name = "combinación de filtros {0}: selección, conteo y totales")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void soloGeneraPredicadosPresentesConTiposYLimitesCorrectos(int mascara) throws Exception {
        LocalDate d = (mascara & 1) != 0 ? desde : null;
        LocalDate h = (mascara & 2) != 0 ? hasta : null;
        Integer id = (mascara & 4) != 0 ? 6 : null;
        try (EntityManager em = fabrica.getObject().createEntityManager()) {
            // A través de fragmentos reales: el resultado también se transforma a las proyecciones reales.
            var pagina = new RetoReporteRepositoryImpl(em).buscarParaReporte(1L, d, h, id,
                    PageRequest.of(0, 250, Sort.by("id")));
            assertEquals(1, pagina.getTotalElements()); assertEquals(1, pagina.getContent().size());
            var campana = pagina.getContent().getFirst();
            assertEquals(6, campana.getId()); assertEquals("Reciclaje en equipo", campana.getTitulo());
            assertEquals(desde, campana.getFechaInicio()); assertEquals(hasta, campana.getFechaFin());
            var totales = new UsuarioRetoReporteRepositoryImpl(em).totalesParaReporte(1L, d, h, id);
            assertEquals(1L, totales.getTotal()); assertEquals(1L, totales.getUsuarios());
        }
        assertEquals(3, sentencias.size()); // selección + count de página + totales de participaciones
        for (String sql : sentencias) {
            assertFalse(sql.contains(" is null"), sql); assertFalse(sql.contains(" or "), sql);
            assertTrue(sql.contains("institucion_id=?"), sql); assertTrue(sql.contains("tipo=?"), sql);
            assertEquals(d != null, sql.contains("fecha_fin>=?"), sql);
            assertEquals(h != null, sql.contains("fecha_inicio<=?"), sql);
            assertEquals(id != null, sql.matches("(?s).*\\br\\d+_\\d+\\.id=\\?.*"), sql);
        }
        assertTrue(sentencias.getFirst().contains("order by"));
        assertTrue(sentencias.getFirst().contains("fetch first ? rows only"));
        assertTrue(sentencias.get(1).contains("count("));
        assertTrue(sentencias.get(2).contains("count(distinct"));
        assertFalse(setters.contains("setNull")); assertFalse(enlaces.contains(null));
        assertEquals(d != null, enlaces.contains(java.sql.Date.valueOf(desde)) || enlaces.contains(desde));
        assertEquals(h != null, enlaces.contains(java.sql.Date.valueOf(hasta)) || enlaces.contains(hasta));
        verify(conexion, never()).createStatement();
    }

    @Test void periodoSinCampanasConservaPaginaVaciaYConteoCero() {
        sinFilas = true;
        try (EntityManager em = fabrica.getObject().createEntityManager()) {
            var pagina = new RetoReporteRepositoryImpl(em).buscarParaReporte(1L, desde, hasta, null,
                    PageRequest.of(0, 250, Sort.by("id")));
            assertTrue(pagina.isEmpty()); assertEquals(0, pagina.getTotalElements());
            var totales = new UsuarioRetoReporteRepositoryImpl(em).totalesParaReporte(1L, desde, hasta, null);
            assertEquals(0L, totales.getTotal()); assertEquals(0L, totales.getUsuarios());
        }
        assertEquals(3, sentencias.size());
    }
    @Test void conservaOffsetYOrdenDeLaPaginaSolicitada() {
        sinFilas = true;
        try (EntityManager em = fabrica.getObject().createEntityManager()) {
            new RetoReporteRepositoryImpl(em).buscarParaReporte(1L, desde, hasta, 6,
                    PageRequest.of(2, 250, Sort.by(Sort.Direction.DESC, "id")));
        }
        assertTrue(sentencias.getFirst().contains("desc"));
        assertTrue(sentencias.getFirst().contains("offset ? rows"));
        assertTrue(enlaces.contains(500)); assertTrue(enlaces.contains(250));
    }
}
