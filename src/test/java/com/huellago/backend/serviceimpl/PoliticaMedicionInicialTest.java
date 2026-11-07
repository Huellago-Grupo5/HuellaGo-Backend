package com.huellago.backend.serviceimpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PoliticaMedicionInicialTest {
    PoliticaMedicionInicial politica = new PoliticaMedicionInicial();
    void umbralesFicticiosDePrueba() {
        politica.limiteBajo="10"; politica.limiteMedio="20";
        politica.referencia="Politica ficticia para probar limites; sin significado cientifico";
    }
    @Test void noInventaUnNivelSinUmbralesAprobados() {
        assertEquals("NO_DEFINIDO",politica.nivel(BigDecimal.ZERO));
        assertEquals("NO_DEFINIDO",politica.nivel(null));
        assertEquals("NO_DEFINIDO",politica.nivel(BigDecimal.ONE.negate()));
    }
    @Test void clasificaLimitesInclusivosConPoliticaExplicita() {
        umbralesFicticiosDePrueba();
        assertEquals("BAJO",politica.nivel(BigDecimal.ZERO));
        assertEquals("BAJO",politica.nivel(new BigDecimal("10")));
        assertEquals("MEDIO",politica.nivel(new BigDecimal("10.01")));
        assertEquals("MEDIO",politica.nivel(new BigDecimal("20")));
        assertEquals("ALTO",politica.nivel(new BigDecimal("20.01")));
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
