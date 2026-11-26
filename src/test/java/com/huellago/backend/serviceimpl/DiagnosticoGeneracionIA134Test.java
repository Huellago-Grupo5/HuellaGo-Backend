package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.HabitoRepository;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.huellago.backend.dtos.ResultadoGeneracionManualIADTO.Causa.*;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;

/** Exclusivamente datos, permisos y respuestas sinteticos. Sin servidor, JDBC ni Ollama. */
class DiagnosticoGeneracionIA134Test {
    final GeneracionManualIAServiceImplTest f=new GeneracionManualIAServiceImplTest();
    final HabitoRepository habitos=mock(HabitoRepository.class);
    final List<Habito> datos=new ArrayList<>();
    final ContextoRecomendacionesIAServiceImpl constructor=new ContextoRecomendacionesIAServiceImpl(habitos);
    @BeforeEach void preparar() { f.preparar(); when(habitos.findByUsuario_Id(77L)).thenReturn(datos); }

    void agregar(String categoria,String campo,String valor,String unidad) {
        var u=new Usuario(); u.setId(77L); var c=new CategoriaHabito(); c.setNombre(categoria);
        var h=new Habito(); h.setId((long)datos.size()+1); h.setUsuario(u); h.setCategoria(c); h.setNombre(campo);
        h.setValor(new BigDecimal(valor)); h.setUnidad(unidad); h.setFechaRegistro(LocalDateTime.of(2026,11,24,10,0)); datos.add(h);
    }
    ContextoRecomendacionesIAService.Resultado transporte() {
        agregar("Transporte","medio","0","Auto"); agregar("Transporte","kmSemana","100","km/semana");
        agregar("Transporte","diasSemana","5","dias/semana"); return constructor.construir(77L);
    }
    CapturaContextoAmbientalIADTO capturar(ContextoRecomendacionesIAService.Resultado c,Set<AccionAmbientalAdmisible.Referencia> realizadas) {
        var captura=new CapturaContextoAmbientalIADTO("SINTETICA",77L,c,realizadas,Set.of(),Set.of(),"huella-sintetica");
        when(f.capturas.capturar(f.auth)).thenReturn(captura);
        when(f.escritor.referenciaEditorial(f.auth,captura,PersistenciaRecomendacionesIAService.Destino.PRUEBA_INTERNA)).thenReturn("receta-sintetica");
        return captura;
    }
    @Test void insuficienciaTotalConservaTodosLosCamposSinPoliticaValidadorInferenciaNiEscritor() {
        var c=constructor.construir(77L); capturar(c,Set.of());
        var politica=mock(PoliticaAutorizacionEditorialIAService.class); var orquestador=mock(OrquestadorRecomendacionesIAService.class);
        var catalogo=mock(CatalogoAccionesAmbientalesService.class);
        var s=new GeneracionManualIAServiceImpl(f.capturas,politica,orquestador,f.escritor,catalogo);
        var r=s.generarParaPruebaInterna(f.auth);
        assertEquals(ResultadoGeneracionManualIADTO.Estado.SIN_PROPUESTA,r.estado());
        assertEquals(Set.of(INFORMACION_AMBIENTAL_INSUFICIENTE),r.diagnostico().causas());
        assertSame(c,r.diagnostico().contexto()); assertEquals(4,c.categoriasIncompletas().size());
        assertEquals(9,c.observaciones().size()); assertTrue(c.categoriasValidas().isEmpty());
        assertTrue(r.propuestasAceptadasOrdenadas().isEmpty()); assertNull(r.persistencia());
        verifyNoInteractions(politica,orquestador,f.escritor,catalogo,f.selector);
    }
    @Test void parcialConservaCategoriaValidaYCamposSinInferirAusenciaEditorial() {
        var c=transporte(); capturar(c,Set.of());
        var s=f.componer(new PoliticaAutorizacionEditorialIAServiceImpl(),f.selector);
        var r=s.generarParaPruebaInterna(f.auth);
        assertEquals(List.of("Transporte"),r.diagnostico().contexto().categoriasValidas());
        assertEquals(List.of("Energía","Alimentación","Residuos"),c.categoriasIncompletas());
        assertTrue(c.observaciones().contains("Energía.personas: dato obligatorio ausente"));
        assertTrue(r.diagnostico().causas().containsAll(Set.of(CONTEXTO_PARCIAL,SIN_AUTORIZACION_EDITORIAL,CATEGORIAS_FUERA_DE_ALCANCE)));
        assertFalse(r.diagnostico().causas().contains(INFORMACION_AMBIENTAL_INSUFICIENTE));
        verifyNoInteractions(f.selector,f.escritor);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void obligatorioAusenteOInvalidoConservaOtraCategoriaValida(boolean invalido) {
        transporte(); var km=datos.get(1); if(invalido) km.setValor(new BigDecimal("-1")); else datos.remove(km);
        agregar("Alimentación","tipo","0","Vegetariana"); var c=constructor.construir(77L); capturar(c,Set.of());
        var r=f.componer(new PoliticaAutorizacionEditorialIAServiceImpl(),f.selector).generarParaPruebaInterna(f.auth);
        assertEquals(List.of("Alimentación"),r.diagnostico().contexto().categoriasValidas());
        assertTrue(c.observaciones().contains("Transporte.kmSemana: "+(invalido?"valor o unidad inválidos":"dato obligatorio ausente")));
        assertTrue(r.diagnostico().causas().contains(CONTEXTO_PARCIAL)); verifyNoInteractions(f.selector,f.escritor);
    }
    @Test void consumoOpcionalAusenteNoSolicitaCompletarEnergia() {
        transporte(); agregar("Energía","vivienda","0","Casa"); agregar("Energía","personas","3","personas");
        agregar("Energía","fuente","0","Electricidad"); var c=constructor.construir(77L); capturar(c,Set.of());
        var r=f.componer(new PoliticaAutorizacionEditorialIAServiceImpl(),f.selector).generarParaPruebaInterna(f.auth);
        assertEquals(List.of("Transporte","Energía"),r.diagnostico().contexto().categoriasValidas());
        assertNull(c.contexto().energia().consumoKwhDiario());
        assertTrue(c.observaciones().stream().noneMatch(s->s.contains("consumoKwhDiario")));
        assertTrue(r.diagnostico().categoriasExcluidas().contains("Energía"));
    }
    @Test void sinCompatiblesNoSeAtribuyeARealizadasNiEditorial() {
        var catalogo=mock(CatalogoAccionesAmbientalesService.class); when(catalogo.compatibles(any(),anySet())).thenReturn(List.of());
        var politica=mock(PoliticaAutorizacionEditorialIAService.class);
        when(politica.evaluar(any(),anySet())).thenReturn(new PoliticaAutorizacionEditorialIAService.Resultado(List.of(),List.of(),Map.of()));
        var r=new GeneracionManualIAServiceImpl(f.capturas,politica,mock(OrquestadorRecomendacionesIAService.class),f.escritor,catalogo)
                .generarParaPruebaInterna(f.auth);
        assertTrue(r.diagnostico().causas().contains(SIN_ACCIONES_COMPATIBLES));
        assertFalse(r.diagnostico().causas().contains(ACCIONES_YA_REALIZADAS)); assertFalse(r.diagnostico().causas().contains(SIN_AUTORIZACION_EDITORIAL));
        verify(catalogo,times(2)).compatibles(f.contexto.contexto(),Set.of()); verifyNoInteractions(f.escritor);
    }
    @Test void todasRealizadasNoSonVetoEditorialNiInsuficiencia() {
        var realizadas=new HashSet<AccionAmbientalAdmisible.Referencia>();
        f.catalogo.compatibles(f.contexto.contexto(),Set.of()).forEach(c->realizadas.add(new AccionAmbientalAdmisible.Referencia(c.definicion().id(),c.definicion().version())));
        capturar(f.contexto,realizadas); var r=f.servicio.generarParaPruebaInterna(f.auth);
        assertTrue(r.diagnostico().causas().contains(ACCIONES_YA_REALIZADAS));
        assertFalse(r.diagnostico().causas().contains(SIN_AUTORIZACION_EDITORIAL));
        assertFalse(r.diagnostico().causas().contains(SIN_ACCIONES_COMPATIBLES)); verifyNoInteractions(f.selector,f.escritor);
    }
    @Test void causasSimultaneasNoSePierdenYCategoriaCompletaExcluidaNoEsAbstencion() {
        var c=transporte(); agregar("Energía","vivienda","0","Casa"); agregar("Energía","personas","3","personas");
        agregar("Energía","fuente","0","Electricidad"); c=constructor.construir(77L);
        capturar(c,Set.of(new AccionAmbientalAdmisible.Referencia("TR_EXPLORAR_ALTERNATIVAS_AUTO","1")));
        var r=f.componer(new PoliticaAutorizacionEditorialIAServiceImpl(),f.selector).generarParaPruebaInterna(f.auth);
        assertTrue(r.diagnostico().causas().containsAll(Set.of(CONTEXTO_PARCIAL,ACCIONES_YA_REALIZADAS,SIN_AUTORIZACION_EDITORIAL,CATEGORIAS_FUERA_DE_ALCANCE)));
        assertEquals(List.of("Transporte","Energía"),r.diagnostico().categoriasExcluidas());
        assertFalse(r.diagnostico().causas().contains(ABSTENCION_REAL_MODELO)); verifyNoInteractions(f.selector,f.escritor);
        assertThrows(UnsupportedOperationException.class,()->r.diagnostico().causas().clear());
    }
    @Test void parcialPuedeSeleccionarUnaPropuestaValidaConDiagnostico() {
        var c=transporte(); capturar(c,Set.of());
        when(f.selector.generarV2ConAlcanceInterno(eq(c),anyList(),anySet())).thenReturn(f.propuesta(f.ID,null,false));
        when(f.escritor.persistir(any(),any(),any(),any(),any())).thenReturn(f.guardada(false));
        var r=f.servicio.generarParaPruebaInterna(f.auth);
        assertEquals(ResultadoGeneracionManualIADTO.Estado.PERSISTIDA_PRUEBA_INTERNA,r.estado());
        assertEquals(1,r.propuestasAceptadasOrdenadas().size()); assertTrue(r.diagnostico().causas().contains(CONTEXTO_PARCIAL));
        assertEquals(c.observaciones(),r.diagnostico().contexto().observaciones());
    }
    @Test void abstencionRealYSeleccionRechazadaTienenCausasDistintas() {
        f.responder(f.propuesta(f.ID,null,true)); var a=f.servicio.generarParaPruebaInterna(f.auth);
        assertTrue(a.diagnostico().causas().contains(ABSTENCION_REAL_MODELO)); assertNull(a.persistencia());
        f.responder(f.propuesta("TR_EXPLORAR_ALTERNATIVAS_AUTO",null,false)); var b=f.servicio.generarParaPruebaInterna(f.auth);
        assertTrue(b.diagnostico().causas().contains(SELECCION_RECHAZADA)); assertFalse(b.diagnostico().causas().contains(ABSTENCION_REAL_MODELO));
        verify(f.escritor,never()).persistir(any(),any(),any(),any(),any());
    }
    @Test void constructorAnteriorSigueDisponibleSinInventarDiagnostico() {
        var r=new ResultadoGeneracionManualIADTO(ResultadoGeneracionManualIADTO.Estado.SIN_PROPUESTA,null,null,null);
        assertNull(r.diagnostico().contexto()); assertTrue(r.diagnostico().causas().isEmpty()); assertFalse(r.publicable());
    }
}
