package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class ImpactoHipoteticoIATest {
    ContextoRecomendacionesIADTO contexto = new ContextoRecomendacionesIADTO(
            new ContextoRecomendacionesIADTO.Transporte("Auto", new BigDecimal("100"), 5),
            new ContextoRecomendacionesIADTO.Energia("Casa", 3, "Electricidad", new BigDecimal("10")),
            null, null);
    PoliticaMedicionInicial politica() {
        var politica = new PoliticaMedicionInicial();
        ReflectionTestUtils.setField(politica, "factorElectricidad", "0.23825");
        ReflectionTestUtils.setField(politica, "referenciaElectricidad", "Factor académico registrado");
        return politica;
    }
    RecomendacionPersonalizadaDTO recomendacion() {
        var dto = new RecomendacionPersonalizadaDTO(); dto.indicarImpactoNoEstimable(); return dto;
    }
    @Test void evitaKmAutoConFactorRealYNoAfirmaAhorroConseguido() {
        var escenario = new EscenarioImpactoIADTO(new BigDecimal("10"), null, null);
        ImpactoHipoteticoIA.validar(escenario, contexto);
        var dto = recomendacion();
        ImpactoHipoteticoIA.aplicar(dto, "TR_PLANIFICAR_DESPLAZAMIENTOS", escenario, politica());
        assertEquals(new BigDecimal("1.20"), dto.getImpactoPotencialKgCo2eSemanal());
        assertEquals("ESTIMACION_HIPOTETICA", dto.getImpactoEsperado());
        assertTrue(dto.getLimitacionesImpacto().contains("No acredita ahorro realizado"));
        assertTrue(dto.getMetodologiaImpacto().contains("0.12"));
    }
    @Test void sustitucionHipoteticaPorBicicletaUsaLosDosFactoresVigentes() {
        var dto = recomendacion();
        ImpactoHipoteticoIA.aplicar(dto, "TR_EXPLORAR_ALTERNATIVAS_AUTO",
                new EscenarioImpactoIADTO(null, new BigDecimal("5"), null), politica());
        assertEquals(new BigDecimal("0.60"), dto.getImpactoPotencialKgCo2eSemanal());
        assertTrue(dto.getLimitacionesImpacto().contains("viabilidad"));
    }
    @Test void consumoDelHogarNoSeDividePorPersonas() {
        var dto = recomendacion();
        ImpactoHipoteticoIA.aplicar(dto, "EN_REVISAR_USOS",
                new EscenarioImpactoIADTO(null, null, new BigDecimal("2")), politica());
        assertEquals(new BigDecimal("3.34"), dto.getImpactoPotencialKgCo2eSemanal());
        assertTrue(dto.getMetodologiaImpacto().contains("0.23825"));
        assertTrue(dto.getLimitacionesImpacto().contains("hogar completo"));
    }
    @Test void accionesSinCambioCuantificadoConservanNoEstimable() {
        var dto = recomendacion();
        ImpactoHipoteticoIA.aplicar(dto, "RE_INICIAR_SEPARACION",
                new EscenarioImpactoIADTO(new BigDecimal("10"), null, null), politica());
        assertEquals("NO_ESTIMABLE", dto.getImpactoEsperado());
        assertNull(dto.getImpactoPotencialKgCo2eSemanal());
    }
    @Test void rechazaEscenariosMayoresAlHabitoYNoPermiteDobleContarKm() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> ImpactoHipoteticoIA.validar(
                new EscenarioImpactoIADTO(new BigDecimal("60"), new BigDecimal("50"), null), contexto)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> ImpactoHipoteticoIA.validar(
                new EscenarioImpactoIADTO(null, null, new BigDecimal("11")), contexto)).getStatusCode().value());
    }
    @Test void rechazaKilometrosDeOtroMedioYValoresNegativos() {
        var bus = new ContextoRecomendacionesIADTO(
                new ContextoRecomendacionesIADTO.Transporte("Bus", new BigDecimal("100"), 5), null, null, null);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> ImpactoHipoteticoIA.validar(
                new EscenarioImpactoIADTO(new BigDecimal("5"), null, null), bus)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> ImpactoHipoteticoIA.validar(
                new EscenarioImpactoIADTO(new BigDecimal("-1"), null, null), contexto)).getStatusCode().value());
    }
}
