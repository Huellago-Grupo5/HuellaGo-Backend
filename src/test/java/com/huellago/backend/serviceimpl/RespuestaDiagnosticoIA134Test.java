package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.exceptions.OllamaClientException;
import com.huellago.backend.repositories.UsuarioRecomendacionIARepository;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.huellago.backend.dtos.ResultadoGeneracionManualIADTO.Causa.*;

/** Adaptador real, solicitud en memoria y gestor sin recursos. No realiza HTTP ni conexiones. */
class RespuestaDiagnosticoIA134Test {
    final FixtureDemoIA127 f=new FixtureDemoIA127();
    final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(new UserSecurity(f.usuario),null,List.of());
    MockHttpServletRequest solicitud() {
        var r=new MockHttpServletRequest(); r.setLocalAddr("127.0.0.1"); r.setLocalPort(8081); r.setRemoteAddr("127.0.0.1"); return r;
    }
    @AfterEach void limpiar() { TransactionSynchronizationManager.clear(); }
    @Test void parcialSinSeleccionConservaCamposHastaRespuestaYExplicaVetoEditorial() {
        f.permisos.clear(); var r=f.entrada.generar(auth,solicitud()); var d=r.getBody();
        assertEquals(200,r.getStatusCode().value()); assertNotNull(d);
        assertEquals("SIN_PROPUESTA",d.getEstado()); assertTrue(d.getRecomendaciones().isEmpty());
        assertTrue(d.getMensaje().contains("autorización editorial")); assertFalse(d.getMensaje().contains("Completa la información"));
        assertTrue(d.getDatosFaltantes().contains("Energía")); assertTrue(d.getDatosFaltantes().contains("Energía.personas: dato obligatorio ausente"));
        assertTrue(d.getAdvertencias().stream().anyMatch(s->s.contains("categorías válidas")));
        assertFalse(d.getPublicable()); assertEquals("BORRADOR",d.getEstadoEditorial()); verifyNoInteractions(f.selector);
        verify(f.asociaciones,never()).saveAndFlush(any());
    }
    @Test void insuficienciaSeAdaptaSinSeleccionPeroLaDemoRealMantieneSuControlAuto() {
        // El coordinador reutilizable admite diagnostico total. No ampliar el permiso Auto de la demo.
        var constructor=new ContextoRecomendacionesIAServiceImpl(f.habitos); f.datos.clear(); var c=constructor.construir(91L);
        var generacion=mock(GeneracionManualIAService.class);
        when(generacion.generarParaPruebaInterna(auth)).thenReturn(new ResultadoGeneracionManualIADTO(
                ResultadoGeneracionManualIADTO.Estado.SIN_PROPUESTA,null,null,null,
                new ResultadoGeneracionManualIADTO.Diagnostico(c,Set.of(INFORMACION_AMBIENTAL_INSUFICIENTE),List.of())));
        var asociaciones=mock(UsuarioRecomendacionIARepository.class); var entrada=new EntradaDemoIA127(generacion,asociaciones,f.tm);
        var d=entrada.generar(auth,solicitud()).getBody();
        assertTrue(d.getMensaje().startsWith("Completa la información ambiental")); assertTrue(d.getDatosFaltantes().containsAll(c.observaciones()));
        assertTrue(d.getRecomendaciones().isEmpty()); verifyNoInteractions(asociaciones);
        assertEquals(403,f.entrada.generar(auth,solicitud()).getStatusCode().value());
        verifyNoInteractions(f.selector); assertTrue(f.guardadas.isEmpty());
    }
    @Test void unaSugerenciaYReutilizacionConservanExactamenteLaInstantaneaPersistida() {
        f.enfoque="AGRUPACION"; var a=f.entrada.generar(auth,solicitud()).getBody();
        f.enfoque="NECESIDAD"; var b=f.entrada.generar(auth,solicitud()).getBody();
        assertEquals("PROPUESTA_GUARDADA_PRUEBA_INTERNA",a.getEstado()); assertEquals("RESULTADO_REUTILIZADO",b.getEstado());
        assertEquals(1,a.getRecomendaciones().size()); assertEquals(a.getRecomendaciones(),b.getRecomendaciones());
        assertEquals(PrioridadAccionAmbientalIA.BASE,a.getBasePriorizacion());
        assertEquals(PrioridadAccionAmbientalIA.BASE,b.getBasePriorizacion());
        assertEquals("MEDIA",a.getRecomendaciones().getFirst().getPrioridad());
        assertTrue(a.getRecomendaciones().getFirst().getJustificacionPrioridad().contains("recorridos reales"));
        assertEquals("NO_ESTIMABLE",a.getRecomendaciones().getFirst().getImpactoEsperado());
        assertTrue(a.getRecomendaciones().getFirst().getExplicacionImpacto().contains("no mide ese ahorro"));
        assertTrue(b.getMensaje().contains("sin modificar su contenido")); assertEquals(1,f.guardadas.size());
        assertFalse(b.getDatosFaltantes().isEmpty()); assertFalse(b.getPublicable());
    }
    @Test void abstencionYRechazoRecibenMensajesDistintosSinEscribir() {
        f.abstener=true; var a=f.entrada.generar(auth,solicitud()).getBody();
        assertEquals("ABSTENCION_MODELO",a.getEstado()); assertTrue(a.getMensaje().contains("El modelo no propuso"));
        f.abstener=false; f.accion="TR_EXPLORAR_ALTERNATIVAS_AUTO";
        var b=f.entrada.generar(auth,solicitud()).getBody();
        assertEquals("SELECCION_RECHAZADA",b.getEstado()); assertTrue(b.getMensaje().contains("no superó la validación"));
        assertTrue(f.guardadas.isEmpty());
    }
    @ParameterizedTest @EnumSource(value=OllamaClientException.Tipo.class,names={"TIEMPO_AGOTADO","NO_DISPONIBLE","ERROR_HTTP","RESPUESTA_INVALIDA"})
    void falloTipadoNoSeConvierteEnAbstencionNiExponeDetalles(OllamaClientException.Tipo tipo) {
        var error=new OllamaClientException(tipo,"DETALLE_SENSIBLE_NO_PUBLICAR");
        when(f.selector.generarV2ConAlcanceInterno(any(),anyList(),anySet())).thenThrow(error);
        assertSame(error,assertThrows(OllamaClientException.class,()->f.generacion.generarParaPruebaInterna(auth)));
        var r=f.entrada.generar(auth,solicitud()); var d=r.getBody();
        assertEquals(tipo==OllamaClientException.Tipo.TIEMPO_AGOTADO?504:tipo==OllamaClientException.Tipo.NO_DISPONIBLE?503:502,r.getStatusCode().value());
        assertEquals("OLLAMA_"+tipo.name(),d.getEstado()); assertFalse(d.getMensaje().contains("DETALLE_SENSIBLE"));
        assertFalse(d.getMensaje().contains("abstuvo")); assertTrue(d.getRecomendaciones().isEmpty());
        if(tipo==OllamaClientException.Tipo.RESPUESTA_INVALIDA) assertTrue(d.getMensaje().contains("modelo no es válida"));
        assertTrue(f.guardadas.isEmpty());
    }
    @Test void realizadaNoSePresentaComoInformacionInsuficiente() {
        var politica=mock(PoliticaAutorizacionEditorialIAService.class);
        var constructor=new ContextoRecomendacionesIAServiceImpl(f.habitos); var c=constructor.construir(91L);
        var generacion=mock(GeneracionManualIAService.class);
        when(generacion.generarParaPruebaInterna(auth)).thenReturn(new ResultadoGeneracionManualIADTO(ResultadoGeneracionManualIADTO.Estado.SIN_PROPUESTA,
                null,null,null,new ResultadoGeneracionManualIADTO.Diagnostico(c,Set.of(CONTEXTO_PARCIAL,ACCIONES_YA_REALIZADAS,CATEGORIAS_FUERA_DE_ALCANCE),List.of("Transporte"))));
        var d=new EntradaDemoIA127(generacion,f.asociaciones,f.tm).generar(auth,solicitud()).getBody();
        assertTrue(d.getMensaje().contains("ya fueron realizadas")); assertFalse(d.getMensaje().contains("Completa la información"));
        assertTrue(d.getDatosFaltantes().contains("Energía.personas: dato obligatorio ausente"));
    }
}
