package com.huellago.backend.serviceimpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PoliticaMedicionInicialTest {
    PoliticaMedicionInicial politica = new PoliticaMedicionInicial();
    @Test void configuracionHabitualConservaFactorElectricoAprobadoParaUsoLocal() throws Exception {
        var propiedades = new java.util.Properties();
        try (var recurso = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertNotNull(recurso);
            propiedades.load(recurso);
        }
        assertEquals("0.23825", propiedades.getProperty("huellago.medicion.electricidad.factor-kg-co2e-kwh"));
        assertTrue(propiedades.getProperty("huellago.medicion.electricidad.referencia").contains("factor provisional no oficial"));
    }
    void umbralesFicticiosDePrueba() {
        politica.limiteBajo="10"; politica.limiteMedio="20";
        politica.referencia="Politica ficticia para probar limites; sin significado cientifico";
    }
    @Test void clasificacionAcademicaSemanalYDatosInvalidos() {
        assertEquals("BAJO",politica.nivel(BigDecimal.ZERO));
        assertEquals("BAJO",politica.nivel(new BigDecimal("34.99")));
        assertEquals("MEDIO",politica.nivel(new BigDecimal("35")));
        assertEquals("MEDIO",politica.nivel(new BigDecimal("69.99")));
        assertEquals("ALTO",politica.nivel(new BigDecimal("70")));
        assertEquals("NO_DEFINIDO",politica.nivel(null));
        assertEquals("NO_DEFINIDO",politica.nivel(BigDecimal.ONE.negate()));
        assertTrue(politica.referenciaImpacto().contains("no oficiales"));
    }
    @Test void clasificaLimitesSuperioresExclusivosConPoliticaExplicita() {
        umbralesFicticiosDePrueba();
        assertEquals("BAJO",politica.nivel(BigDecimal.ZERO));
        assertEquals("BAJO",politica.nivel(new BigDecimal("9.99")));
        assertEquals("MEDIO",politica.nivel(new BigDecimal("10")));
        assertEquals("MEDIO",politica.nivel(new BigDecimal("19.99")));
        assertEquals("ALTO",politica.nivel(new BigDecimal("20")));
    }
    @ParameterizedTest @ValueSource(strings={"", "NaN", "-1", "10", "9"})
    void rechazaUmbralMedioAusenteInvalidoOIncompatible(String medio) {
        umbralesFicticiosDePrueba(); politica.limiteMedio=medio;
        assertEquals(503,assertThrows(ResponseStatusException.class,() -> politica.nivel(BigDecimal.ONE)).getStatusCode().value());
    }
    @Test void noClasificaSinReferenciaNiConvierteSinFactor() {
        umbralesFicticiosDePrueba(); politica.referencia="";
        assertThrows(ResponseStatusException.class,() -> politica.nivel(BigDecimal.ONE));
        politica.referenciaElectricidad="Fuente de prueba";
        assertThrows(ResponseStatusException.class,() -> politica.consumoElectricoSemanal(BigDecimal.ONE));
        politica.factorElectricidad="-1";
        assertThrows(ResponseStatusException.class,() -> politica.consumoElectricoSemanal(BigDecimal.ONE));
    }
    @Test void ceroConsumoYCeroFactorSonValidosSinAtribuirEmisionesInventadas() {
        politica.referenciaElectricidad="Exclusivo de prueba"; politica.factorElectricidad="0.4";
        assertEquals(0,politica.consumoElectricoSemanal(BigDecimal.ZERO).signum());
        politica.factorElectricidad="0";
        assertEquals(0,politica.consumoElectricoSemanal(BigDecimal.ONE).signum());
    }
}
