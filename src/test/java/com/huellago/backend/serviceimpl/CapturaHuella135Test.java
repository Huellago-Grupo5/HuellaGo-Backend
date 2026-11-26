package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.huellago.backend.services.CapturaContextoAmbientalIAService.Estado.*;

/** Proxies Spring reales, gestor sin recursos. No acredita exclusion fisica PostgreSQL. */
class CapturaHuella135Test {
    final HuellaContexto135Test h=new HuellaContexto135Test();
    final TransaccionesAisladasIA tm=new TransaccionesAisladasIA();
    CapturaContextoAmbientalIAService captura;
    @BeforeEach void preparar() {
        h.preparar(); var constructor=proxy(h.constructor,ContextoRecomendacionesIAService.class);
        captura=proxy(new CapturaContextoAmbientalIAServiceImpl(h.f.usuarios,h.f.recomendaciones,h.f.retos,constructor),CapturaContextoAmbientalIAService.class);
    }
    <T>T proxy(Object o,Class<T> tipo) {
        var p=new ProxyFactory(o); p.addAdvice(new TransactionInterceptor(tm,new AnnotationTransactionAttributeSource())); return tipo.cast(p.getProxy());
    }
    @Test void versionExplicitaYHuellaEstableAnteOrdenYEscalasSinEscrituras() {
        var a=captura.capturar(h.f.autenticacion); assertEquals("HUELLAGO_CONTEXTO_VIGENCIA_V2",a.versionHuella());
        Collections.reverse(h.f.datos); h.huella.setCo2Transporte(new BigDecimal("12.000"));
        var b=captura.capturar(h.f.autenticacion); assertEquals(a.huellaVigencia(),b.huellaVigencia());
        assertEquals(VIGENTE,captura.revalidar(h.f.autenticacion,a).estado());
        verify(h.huellas,never()).save(any()); verify(h.f.habitos,never()).save(any());
        verify(h.f.recomendaciones,never()).save(any());
        assertThrows(UnsupportedOperationException.class,()->a.contexto().resumenHuella().componentes().clear());
    }
    @ParameterizedTest @ValueSource(strings={"componente","fecha","id","desaparece","aparece","invalidacion","total"})
    void cambiosRelevantesDuranteEsperaSeDetectan(String caso) {
        var guardada=h.huella; if(caso.equals("aparece")) h.huella=null;
        var a=captura.capturar(h.f.autenticacion); var referencia=a.huellaVigencia();
        switch(caso) { case "componente" -> h.huella.setCo2Transporte(new BigDecimal("13"));
            case "fecha" -> h.huella.setFechaCalculo(h.huella.getFechaCalculo().plusSeconds(1));
            case "id" -> h.huella.setId(51L); case "desaparece" -> h.huella=null;
            case "aparece" -> h.huella=guardada; case "invalidacion" -> h.huella.setCo2Energia(null);
            default -> h.huella.setCo2Total(new BigDecimal("40.01")); }
        assertEquals(CAMBIO_DETECTADO,captura.revalidar(h.f.autenticacion,a).estado()); assertEquals(referencia,a.huellaVigencia());
    }
    @Test void mandatoryLeeHuellaDespuesDelBloqueoEnLaMismaTransaccion() {
        var a=captura.capturar(h.f.autenticacion); clearInvocations(h.f.usuarios,h.f.habitos,h.huellas);
        when(h.f.usuarios.bloquearParaRecomendaciones(68L)).thenReturn(Optional.of(68L));
        int previas=tm.comienzos; var template=new TransactionTemplate(tm);
        template.executeWithoutResult(s->{
            h.f.usuarios.bloquearParaRecomendaciones(68L);
            when(h.huellas.findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(68L)).thenAnswer(i->{
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly()); return h.huella;
            });
            assertEquals(VIGENTE,captura.comprobarVigenciaEnTransaccion(h.f.autenticacion,a).estado());
            h.huella.setCo2Energia(new BigDecimal("21"));
            assertEquals(CAMBIO_DETECTADO,captura.comprobarVigenciaEnTransaccion(h.f.autenticacion,a).estado());
            assertEquals(previas+1,tm.comienzos);
        });
        var orden=inOrder(h.f.usuarios,h.f.habitos,h.huellas);
        orden.verify(h.f.usuarios).bloquearParaRecomendaciones(68L); orden.verify(h.f.usuarios).existsById(68L);
        orden.verify(h.f.habitos).findByUsuario_Id(68L); orden.verify(h.huellas).findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(68L);
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                ()->captura.comprobarVigenciaEnTransaccion(h.f.autenticacion,a));
    }
    @Test void capturaV1NoSeReescribeNiSePromueveAV2() {
        var a=captura.capturar(h.f.autenticacion);
        var vieja=new CapturaContextoAmbientalIADTO("HUELLAGO_CONTEXTO_VIGENCIA_V1",a.usuarioId(),a.contexto(),a.realizadas(),
                a.recomendacionesLegadasRealizadas(),a.retosLegadosRealizados(),a.huellaVigencia());
        assertEquals(400,assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->captura.revalidar(h.f.autenticacion,vieja)).getStatusCode().value());
        assertEquals("HUELLAGO_CONTEXTO_VIGENCIA_V1",vieja.versionHuella());
    }
}
