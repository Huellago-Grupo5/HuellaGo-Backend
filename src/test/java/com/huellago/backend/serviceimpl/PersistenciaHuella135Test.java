package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.HuellaCarbono;
import com.huellago.backend.repositories.HuellaCarbonoRepository;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** Escritor sin modificaciones, repositorios simulados y proxies reales sin recursos JDBC. */
class PersistenciaHuella135Test {
    final PersistenciaRecomendacionesIAServiceImplTest f=new PersistenciaRecomendacionesIAServiceImplTest();
    final HuellaCarbonoRepository huellas=mock(HuellaCarbonoRepository.class);
    HuellaCarbono huella;
    @BeforeEach void preparar() {
        f.preparar(); huella=new HuellaCarbono(50L,new BigDecimal("40"),new BigDecimal("12"),new BigDecimal("20"),
                new BigDecimal("3"),new BigDecimal("5"),LocalDateTime.of(2026,11,8,12,0),f.usuario);
        when(huellas.findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(77L)).thenAnswer(i->huella);
        var constructor=f.proxy(new ContextoRecomendacionesIAServiceImpl(f.habitos,huellas),ContextoRecomendacionesIAService.class);
        f.captura=f.proxy(new CapturaContextoAmbientalIAServiceImpl(f.usuarios,f.asociaciones,f.retos,constructor),CapturaContextoAmbientalIAService.class);
        f.servicio=f.proxy(new PersistenciaRecomendacionesIAServiceImpl(f.usuarios,f.acciones,f.asociaciones,f.categorias,f.captura,f.em,f.editorial,true),PersistenciaRecomendacionesIAService.class);
        f.inicial=f.captura.capturar(f.auth); f.seleccion=f.seleccionar(null,false); f.receta=f.servicio.referenciaEditorial(f.auth,f.inicial);
    }
    @Test void cambioDuranteInferenciaSeReleeBajoBloqueoYNoSeGuarda() {
        var selector=mock(GeneradorRecomendacionesIAService.class); boolean[] inferida={false},bloqueado={false};
        doAnswer(i->{ assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); bloqueado[0]=true; return Optional.of(77L); })
                .when(f.usuarios).bloquearParaRecomendaciones(77L);
        when(huellas.findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(77L)).thenAnswer(i->{
            if(inferida[0]) { assertTrue(bloqueado[0]); assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly()); }
            return huella;
        });
        when(selector.generarV2ConAlcanceInterno(any(),anyList(),anySet())).thenAnswer(i->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            inferida[0]=true; huella.setCo2Energia(new BigDecimal("21")); return f.seleccion.seleccion();
        });
        var manual=new GeneracionManualIAServiceImpl(f.captura,f.editorial,
                new OrquestadorRecomendacionesIAServiceImpl(selector,f.catalogo,f.presentador),f.servicio);
        var e=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->manual.generarParaPruebaInterna(f.auth));
        assertEquals("CONTEXTO_MODIFICADO",e.getReason()); assertTrue(bloqueado[0]); f.sinEscrituras();
        verify(selector,times(1)).generarV2ConAlcanceInterno(any(),anyList(),anySet());
    }
    @Test void reintentoMismaCapturaReutilizaSinSobrescribirNiRellenarMetadatosAmbientales() {
        var a=f.guardar(); var fila=f.guardadas.getFirst(); var titulo=fila.getTituloInstantanea();
        var fecha=fila.getFechaGeneracion(); var referencia=fila.getReferenciaContexto();
        f.seleccion=f.seleccionar("NECESIDAD",false); var b=f.guardar();
        assertEquals(a.resultados().getFirst().asociacionId(),b.resultados().getFirst().asociacionId());
        assertTrue(b.resultados().getFirst().reutilizada()); assertEquals(1,f.guardadas.size());
        assertEquals(titulo,fila.getTituloInstantanea()); assertEquals(fecha,fila.getFechaGeneracion()); assertEquals(referencia,fila.getReferenciaContexto());
        assertNull(fila.getHuellaOrigenId()); assertEquals("MEDIA",fila.getPrioridadInstantanea());
        assertEquals(PrioridadAccionAmbientalIA.BASE,fila.getBasePriorizacion());
        verify(f.asociaciones,times(1)).saveAndFlush(any());
    }
    @Test void nuevaCapturaDeHuellaConPendienteAnteriorMantieneConflicto() {
        f.guardar(); var previa=f.inicial; var fila=f.guardadas.getFirst(); var texto=fila.getTituloInstantanea();
        huella.setCo2Transporte(new BigDecimal("13")); f.inicial=f.captura.capturar(f.auth);
        assertNotEquals(previa.huellaVigencia(),f.inicial.huellaVigencia());
        f.seleccion=f.seleccionar(null,false); f.receta=f.servicio.referenciaEditorial(f.auth,f.inicial);
        when(f.asociaciones.findByUsuario_IdAndEstadoIgnoreCaseAndRecomendacionIA_Categoria_Id(77L,"pendiente",2L)).thenReturn(List.of(fila));
        var e=assertThrows(org.springframework.web.server.ResponseStatusException.class,f::guardar);
        assertEquals("CATEGORIA_PENDIENTE_OTRO_CONTEXTO",e.getReason());
        assertEquals(1,f.guardadas.size()); assertEquals(texto,fila.getTituloInstantanea()); verify(f.asociaciones,times(1)).saveAndFlush(any());
    }
}
