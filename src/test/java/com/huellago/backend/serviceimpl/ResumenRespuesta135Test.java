package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.HuellaCarbono;
import com.huellago.backend.repositories.HuellaCarbonoRepository;
import com.huellago.backend.services.*;
import com.huellago.backend.security.UserSecurity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.mock.web.MockHttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ResumenRespuesta135Test {
    @ParameterizedTest @ValueSource(strings={"vigente","inexistente","obsoleta","invalida","sinFechas"})
    void mantieneDiagnosticoEditorialYParcialConAdvertenciaAmbientalSinInferir(String caso) {
        var f=new FixtureDemoIA127(); var huellas=mock(HuellaCarbonoRepository.class);
        var fecha=LocalDateTime.of(2026,11,8,12,0); f.datos.forEach(h->h.setFechaRegistro(fecha.minusHours(1)));
        var h=new HuellaCarbono(50L,new BigDecimal("40"),new BigDecimal("12"),new BigDecimal("20"),new BigDecimal("3"),new BigDecimal("5"),fecha,f.usuario);
        switch(caso) { case "inexistente" -> h=null; case "obsoleta" -> f.datos.getFirst().setFechaActualizacion(fecha.plusHours(1));
            case "invalida" -> h.setCo2Total(null); case "sinFechas" -> f.datos.getFirst().setFechaRegistro(null); }
        when(huellas.findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(91L)).thenReturn(h);
        var lectura=new CapturaContextoAmbientalIAServiceImpl(f.usuarios,f.asociaciones,f.retos,new ContextoRecomendacionesIAServiceImpl(f.habitos,huellas));
        var proxy=new ProxyFactory(lectura); proxy.addAdvice(new TransactionInterceptor(f.tm,new AnnotationTransactionAttributeSource()));
        var captura=ProteccionDemoIA127.captura((CapturaContextoAmbientalIAService)proxy.getProxy());
        var escritor=mock(PersistenciaRecomendacionesIAService.class); var orquestador=mock(OrquestadorRecomendacionesIAService.class);
        var manual=new GeneracionManualIAServiceImpl(captura,new PoliticaAutorizacionEditorialIAServiceImpl(),orquestador,escritor);
        var entrada=new EntradaDemoIA127(manual,f.asociaciones,f.tm); var req=new MockHttpServletRequest();
        req.setLocalAddr("127.0.0.1"); req.setRemoteAddr("127.0.0.1"); req.setLocalPort(8081);
        var auth=new UsernamePasswordAuthenticationToken(new UserSecurity(f.usuario),null,java.util.List.of());
        var respuesta=entrada.generar(auth,req); var dto=respuesta.getBody();
        assertEquals(200,respuesta.getStatusCode().value()); assertFalse(dto.getPublicable()); assertTrue(dto.getRecomendaciones().isEmpty());
        if(caso.equals("vigente")) {
            assertEquals(50L,dto.getHuellaId()); assertEquals(fecha,dto.getFechaCalculoHuella());
            assertEquals("kg CO2e",dto.getUnidad()); assertEquals("semanal",dto.getPeriodo());
            assertTrue(dto.getAdvertencias().stream().anyMatch(a->a.contains("Componentes numéricos")
                    && a.contains("Energía 20") && a.contains("atribución personal no acreditada")
                    && a.contains("no compara impacto personal")));
        } else {
            assertNull(dto.getHuellaId()); assertNull(dto.getFechaCalculoHuella());
            assertTrue(dto.getAdvertencias().stream().noneMatch(a->a.contains("Componentes numéricos")));
        }
        assertTrue(dto.getMensaje().contains("autorización editorial")); assertTrue(dto.getDatosFaltantes().contains("Energía.personas: dato obligatorio ausente"));
        assertTrue(dto.getAdvertencias().stream().anyMatch(a->switch(caso) {
            case "vigente" -> a.contains("no acredita un foco personal dominante"); case "inexistente" -> a.contains("No hay una huella registrada");
            case "obsoleta" -> a.contains("anterior a cambios de hábitos"); default -> a.contains("No se pudo verificar una huella utilizable");
        }));
        verifyNoInteractions(escritor,orquestador); assertTrue(f.guardadas.isEmpty());
    }
}
