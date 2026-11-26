package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.services.*;
import com.huellago.backend.services.PoliticaAutorizacionEditorialIAService.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.*;
import static com.huellago.backend.dtos.ResultadoPresentacionRecomendacionesIADTO.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Decisión editorial real limitada a test; contexto y respuestas del selector SINTÉTICOS.
 * No acredita inferencia real de Qwen3 ni autoriza datos productivos. */
class AutorizacionPruebaInternaPaso114Test {
    static final String ID = "TR_PLANIFICAR_DESPLAZAMIENTOS";
    final CatalogoAccionesAmbientalesServiceImpl catalogo = new CatalogoAccionesAmbientalesServiceImpl();
    final PresentacionRecomendacionesIAServiceImpl presentador = new PresentacionRecomendacionesIAServiceImpl();
    final RegistroEditorialPruebaInternaFixture registro = new RegistroEditorialPruebaInternaFixture();
    final PoliticaAutorizacionEditorialIAServiceImpl politica = new PoliticaAutorizacionEditorialIAServiceImpl(catalogo,presentador,registro);
    final GeneradorRecomendacionesIAService selector = mock(GeneradorRecomendacionesIAService.class);
    final OrquestadorRecomendacionesIAServiceImpl orquestador = new OrquestadorRecomendacionesIAServiceImpl(selector,catalogo,presentador);
    final ContextoRecomendacionesIADTO contexto = new ContextoRecomendacionesIADTO(
            new ContextoRecomendacionesIADTO.Transporte("Auto",new BigDecimal("100"),5),null,null,null);
    final ContextoRecomendacionesIAService.Resultado entrada = new ContextoRecomendacionesIAService.Resultado(contexto,
            List.of("Energía","Alimentación","Residuos"),List.of(),ReglasInterpretacionAmbiental.REGLAS);
    final JsonMapper mapper = JsonMapper.builder().build();
    ResultadoGeneracionFundamentadaIADTO propuesta(String id,String version,String enfoque) {
        var p = new Propuesta("Transporte",Decision.PROPONER,new Accion(id,version,
                enfoque == null ? Map.of() : Map.of("enfoque",new Texto(enfoque))),List.of(
                new Evidencia("/transporte/medio",new Texto("Auto"),Ambito.NO_APLICA),
                new Evidencia("/transporte/kmSemana",new Numero(new BigDecimal("100")),Ambito.NO_APLICA),
                new Evidencia("/transporte/diasSemana",new Numero(new BigDecimal("5")),Ambito.NO_APLICA)),
                null,null,null,List.of(),null);
        return simulada(p);
    }
    ResultadoGeneracionFundamentadaIADTO simulada(Propuesta p) {
        var contrato = new RecomendacionFundamentadaIADTO("2",List.of(p));
        // Etiqueta de mock: el presentador debe revalidar, nunca confiar en esta etiqueta.
        return new ResultadoGeneracionFundamentadaIADTO(mapper.writeValueAsString(contrato),contrato,
                new ResultadoEvaluacionRecomendacionIADTO(ACEPTADA_ESTRUCTURALMENTE,List.of(),List.of()));
    }
    @Test void elegibilidadExactaSinModificarContextoCatalogoNiProduccion() {
        String c=mapper.writeValueAsString(contexto), defs=mapper.writeValueAsString(presentador.definicionesParaRevision());
        var r=politica.evaluar(contexto,Set.of());
        assertEquals(2,r.compatibles().size()); assertEquals(1,r.elegibles().size());
        assertEquals(ID,r.elegibles().getFirst().definicion().id()); assertEquals("1",r.elegibles().getFirst().definicion().version());
        assertTrue(r.excluidas().containsKey(new AccionAmbientalAdmisible.Referencia("TR_EXPLORAR_ALTERNATIVAS_AUTO","1")));
        assertEquals(Set.of(ID),registro.consultar().stream().map(Autorizacion::accionId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(c,mapper.writeValueAsString(contexto)); assertEquals(defs,mapper.writeValueAsString(presentador.definicionesParaRevision()));
        assertFalse(r.publicable()); assertTrue(new PoliticaAutorizacionEditorialIAServiceImpl().evaluar(contexto,Set.of()).elegibles().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> registro.consultar().clear());
    }
    @ParameterizedTest @ValueSource(ints={1,2})
    void familiaParcialNoHabilita(int cantidad) {
        var p=new PoliticaAutorizacionEditorialIAServiceImpl(catalogo,presentador,() -> registro.consultar().subList(0,cantidad));
        assertTrue(p.evaluar(contexto,Set.of()).elegibles().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"SIN_ENFOQUE","AGRUPACION","NECESIDAD"})
    void seleccionSimuladaSoloUsaVarianteExactaAutorizada(String enfoque) {
        var permitidas=politica.evaluar(contexto,Set.of()).elegibles();
        var s=propuesta(ID,"1",enfoque.equals("SIN_ENFOQUE")?null:enfoque);
        when(selector.generarV2ConAlcanceParaPruebasSinteticas(entrada,permitidas,Set.of())).thenReturn(s);
        var r=orquestador.orquestarParaPruebasSinteticas(entrada,permitidas,Set.of());
        assertEquals(Estado.SELECCION_ACEPTADA,r.presentacion().estado()); assertSame(s,r.seleccion());
        var v=r.presentacion().resultados().getFirst().presentacion();
        var d=presentador.definicionesParaRevision().stream().filter(x -> ID.equals(x.accionId()) && enfoque.equals(x.enfoque())).findFirst().orElseThrow();
        assertEquals(d.titulo(),v.titulo()); assertEquals(d.descripcion(),v.descripcion());
        assertEquals(d.motivo().replace("{/transporte/medio}","Auto").replace("{/transporte/kmSemana}","100")
                .replace("{/transporte/diasSemana}","5"),v.motivo());
        assertEquals(enfoque,v.enfoque()); assertEquals(!enfoque.equals("SIN_ENFOQUE"),v.enfoqueSeleccionado());
        assertEquals(Revision.BORRADOR,v.estado()); assertEquals(PENDIENTE_REVISION,v.revisionHumana());
        assertFalse(v.publicable()); assertFalse(r.publicable());
        verify(selector).generarV2ConAlcanceParaPruebasSinteticas(entrada,permitidas,Set.of()); verifyNoMoreInteractions(selector);
    }
    @Test void abstencionDelSelectorSimuladoSeConservaSinFabricarTexto() {
        var permitidas=politica.evaluar(contexto,Set.of()).elegibles();
        var s=simulada(new Propuesta("Transporte",Decision.ABSTENERSE,null,List.of(),null,null,null,List.of(),MotivoAbstencion.NO_VERIFICABLE));
        when(selector.generarV2ConAlcanceParaPruebasSinteticas(entrada,permitidas,Set.of())).thenReturn(s);
        var r=orquestador.orquestarParaPruebasSinteticas(entrada,permitidas,Set.of());
        assertEquals(EstadoDetalle.ABSTENCION_V2,r.presentacion().resultados().getFirst().estado());
        assertNull(r.presentacion().resultados().getFirst().presentacion()); assertFalse(r.publicable());
    }
    @ParameterizedTest @ValueSource(strings={"accion","version"})
    void accionNoAutorizadaOVersionIncorrectaSeRechaza(String caso) {
        var permitidas=politica.evaluar(contexto,Set.of()).elegibles();
        var s=propuesta(caso.equals("accion")?"TR_EXPLORAR_ALTERNATIVAS_AUTO":ID,caso.equals("version")?"999":"1",null);
        when(selector.generarV2ConAlcanceParaPruebasSinteticas(entrada,permitidas,Set.of())).thenReturn(s);
        var r=orquestador.orquestarParaPruebasSinteticas(entrada,permitidas,Set.of());
        assertEquals(Estado.SELECCION_RECHAZADA,r.presentacion().estado());
        assertTrue(r.presentacion().resultados().isEmpty()); assertSame(s,r.seleccion()); assertFalse(r.publicable());
    }
    @ParameterizedTest @ValueSource(strings={"titulo","descripcion","motivo","campo","version","enfoque","identidad"})
    void fixtureFallaCerradoSiCambiaUnaDefinicion(String cambio) {
        var fuente=mock(PresentacionRecomendacionesIAService.class);
        var defs=new ArrayList<>(presentador.definicionesParaRevision());
        int i=0; while(!defs.get(i).accionId().equals(ID)) i++;
        var d=defs.get(i);
        defs.set(i,new DefinicionPresentacionIADTO(cambio.equals("identidad")?"OTRA":d.accionId(),d.versionAccion(),
                cambio.equals("enfoque")?"OTRO":d.enfoque(),cambio.equals("version")?"2":d.versionPresentacion(),
                cambio.equals("titulo")?"Cambiado":d.titulo(),cambio.equals("descripcion")?"Cambiado":d.descripcion(),
                cambio.equals("motivo")?"Cambiado":d.motivo(),cambio.equals("campo")?Set.of("/transporte/medio"):d.camposContextoPermitidos()));
        when(fuente.definicionesParaRevision()).thenReturn(defs);
        assertThrows(IllegalStateException.class, () -> new RegistroEditorialPruebaInternaFixture(fuente).consultar());
        verifyNoInteractions(selector);
    }
    @Test void huellaDePermisoAlteradaNoProduceElegibilidad() {
        var lista=new ArrayList<>(registro.consultar()); var p=lista.getFirst();
        lista.set(0,new Autorizacion(p.accionId(),p.versionAccion(),p.enfoque(),p.versionPresentacion(),"0".repeat(64),p.camposContextoPermitidos()));
        var q=new PoliticaAutorizacionEditorialIAServiceImpl(catalogo,presentador,() -> lista);
        assertTrue(q.evaluar(contexto,Set.of()).elegibles().isEmpty()); verifyNoInteractions(selector);
    }
    @Test void sinAutorizacionesNoSeInvocaSelector() {
        var permitidas=new PoliticaAutorizacionEditorialIAServiceImpl().evaluar(contexto,Set.of()).elegibles();
        var r=orquestador.orquestarParaPruebasSinteticas(entrada,permitidas,Set.of());
        assertEquals(Estado.SIN_PROPUESTA,r.presentacion().estado()); assertTrue(r.categoriasRespondidas().isEmpty());
        verifyNoInteractions(selector);
    }
    @Test void transaccionActivaImpideInferenciaAunqueFamiliaSeaElegible() {
        var permitidas=politica.evaluar(contexto,Set.of()).elegibles();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class, () -> orquestador.orquestarParaPruebasSinteticas(entrada,permitidas,Set.of()));
            verifyNoInteractions(selector);
        } finally { TransactionSynchronizationManager.clear(); }
    }
}
