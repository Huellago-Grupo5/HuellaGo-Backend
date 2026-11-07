package com.huellago.backend;

import com.huellago.backend.dtos.*;
import com.huellago.backend.security.BasePruebasGuard;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in. Solo filas propias, en transacciones revertidas; nunca usa el usuario 64. */
@EnabledIfSystemProperty(named="huellago.habitos.realDb",matches="true")
@SpringBootTest(properties={
        "spring.datasource.url=jdbc:postgresql://localhost:5432/huellagodb_hu29_test",
        "spring.sql.init.mode=never","spring.jpa.hibernate.ddl-auto=validate",
        "spring.mail.test-connection=false","huellago.recovery.enabled=false",
        "huellago.cierre-colectivo.enabled=false"})
@ContextConfiguration(initializers=BasePruebasGuard.class)
@Transactional
@Rollback
class HabitosInicialesPostgresTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioService usuarios;
    @Autowired HabitoService habitos;
    @Autowired HuellaCarbonoService huellas;
    @Autowired jakarta.persistence.EntityManager entityManager;
    Long id;

    @BeforeEach void crearSoloUsuarioPropio() {
        assertEquals("huellagodb_hu29_test",jdbc.queryForObject("SELECT current_database()",String.class));
        id=usuarios.registrar(new UsuarioRegistroDTO("Prueba","Habitos",
                "hu08-11-"+UUID.randomUUID()+"@example.invalid","SoloPruebas!2026")).getId();
        assertNotEquals(64L,id);
    }
    void transporteYEnergia() {
        habitos.registrarTransporte(new TransporteDTO(id,"Bus",new BigDecimal("120"),5));
        habitos.registrarEnergia(new EnergiaDTO(id,"Casa",3,"Electricidad"));
    }
    @Test void persisteCategoriasYTotalConsistenteEnTransaccionRevertida() {
        transporteYEnergia();
        var estado=habitos.registrarAlimentacionResiduos(new AlimentacionResiduosDTO(id,"Mixta","Medio",true));
        assertTrue(estado.isConfiguracionInicialCompleta());
        var resultado=huellas.calcularHuellaInicial(id);
        entityManager.flush(); entityManager.clear();
        assertEquals(9,jdbc.queryForObject("SELECT count(*) FROM habitos WHERE usuario_id=?",Integer.class,id));
        assertEquals(0,new BigDecimal("10.5").compareTo(resultado.getCo2Total()));
        assertEquals(0,resultado.getCo2Total().compareTo(jdbc.queryForObject(
                "SELECT co2_total FROM huellas_carbono WHERE usuario_id=?",BigDecimal.class,id)));
    }
    @Test void duplicadoNoInsertaFilasAdicionales() {
        transporteYEnergia();
        assertEquals(409,assertThrows(ResponseStatusException.class,() ->
                habitos.registrarTransporte(new TransporteDTO(id,"Auto",BigDecimal.TEN,7))).getStatusCode().value());
        assertEquals(6,jdbc.queryForObject("SELECT count(*) FROM habitos WHERE usuario_id=?",Integer.class,id));
    }
}
