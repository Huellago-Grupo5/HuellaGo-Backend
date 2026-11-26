package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.HuellaCarbonoRepository;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.huellago.backend.services.ContextoRecomendacionesIAService.*;

/** Lectura real del constructor con repositorios simulados. Ninguna base ni nueva formula. */
class HuellaContexto135Test {
    final CapturaContextoAmbientalIAServiceImplTest f=new CapturaContextoAmbientalIAServiceImplTest();
    final HuellaCarbonoRepository huellas=mock(HuellaCarbonoRepository.class);
    final ContextoRecomendacionesIAServiceImpl constructor=new ContextoRecomendacionesIAServiceImpl(f.habitos,huellas);
    HuellaCarbono huella;
    @BeforeEach void preparar() {
        f.preparar(); huella=new HuellaCarbono(); huella.setId(50L); huella.setUsuario(f.usuario);
        huella.setFechaCalculo(LocalDateTime.of(2026,11,8,12,0));
        huella.setCo2Transporte(new BigDecimal("12")); huella.setCo2Energia(new BigDecimal("20"));
        huella.setCo2Alimentacion(new BigDecimal("3")); huella.setCo2Residuos(new BigDecimal("5")); huella.setCo2Total(new BigDecimal("40"));
        when(huellas.findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(68L)).thenAnswer(i->huella);
    }
    @Test void vigenteConservaComponentesSinAfirmarComparabilidadHogarPersona() {
        var r=constructor.construir(68L); var h=r.resumenHuella();
        assertEquals(Vigencia.VIGENTE,h.vigencia()); assertEquals(50L,h.referencia()); assertEquals(4,h.componentes().size());
        assertEquals(new BigDecimal("20"),h.componentes().get("Energía"));
        assertEquals("kg CO2e",h.unidad()); assertEquals("semanal",h.periodo());
        assertEquals(Comparabilidad.PERIMETROS_NO_COMPARABLES,h.comparabilidad()); assertTrue(h.focos().isEmpty());
        assertEquals(List.of("Energía","Transporte","Residuos","Alimentación"),
                h.referenciasNumericas().stream().map(ReferenciaNumerica::categoria).toList());
        assertEquals(Perimetro.HOGAR_SEGUN_CONTEXTO,h.perimetros().get("Energía"));
        assertTrue(h.limitaciones().contains(Limitacion.ORIGEN_HISTORICO_NO_TRAZADO));
        assertEquals(new BigDecimal("12.345"),r.contexto().energia().consumoKwhDiario());
        assertThrows(UnsupportedOperationException.class,()->h.componentes().clear());
        assertThrows(UnsupportedOperationException.class,()->h.perimetros().clear());
        assertThrows(UnsupportedOperationException.class,()->h.limitaciones().clear());
        verify(huellas).findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(68L); verifyNoMoreInteractions(huellas);
    }
    @Test void inexistenteNoFabricaCerosNiInvalidaHabitos() {
        huella=null; var r=constructor.construir(68L);
        assertEquals(Vigencia.INEXISTENTE,r.resumenHuella().vigencia()); assertTrue(r.resumenHuella().componentes().isEmpty());
        assertNull(r.resumenHuella().referencia()); assertEquals(4,r.categoriasValidas().size());
    }
    @ParameterizedTest @ValueSource(strings={"ajena","sinPropietario","sinId"})
    void identidadInvalidaNoTrasladaInformacionDeOtraFila(String caso) {
        if(caso.equals("ajena")) { var otro=new Usuario(); otro.setId(999L); huella.setUsuario(otro); }
        if(caso.equals("sinPropietario")) huella.setUsuario(null);
        if(caso.equals("sinId")) huella.setId(null);
        var h=constructor.construir(68L).resumenHuella();
        assertEquals(Vigencia.INVALIDA,h.vigencia()); assertTrue(h.componentes().isEmpty());
        assertNull(h.referencia()); assertNull(h.fecha()); assertNull(h.totalRegistrado());
    }
    @ParameterizedTest @ValueSource(strings={"negativo","nulo","totalNegativo","sinTotal","sinFecha"})
    void invalidaConservaSoloComponentesPresentesValidos(String caso) {
        switch(caso) { case "negativo" -> huella.setCo2Transporte(new BigDecimal("-1"));
            case "nulo" -> huella.setCo2Energia(null); case "totalNegativo" -> huella.setCo2Total(new BigDecimal("-1"));
            case "sinTotal" -> huella.setCo2Total(null); default -> huella.setFechaCalculo(null); }
        var h=constructor.construir(68L).resumenHuella(); assertEquals(Vigencia.INVALIDA,h.vigencia());
        assertTrue(h.focos().isEmpty()); assertTrue(h.componentes().values().stream().allMatch(v->v.signum()>=0));
        if(caso.equals("nulo")) assertFalse(h.componentes().containsKey("Energía"));
        if(caso.equals("negativo")) assertFalse(h.componentes().containsKey("Transporte"));
    }
    @Test void obsoletaAlEditarUnHabitoSeleccionado() {
        f.campo("kmSemana").setFechaActualizacion(huella.getFechaCalculo().plusSeconds(1));
        var h=constructor.construir(68L).resumenHuella(); assertEquals(Vigencia.OBSOLETA,h.vigencia());
        assertEquals(new BigDecimal("12"),h.componentes().get("Transporte")); assertTrue(h.focos().isEmpty());
        assertTrue(h.referenciasNumericas().isEmpty());
    }
    @Test void fechaHabitoDesconocidaNoAcreditaVigencia() {
        f.campo("kmSemana").setFechaRegistro(null);
        assertEquals(Vigencia.NO_VERIFICABLE,constructor.construir(68L).resumenHuella().vigencia());
    }
    @Test void parcialPreservaDiagnosticoSinConvertirAusenciaEnBajoImpacto() {
        f.datos.remove(f.campo("tipo")); var r=constructor.construir(68L);
        assertEquals(List.of("Alimentación"),r.categoriasIncompletas()); assertNull(r.contexto().alimentacion());
        assertTrue(r.observaciones().contains("Alimentación.tipo: dato obligatorio ausente"));
        assertTrue(r.resumenHuella().limitaciones().contains(Limitacion.CONTEXTO_PARCIAL));
        assertEquals(new BigDecimal("3"),r.resumenHuella().componentes().get("Alimentación")); assertTrue(r.resumenHuella().focos().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"cero","empate","totalIndependiente","sinConsumo"})
    void noInventaFocoNiRechazaRedondeoHistorico(String caso) {
        switch(caso) {
            case "cero" -> { huella.setCo2Transporte(BigDecimal.ZERO); huella.setCo2Energia(BigDecimal.ZERO);
                huella.setCo2Alimentacion(BigDecimal.ZERO); huella.setCo2Residuos(BigDecimal.ZERO); huella.setCo2Total(BigDecimal.ZERO); }
            case "empate" -> { huella.setCo2Transporte(new BigDecimal("20")); huella.setCo2Total(new BigDecimal("48")); }
            case "totalIndependiente" -> huella.setCo2Total(new BigDecimal("40.01"));
            default -> f.datos.remove(f.campo("consumoKwhDiario"));
        }
        var h=constructor.construir(68L).resumenHuella(); assertEquals(Vigencia.VIGENTE,h.vigencia()); assertTrue(h.focos().isEmpty());
        if(caso.equals("cero")) assertEquals(4,h.componentes().size());
        if(caso.equals("empate")) assertEquals(List.of("Energía","Transporte"),
                h.referenciasNumericas().stream().limit(2).map(ReferenciaNumerica::categoria).toList());
        if(caso.equals("sinConsumo")) assertEquals(Comparabilidad.NO_ACREDITADA,h.comparabilidad());
    }
    @Test void composicionAcademicaInyectaRepositorioRealSinArrancarContexto() {
        var c=new AplicacionDemoIA127.Servicios().constructor(f.habitos,huellas);
        assertEquals(Vigencia.VIGENTE,c.construir(68L).resumenHuella().vigencia());
    }
}
