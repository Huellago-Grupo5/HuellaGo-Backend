package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.exceptions.OllamaClientException;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.*;
import static com.huellago.backend.dtos.ResultadoPresentacionRecomendacionesIADTO.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Selecciones manuales SINTÉTICAS y selector Mockito. Ninguna inferencia real ni repositorio. */
class OrquestadorRecomendacionesIAServiceImplTest {
    final GeneradorRecomendacionesIAService selector = mock(GeneradorRecomendacionesIAService.class);
    final CatalogoAccionesAmbientalesServiceImpl catalogo = new CatalogoAccionesAmbientalesServiceImpl();
    final PresentacionRecomendacionesIAServiceImpl presentador = new PresentacionRecomendacionesIAServiceImpl();
    final OrquestadorRecomendacionesIAServiceImpl servicio = new OrquestadorRecomendacionesIAServiceImpl(selector, catalogo, presentador);
    final JsonMapper mapper = JsonMapper.builder().build();

    ContextoRecomendacionesIAService.Resultado contexto(boolean completo) {
        return new ContextoRecomendacionesIAService.Resultado(new ContextoRecomendacionesIADTO(
                new ContextoRecomendacionesIADTO.Transporte("Auto", new BigDecimal("100"), 5),
                completo ? new ContextoRecomendacionesIADTO.Energia("Casa", 3, "Electricidad", new BigDecimal("12.345")) : null,
                completo ? new ContextoRecomendacionesIADTO.Alimentacion("Vegetariana") : null,
                completo ? new ContextoRecomendacionesIADTO.Residuos("Medio", false) : null),
                completo ? List.of() : List.of("Energía", "Alimentación", "Residuos"), List.of(), ReglasInterpretacionAmbiental.REGLAS);
    }
    ResultadoGeneracionFundamentadaIADTO seleccion(ContextoRecomendacionesIAService.Resultado c, boolean enfoque, boolean abstener) {
        var candidatas = catalogo.compatibles(c.contexto(), Set.of());
        var hechos = ContratoOllamaV2.hechos(ValidadorRecomendacionesIA.contexto(c.contexto()));
        List<Propuesta> propuestas = new ArrayList<>(); Set<String> categorias = new HashSet<>();
        for (var candidata : candidatas) {
            var a = candidata.definicion(); if (!categorias.add(a.categoria())) continue;
            if (abstener) {
                propuestas.add(new Propuesta(a.categoria(), Decision.ABSTENERSE, null, List.of(), null, null, null, List.of(), MotivoAbstencion.NO_VERIFICABLE));
                continue;
            }
            var evidencias = a.evidenciasRequeridas().stream().sorted().map(r -> {
                var h = mapper.readTree(mapper.writeValueAsString(hechos.get(r)));
                return new Evidencia(r, mapper.readValue(mapper.writeValueAsString(h.get("valor")), Valor.class), Ambito.valueOf(h.get("ambito").asString()));
            }).toList();
            var parametros = enfoque && a.id().equals("TR_PLANIFICAR_DESPLAZAMIENTOS")
                    ? Map.<String, Valor>of("enfoque", new Texto("AGRUPACION")) : Map.<String, Valor>of();
            propuestas.add(new Propuesta(a.categoria(), Decision.PROPONER, new Accion(a.id(), a.version(), parametros), evidencias, null, null, null, List.of(), null));
        }
        var propuesta = new RecomendacionFundamentadaIADTO("2", propuestas);
        return new ResultadoGeneracionFundamentadaIADTO(mapper.writeValueAsString(propuesta), propuesta,
                new ResultadoEvaluacionRecomendacionIADTO(ACEPTADA_ESTRUCTURALMENTE, List.of(), List.of()));
    }
    @Test void cuatroCategoriasConservanSeleccionEvidenciasYBorradores() {
        var c = contexto(true); var s = seleccion(c, false, false);
        String antes = mapper.writeValueAsString(s);
        when(selector.generarV2ConAlcanceParaPruebasSinteticas(c, catalogo.compatibles(c.contexto(), Set.of()), Set.of())).thenReturn(s);
        var r = servicio.orquestarParaPruebasSinteticas(c, catalogo.compatibles(c.contexto(), Set.of()), Set.of());
        assertSame(s, r.seleccion()); assertEquals(Estado.SELECCION_ACEPTADA, r.presentacion().estado());
        assertEquals(4, r.presentacion().resultados().size()); assertFalse(r.publicable());
        for (var d : r.presentacion().resultados()) {
            assertTrue(s.propuesta().resultados().stream().anyMatch(p -> p == d.seleccionOriginal()));
            assertEquals(EstadoDetalle.VISTA_PREVIA_BORRADOR, d.estado());
            assertEquals("SIN_ENFOQUE", d.presentacion().enfoque()); assertFalse(d.presentacion().enfoqueSeleccionado());
            assertEquals(Revision.BORRADOR, d.presentacion().estado());
            assertEquals(PENDIENTE_REVISION, d.presentacion().revisionHumana()); assertFalse(d.presentacion().publicable());
        }
        assertEquals(antes, mapper.writeValueAsString(s));
        verify(selector).generarV2ConAlcanceParaPruebasSinteticas(c, catalogo.compatibles(c.contexto(), Set.of()), Set.of()); verifyNoMoreInteractions(selector);
    }
    @Test void parcialesConservanEnfoquePermitidoSinRellenarCategorias() {
        var c = contexto(false); var s = seleccion(c, true, false);
        when(selector.generarV2ConAlcanceParaPruebasSinteticas(c, catalogo.compatibles(c.contexto(), Set.of()), Set.of())).thenReturn(s);
        var r = servicio.orquestarParaPruebasSinteticas(c, catalogo.compatibles(c.contexto(), Set.of()), Set.of());
        assertEquals(1, r.presentacion().resultados().size());
        assertEquals("AGRUPACION", r.presentacion().resultados().getFirst().presentacion().enfoque());
        assertEquals(s.propuesta().resultados().getFirst().accion(), r.presentacion().resultados().getFirst().seleccionOriginal().accion());
    }
    @Test void abstencionesDelSelectorNoCreanContenido() {
        var c = contexto(true); var s = seleccion(c, false, true);
        when(selector.generarV2ConAlcanceParaPruebasSinteticas(c, catalogo.compatibles(c.contexto(), Set.of()), Set.of())).thenReturn(s);
        var r = servicio.orquestarParaPruebasSinteticas(c, catalogo.compatibles(c.contexto(), Set.of()), Set.of());
        assertEquals(4, r.presentacion().resultados().size());
        assertTrue(r.presentacion().resultados().stream().allMatch(d -> d.estado() == EstadoDetalle.ABSTENCION_V2 && d.presentacion() == null));
    }
    @Test void todasRealizadasSinSelectorNiAbstencionFabricada() {
        var c = contexto(false); Set<AccionAmbientalAdmisible.Referencia> realizadas = new HashSet<>();
        catalogo.compatibles(c.contexto(), Set.of()).forEach(a -> realizadas.add(new AccionAmbientalAdmisible.Referencia(a.definicion().id(), a.definicion().version())));
        var r = servicio.orquestarParaPruebasSinteticas(c, List.of(), realizadas);
        assertEquals(Estado.SIN_PROPUESTA, r.presentacion().estado());
        assertNull(r.seleccion().propuestaJson()); assertNull(r.seleccion().propuesta()); assertFalse(r.publicable());
        verifyNoInteractions(selector);
    }
    @Test void subconjuntoVacioNoSolicitaInferencia() {
        var c = contexto(false);
        assertEquals(Estado.SIN_PROPUESTA, servicio.orquestarParaPruebasSinteticas(c, List.of(), Set.of()).presentacion().estado());
        verifyNoInteractions(selector);
    }
    @Test void propuestaConEvidenciasAlteradasSeRechazaSinRepararla() {
        var c = contexto(false); var original = seleccion(c, false, false);
        String alterado = original.propuestaJson().replace("100", "999");
        var s = new ResultadoGeneracionFundamentadaIADTO(alterado, mapper.readValue(alterado, RecomendacionFundamentadaIADTO.class), original.evaluacion());
        when(selector.generarV2ConAlcanceParaPruebasSinteticas(c, catalogo.compatibles(c.contexto(), Set.of()), Set.of())).thenReturn(s);
        var r = servicio.orquestarParaPruebasSinteticas(c, catalogo.compatibles(c.contexto(), Set.of()), Set.of());
        assertSame(s, r.seleccion()); assertEquals(Estado.SELECCION_RECHAZADA, r.presentacion().estado());
        assertTrue(r.presentacion().resultados().isEmpty()); assertFalse(r.publicable());
    }
    @ParameterizedTest @EnumSource(value = OllamaClientException.Tipo.class,
            names = {"NO_DISPONIBLE", "TIEMPO_AGOTADO", "ERROR_HTTP", "RESPUESTA_INVALIDA"})
    void erroresTipadosSePropaganSinAbstencionNiFallback(OllamaClientException.Tipo tipo) {
        var c = contexto(false); var error = new OllamaClientException(tipo, "Error sintético");
        when(selector.generarV2ConAlcanceParaPruebasSinteticas(c, catalogo.compatibles(c.contexto(), Set.of()), Set.of())).thenThrow(error);
        assertSame(error, assertThrows(OllamaClientException.class,
                () -> servicio.orquestarParaPruebasSinteticas(c, catalogo.compatibles(c.contexto(), Set.of()), Set.of())));
        verify(selector).generarV2ConAlcanceParaPruebasSinteticas(c, catalogo.compatibles(c.contexto(), Set.of()), Set.of()); verifyNoMoreInteractions(selector);
    }
    @Test void transaccionActivaInclusoSoloLecturaSeRechazaAntesDelSelector() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        try {
            var c = contexto(false);
            assertThrows(IllegalStateException.class, () -> servicio.orquestarParaPruebasSinteticas(c, List.of(), Set.of()));
            verifyNoInteractions(selector);
        } finally { TransactionSynchronizationManager.clear(); }
    }
    @Test void respuestaNulaNoSeConvierteEnAbstencion() {
        var c = contexto(false);
        var r = servicio.orquestarParaPruebasSinteticas(c, catalogo.compatibles(c.contexto(), Set.of()), Set.of());
        assertEquals(Estado.SELECCION_RECHAZADA, r.presentacion().estado()); assertFalse(r.publicable());
    }
    @Test void sinPresentacionSeConservaSinElegirOtraAccion() {
        var proyector = mock(PresentacionRecomendacionesIAService.class);
        var coordinador = new OrquestadorRecomendacionesIAServiceImpl(selector, catalogo, proyector);
        var c = contexto(false); var s = seleccion(c, false, false);
        var candidatas = catalogo.compatibles(c.contexto(), Set.of());
        var sin = new ResultadoPresentacionRecomendacionesIADTO(Estado.SELECCION_ACEPTADA, s.evaluacion(), List.of(
                new Detalle(s.propuesta().resultados().getFirst(), EstadoDetalle.SIN_PRESENTACION,
                        Motivo.COMBINACION_NO_DEFINIDA, null)));
        when(selector.generarV2ConAlcanceParaPruebasSinteticas(c, catalogo.compatibles(c.contexto(), Set.of()), Set.of())).thenReturn(s);
        when(proyector.presentarConAlcanceParaPruebasSinteticas(c.contexto(), s, new ValidadorRecomendacionFundamentadaIA().construirAlcanceExperimental(c.contexto(), candidatas, Set.of()), Set.of())).thenReturn(sin);
        var r = coordinador.orquestarParaPruebasSinteticas(c, candidatas, Set.of());
        assertSame(sin, r.presentacion()); assertSame(s, r.seleccion()); assertFalse(r.publicable());
        assertNull(r.presentacion().resultados().getFirst().presentacion());
        verify(selector).generarV2ConAlcanceParaPruebasSinteticas(c, catalogo.compatibles(c.contexto(), Set.of()), Set.of()); verifyNoMoreInteractions(selector);
    }
    @Test void contextoYPoliticaInvalidosNoAlcanzanSelector() {
        assertThrows(OllamaClientException.class, () -> servicio.orquestarParaPruebasSinteticas(null, List.of(), Set.of()));
        var c = contexto(false);
        var alterado = new ContextoRecomendacionesIAService.Resultado(c.contexto(), List.of("Transporte"), List.of(), c.reglasInterpretacion());
        assertThrows(OllamaClientException.class, () -> servicio.orquestarParaPruebasSinteticas(alterado, List.of(), Set.of()));
        verifyNoInteractions(selector);
    }
}
