package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;

/** Permisos multcategoria solo simulados; validador, catalogo y presentador reales. No son aprobaciones. */
class OrdenGeneracionIA134Test {
    final GeneracionManualIAServiceImplTest f=new GeneracionManualIAServiceImplTest();
    static final List<String> IDS=List.of("TR_PLANIFICAR_DESPLAZAMIENTOS","EN_REVISAR_USOS","AL_PLANIFICAR_APROVECHAMIENTO","RE_REVISAR_ENTREGA");
    final ContextoRecomendacionesIAService.Resultado contexto=new ContextoRecomendacionesIAService.Resultado(
            new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte("Auto",new BigDecimal("100"),5),
                    new ContextoRecomendacionesIADTO.Energia("Casa",3,"Electricidad",null),
                    new ContextoRecomendacionesIADTO.Alimentacion("Vegetariana"),
                    new ContextoRecomendacionesIADTO.Residuos("Bajo",true)),List.of(),List.of(),ReglasInterpretacionAmbiental.REGLAS);
    @BeforeEach void preparar() { f.preparar(); }
    Valor dato(String ruta) {
        return switch(ruta) {
            case "/transporte/medio" -> new Texto("Auto"); case "/transporte/kmSemana" -> new Numero(new BigDecimal("100"));
            case "/transporte/diasSemana" -> new Numero(new BigDecimal("5"));
            case "/energia/vivienda" -> new Texto("Casa"); case "/energia/personas" -> new Numero(new BigDecimal("3"));
            case "/energia/fuente" -> new Texto("Electricidad"); case "/alimentacion/tipo" -> new Texto("Vegetariana");
            case "/residuos/plasticos" -> new Texto("Bajo"); case "/residuos/reciclas" -> new Booleano(true);
            default -> throw new AssertionError(ruta);
        };
    }
    ResultadoGeneracionFundamentadaIADTO seleccion(List<CatalogoAccionesAmbientalesService.Candidata> candidatas) {
        var propuestas=candidatas.stream().map(c->new Propuesta(c.definicion().categoria(),Decision.PROPONER,
                new Accion(c.definicion().id(),c.definicion().version(),Map.of()),
                c.definicion().evidenciasRequeridas().stream().sorted().map(r->new Evidencia(r,dato(r),Ambito.NO_APLICA)).toList(),
                null,null,null,List.of(),null)).toList();
        var dto=new RecomendacionFundamentadaIADTO("2",propuestas);
        return new ResultadoGeneracionFundamentadaIADTO(f.mapper.writeValueAsString(dto),dto,
                new ResultadoEvaluacionRecomendacionIADTO(ResultadoEvaluacionRecomendacionIADTO.Estado.ACEPTADA_ESTRUCTURALMENTE,List.of(),List.of()));
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3})
    void variasPropuestasValidadasOrdenadasSinModificarSeleccionNiSnapshots(int rotacion) {
        var compatibles=f.catalogo.compatibles(contexto.contexto(),Set.of());
        var elegibles=new ArrayList<>(compatibles.stream().filter(c->IDS.contains(c.definicion().id())).toList());
        Collections.reverse(elegibles); Collections.rotate(elegibles,rotacion);
        var politica=mock(PoliticaAutorizacionEditorialIAService.class);
        when(politica.evaluar(contexto.contexto(),Set.of())).thenReturn(new PoliticaAutorizacionEditorialIAService.Resultado(compatibles,elegibles,Map.of()));
        var captura=new CapturaContextoAmbientalIADTO("SINTETICA",77L,contexto,Set.of(),Set.of(),Set.of(),"igual");
        when(f.capturas.capturar(f.auth)).thenReturn(captura);
        when(f.escritor.referenciaEditorial(f.auth,captura,PersistenciaRecomendacionesIAService.Destino.PRUEBA_INTERNA)).thenReturn("receta-sintetica");
        var original=seleccion(elegibles); String json=original.propuestaJson();
        when(f.selector.generarV2ConAlcanceInterno(eq(contexto),eq(elegibles),eq(Set.of()))).thenReturn(original);
        var guardadas=new ArrayList<ResultadoPersistenciaRecomendacionesIADTO.Guardada>();
        when(f.escritor.persistir(eq(f.auth),eq(captura),any(),eq("receta-sintetica"),any())).thenAnswer(i->{
            OrquestadorRecomendacionesIAService.Resultado s=i.getArgument(2);
            assertSame(original,s.seleccion()); assertEquals(json,s.seleccion().propuestaJson());
            guardadas.clear();
            for(var d:s.presentacion().resultados()) {
                var p=d.presentacion(); int id=IDS.indexOf(p.accionId())+10;
                guardadas.add(new ResultadoPersistenciaRecomendacionesIADTO.Guardada(id,id+10,rotacion%2==0,p.accionId(),p.versionAccion(),null,
                        p.versionPresentacion(),p.titulo(),p.descripcion(),p.motivo(),"Pendiente",LocalDateTime.of(2026,11,24,10,0)));
            }
            return new ResultadoPersistenciaRecomendacionesIADTO(guardadas);
        });
        var servicio=f.componer(politica,f.selector); var resultado=servicio.generarParaPruebaInterna(f.auth);
        assertEquals(ResultadoGeneracionManualIADTO.Estado.PERSISTIDA_PRUEBA_INTERNA,resultado.estado());
        assertEquals(IDS,resultado.propuestasAceptadasOrdenadas().stream().map(d->d.presentacion().accionId()).toList());
        assertEquals(IDS,resultado.persistencia().resultados().stream().map(ResultadoPersistenciaRecomendacionesIADTO.Guardada::accionId).toList());
        assertSame(original,resultado.seleccion().seleccion()); assertEquals(json,resultado.seleccion().seleccion().propuestaJson());
        for(var g:resultado.persistencia().resultados()) {
            assertSame(guardadas.stream().filter(a->a.asociacionId().equals(g.asociacionId())).findFirst().orElseThrow(),g);
            assertFalse(g.publicable()); assertEquals(rotacion%2==0,g.reutilizada());
        }
        assertThrows(UnsupportedOperationException.class,()->resultado.propuestasAceptadasOrdenadas().clear());
        verify(f.escritor).persistir(f.auth,captura,resultado.seleccion(),"receta-sintetica",PersistenciaRecomendacionesIAService.Destino.PRUEBA_INTERNA);
    }
    @Test void desempateDeRepresentacionUsaAccionVersionEnfoqueYAsociacionSinAfirmarValidezDeDecisionesDuplicadas() {
        // Prueba del comparador, no una respuesta V2 admisible (V2 exige una decision por categoria).
        var candidatas=f.catalogo.compatibles(f.contexto.contexto(),Set.of());
        var otras=new ArrayList<>(candidatas); var primera=candidatas.getFirst(); var d=primera.definicion();
        var v2=new AccionAmbientalAdmisible(d.id(),"2",d.categoria(),d.condiciones(),d.evidenciasRequeridas(),d.parametros(),d.informacionAdicionalPermitida());
        otras.add(new CatalogoAccionesAmbientalesService.Candidata(v2,primera.objetivo(),primera.descripcion(),primera.restricciones(),primera.estadoRevision()));
        var editorial=new PoliticaAutorizacionEditorialIAService.Resultado(otras,otras,Map.of());
        var entrada=new ArrayList<>(List.of(g("TR_PLANIFICAR_DESPLAZAMIENTOS","2",null,4),g("TR_PLANIFICAR_DESPLAZAMIENTOS","1","NECESIDAD",3),
                g("TR_PLANIFICAR_DESPLAZAMIENTOS","1",null,2),g("TR_EXPLORAR_ALTERNATIVAS_AUTO","1",null,1),g("TR_PLANIFICAR_DESPLAZAMIENTOS","1",null,5)));
        for(int i=0;i<5;i++) {
            Collections.rotate(entrada,1); var r=new ResultadoGeneracionManualIADTO(ResultadoGeneracionManualIADTO.Estado.PERSISTIDA_PRUEBA_INTERNA,
                    editorial,null,new ResultadoPersistenciaRecomendacionesIADTO(entrada));
            assertEquals(List.of(1,2,5,3,4),r.persistencia().resultados().stream().map(ResultadoPersistenciaRecomendacionesIADTO.Guardada::asociacionId).toList());
        }
    }
    ResultadoPersistenciaRecomendacionesIADTO.Guardada g(String id,String version,String enfoque,int asociacion) {
        return new ResultadoPersistenciaRecomendacionesIADTO.Guardada(asociacion,20,true,id,version,enfoque,"1","Texto conservado","Descripcion conservada","Motivo conservado","Pendiente",LocalDateTime.of(2026,11,24,10,0));
    }
}
