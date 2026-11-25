package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.exceptions.OllamaClientException;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.*;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;
import static com.huellago.backend.dtos.ResultadoGeneracionManualIADTO.Estado.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Datos y decisiones SINTETICOS. No ejecuta Ollama, SQL ni un contexto Boot. */
class GeneracionManualIAServiceImplTest {
    static final String ID="TR_PLANIFICAR_DESPLAZAMIENTOS";
    final CapturaContextoAmbientalIAService capturas=mock(CapturaContextoAmbientalIAService.class);
    final PersistenciaRecomendacionesIAService escritor=mock(PersistenciaRecomendacionesIAService.class);
    final GeneradorRecomendacionesIAService selector=mock(GeneradorRecomendacionesIAService.class);
    final CatalogoAccionesAmbientalesServiceImpl catalogo=new CatalogoAccionesAmbientalesServiceImpl();
    final PresentacionRecomendacionesIAServiceImpl presentador=new PresentacionRecomendacionesIAServiceImpl();
    final PoliticaAutorizacionEditorialIAService editorial=new PoliticaAutorizacionEditorialIAServiceImpl(
            catalogo,presentador,new RegistroEditorialPruebaInternaFixture());
    final JsonMapper mapper=JsonMapper.builder().build();
    final ContextoRecomendacionesIAService.Resultado contexto=new ContextoRecomendacionesIAService.Resultado(
            new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte("Auto",new BigDecimal("100"),5),null,null,null),
            List.of("Energía","Alimentación","Residuos"),List.of(),ReglasInterpretacionAmbiental.REGLAS);
    final CapturaContextoAmbientalIADTO captura=new CapturaContextoAmbientalIADTO("SINTETICA",77L,contexto,Set.of(),Set.of(),Set.of(),"huella-sintetica");
    Authentication auth;
    GeneracionManualIAService servicio;

    @BeforeEach void preparar() {
        var u=new Usuario(); u.setId(77L);
        auth=new UsernamePasswordAuthenticationToken(new UserSecurity(u),null,List.of());
        when(capturas.capturar(auth)).thenReturn(captura);
        when(escritor.referenciaEditorial(auth,captura,PersistenciaRecomendacionesIAService.Destino.PRUEBA_INTERNA)).thenReturn("receta-sintetica");
        servicio=componer(editorial,selector);
    }
    GeneracionManualIAService componer(PoliticaAutorizacionEditorialIAService politica,GeneradorRecomendacionesIAService cliente) {
        return new GeneracionManualIAServiceImpl(capturas,politica,
                new OrquestadorRecomendacionesIAServiceImpl(cliente,catalogo,presentador),escritor);
    }
    ResultadoGeneracionFundamentadaIADTO propuesta(String id,String enfoque,boolean abstener) {
        Propuesta p=abstener ? new Propuesta("Transporte",Decision.ABSTENERSE,null,List.of(),null,null,null,List.of(),MotivoAbstencion.NO_VERIFICABLE)
                : new Propuesta("Transporte",Decision.PROPONER,new Accion(id,"1",enfoque==null ? Map.of() : Map.of("enfoque",new Texto(enfoque))),
                List.of(new Evidencia("/transporte/medio",new Texto("Auto"),Ambito.NO_APLICA),
                        new Evidencia("/transporte/kmSemana",new Numero(new BigDecimal("100")),Ambito.NO_APLICA),
                        new Evidencia("/transporte/diasSemana",new Numero(new BigDecimal("5")),Ambito.NO_APLICA)),null,null,null,List.of(),null);
        var dto=new RecomendacionFundamentadaIADTO("2",List.of(p));
        return new ResultadoGeneracionFundamentadaIADTO(mapper.writeValueAsString(dto),dto,
                new ResultadoEvaluacionRecomendacionIADTO(ResultadoEvaluacionRecomendacionIADTO.Estado.ACEPTADA_ESTRUCTURALMENTE,List.of(),List.of()));
    }
    void responder(ResultadoGeneracionFundamentadaIADTO s) {
        when(selector.generarV2ConAlcanceInterno(eq(contexto),anyList(),eq(Set.of()))).thenAnswer(i->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(editorial.evaluar(contexto.contexto(),Set.of()).elegibles(),i.getArgument(1));
            return s;
        });
    }
    ResultadoPersistenciaRecomendacionesIADTO guardada(boolean reutilizada) {
        return new ResultadoPersistenciaRecomendacionesIADTO(List.of(new ResultadoPersistenciaRecomendacionesIADTO.Guardada(
                10,20,reutilizada,ID,"1",null,"1","Titulo sintetico","Descripcion sintetica","Motivo sintetico","Pendiente",LocalDateTime.of(2026,11,9,12,0))));
    }
    @Test void produccionSinPermisosNoInfiereNiEscribe() {
        servicio=componer(new PoliticaAutorizacionEditorialIAServiceImpl(),selector);
        var r=servicio.generarParaPruebaInterna(auth);
        assertEquals(SIN_PROPUESTA,r.estado()); assertNull(r.seleccion()); assertNull(r.persistencia()); assertFalse(r.publicable());
        verifyNoInteractions(selector,escritor);
    }
    @ParameterizedTest @ValueSource(strings={"SIN_ENFOQUE","AGRUPACION","NECESIDAD"})
    void seleccionPresentacionYDelegacionExactas(String enfoque) {
        var s=propuesta(ID,enfoque.equals("SIN_ENFOQUE")?null:enfoque,false); responder(s);
        var persistida=guardada(false);
        when(escritor.persistir(eq(auth),eq(captura),any(),eq("receta-sintetica"),eq(PersistenciaRecomendacionesIAService.Destino.PRUEBA_INTERNA))).thenReturn(persistida);
        var r=servicio.generarParaPruebaInterna(auth);
        assertEquals(PERSISTIDA_PRUEBA_INTERNA,r.estado()); assertSame(persistida,r.persistencia()); assertSame(s,r.seleccion().seleccion());
        var p=r.seleccion().presentacion().resultados().getFirst().presentacion();
        assertEquals(enfoque,p.enfoque()); assertEquals(!enfoque.equals("SIN_ENFOQUE"),p.enfoqueSeleccionado());
        assertEquals(ResultadoPresentacionRecomendacionesIADTO.Revision.BORRADOR,p.estado()); assertFalse(p.publicable()); assertFalse(r.publicable());
        verify(escritor).persistir(auth,captura,r.seleccion(),"receta-sintetica",PersistenciaRecomendacionesIAService.Destino.PRUEBA_INTERNA);
        var orden=inOrder(capturas,escritor,selector);
        orden.verify(capturas).capturar(auth); orden.verify(escritor).referenciaEditorial(auth,captura,PersistenciaRecomendacionesIAService.Destino.PRUEBA_INTERNA);
        orden.verify(selector).generarV2ConAlcanceInterno(eq(contexto),anyList(),eq(Set.of()));
        orden.verify(escritor).persistir(eq(auth),eq(captura),any(),eq("receta-sintetica"),eq(PersistenciaRecomendacionesIAService.Destino.PRUEBA_INTERNA));
        verify(selector,never()).generarV2ConAlcanceParaPruebasSinteticas(any(),anyList(),anySet());
    }
    @Test void abstencionRealSimuladaNoPersiste() {
        var s=propuesta(ID,null,true); responder(s);
        var r=servicio.generarParaPruebaInterna(auth);
        assertEquals(ABSTENCION_MODELO,r.estado()); assertSame(s,r.seleccion().seleccion()); assertNull(r.persistencia());
        verify(escritor,never()).persistir(any(),any(),any(),anyString(),any());
    }
    @Test void accionCompatibleFueraDelSubconjuntoSeRechaza() {
        responder(propuesta("TR_EXPLORAR_ALTERNATIVAS_AUTO",null,false));
        assertEquals(SELECCION_RECHAZADA,servicio.generarParaPruebaInterna(auth).estado());
        verify(escritor,never()).persistir(any(),any(),any(),anyString(),any());
    }
    @ParameterizedTest @EnumSource(value=OllamaClientException.Tipo.class,names={"TIEMPO_AGOTADO","NO_DISPONIBLE","ERROR_HTTP","RESPUESTA_INVALIDA"})
    void errorSelectorNoEsAbstencion(OllamaClientException.Tipo tipo) {
        var error=new OllamaClientException(tipo,"Fallo sintetico");
        when(selector.generarV2ConAlcanceInterno(any(),anyList(),anySet())).thenThrow(error);
        assertSame(error,assertThrows(OllamaClientException.class,()->servicio.generarParaPruebaInterna(auth)));
        verify(escritor,never()).persistir(any(),any(),any(),anyString(),any());
    }
    @ParameterizedTest @ValueSource(strings={"CONTEXTO_MODIFICADO","ACCION_REALIZADA","CATEGORIA_PENDIENTE_OTRO_CONTEXTO","RECETA_EDITORIAL_MODIFICADA"})
    void conflictosFinalesNoSeOcultanNiReintentan(String motivo) {
        responder(propuesta(ID,null,false)); var error=new ResponseStatusException(HttpStatus.CONFLICT,motivo);
        when(escritor.persistir(any(),any(),any(),anyString(),any())).thenThrow(error);
        assertSame(error,assertThrows(ResponseStatusException.class,()->servicio.generarParaPruebaInterna(auth)));
        verify(selector,times(1)).generarV2ConAlcanceInterno(any(),anyList(),anySet());
        verify(escritor,times(1)).persistir(any(),any(),any(),anyString(),any());
    }
    @Test void errorPostgresSePropagaSinRespaldo() {
        responder(propuesta(ID,null,false)); var error=new DataAccessResourceFailureException("Fallo sintetico");
        when(escritor.persistir(any(),any(),any(),anyString(),any())).thenThrow(error);
        assertSame(error,assertThrows(DataAccessResourceFailureException.class,()->servicio.generarParaPruebaInterna(auth)));
    }
    @Test void reintentoDelegaIdempotenciaSinCambiarCapturaNiReceta() {
        responder(propuesta(ID,null,false)); var primera=guardada(false); var segunda=guardada(true);
        when(escritor.persistir(eq(auth),eq(captura),any(),eq("receta-sintetica"),any())).thenReturn(primera,segunda);
        var a=servicio.generarParaPruebaInterna(auth); var b=servicio.generarParaPruebaInterna(auth);
        assertEquals(a.persistencia().resultados().getFirst().asociacionId(),b.persistencia().resultados().getFirst().asociacionId());
        assertTrue(b.persistencia().resultados().getFirst().reutilizada());
        verify(escritor,times(2)).persistir(eq(auth),eq(captura),any(),eq("receta-sintetica"),eq(PersistenciaRecomendacionesIAService.Destino.PRUEBA_INTERNA));
    }
    @Test void sinAutenticacionSeRechazaAntesDeCapturar() {
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ResponseStatusException.class,()->servicio.generarParaPruebaInterna(null)).getStatusCode());
        verifyNoInteractions(capturas,selector,escritor);
    }
    @Test void capturaAjenaSeRechaza() {
        when(capturas.capturar(auth)).thenReturn(new CapturaContextoAmbientalIADTO("SINTETICA",88L,contexto,Set.of(),Set.of(),Set.of(),"otra"));
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,()->servicio.generarParaPruebaInterna(auth)).getStatusCode());
        verifyNoInteractions(selector,escritor);
    }
    @Test void transaccionActivaRechazadaAntesDeCapturar() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class,()->servicio.generarParaPruebaInterna(auth)); }
        finally { TransactionSynchronizationManager.clear(); }
        verifyNoInteractions(capturas,selector,escritor);
    }
    @Test void realizadaNoSeSuministraAlSelector() {
        when(capturas.capturar(auth)).thenReturn(new CapturaContextoAmbientalIADTO("SINTETICA",77L,contexto,
                Set.of(new AccionAmbientalAdmisible.Referencia(ID,"1")),Set.of(),Set.of(),"otra"));
        assertEquals(SIN_PROPUESTA,servicio.generarParaPruebaInterna(auth).estado()); verifyNoInteractions(selector,escritor);
    }
    @Test void clienteInternoHttpSimuladoConservaEsquemaYLimite() {
        var builder=RestClient.builder().baseUrl("http://localhost:11434");
        var servidor=MockRestServiceServer.bindTo(builder).build();
        var cliente=new OllamaRecomendacionesServiceImpl(builder.build(),new OllamaConfiguration.Ajustes(
                URI.create("http://localhost:11434"),"qwen3:4b-instruct",Duration.ofSeconds(3),Duration.ofSeconds(120),65536));
        var s=propuesta(ID,null,false);
        servidor.expect(requestTo("http://localhost:11434/api/chat")).andExpect(method(HttpMethod.POST)).andExpect(req->{
            String body=((org.springframework.mock.http.client.MockClientHttpRequest)req).getBodyAsString();
            assertTrue(body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=16384);
            System.out.println("PASO122 solicitud interna HTTP simulada: "+body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+" bytes");
            assertTrue(body.contains(ID)); assertFalse(body.contains("TR_EXPLORAR_ALTERNATIVAS_AUTO"));
            assertFalse(body.contains("usuarioId")); assertFalse(body.contains("receta-sintetica"));
            assertEquals("qwen3:4b-instruct",mapper.readTree(body).get("model").asString());
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        }).andRespond(withSuccess(mapper.writeValueAsString(Map.of("model","qwen3:4b-instruct","done",true,
                "message",Map.of("role","assistant","content",s.propuestaJson()))),MediaType.APPLICATION_JSON));
        servicio=componer(editorial,cliente);
        when(escritor.persistir(any(),any(),any(),anyString(),any())).thenReturn(guardada(false));
        assertEquals(PERSISTIDA_PRUEBA_INTERNA,servicio.generarParaPruebaInterna(auth).estado()); servidor.verify();
    }

    @Test void composicionConCapturaYEscritorRealesPorProxySinBaseDeDatos() {
        // Reutiliza la preparacion aislada existente: gestor sin recursos y repositorios Mockito.
        var f=new PersistenciaRecomendacionesIAServiceImplTest(); f.preparar();
        f.permisos.clear(); f.permisos.addAll(new RegistroEditorialPruebaInternaFixture().consultar());
        when(selector.generarV2ConAlcanceInterno(any(),anyList(),anySet())).thenAnswer(i->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return f.seleccion.seleccion();
        });
        var aplicacion=new GeneracionManualIAServiceImpl(f.captura,f.editorial,
                new OrquestadorRecomendacionesIAServiceImpl(selector,catalogo,presentador),f.servicio);
        var a=aplicacion.generarParaPruebaInterna(f.auth);
        var b=aplicacion.generarParaPruebaInterna(f.auth);
        assertEquals(PERSISTIDA_PRUEBA_INTERNA,a.estado()); assertFalse(a.publicable());
        assertTrue(b.persistencia().resultados().getFirst().reutilizada()); assertEquals(1,f.guardadas.size());
        var p=a.seleccion().presentacion().resultados().getFirst().presentacion();
        var guardada=f.guardadas.getFirst();
        assertEquals(p.titulo(),guardada.getTituloInstantanea()); assertEquals(p.descripcion(),guardada.getDescripcionInstantanea());
        assertEquals(p.motivo(),guardada.getMotivoInstantanea()); assertNull(guardada.getEnfoque());
        assertEquals(30,f.usuario.getEcoPuntos());
        verify(f.asociaciones,times(1)).saveAndFlush(any()); verify(f.acciones,times(1)).saveAndFlush(any());
        var orden=inOrder(f.usuarios,f.habitos,f.asociaciones);
        orden.verify(f.usuarios).bloquearParaRecomendaciones(77L);
        orden.verify(f.habitos).findByUsuario_Id(77L); orden.verify(f.asociaciones).saveAndFlush(any());
    }

    @ParameterizedTest @ValueSource(strings={"habitos","realizada","editorial","pendiente","fallo"})
    void composicionRealRechazaCambiosDuranteInferenciaSinEscrituraAlternativa(String caso) {
        var f=new PersistenciaRecomendacionesIAServiceImplTest(); f.preparar();
        f.permisos.clear(); f.permisos.addAll(new RegistroEditorialPruebaInternaFixture().consultar());
        when(selector.generarV2ConAlcanceInterno(any(),anyList(),anySet())).thenAnswer(i->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            switch(caso) {
                case "habitos" -> f.datos.get(1).setValor(new BigDecimal("101"));
                case "realizada" -> {
                    var r=new com.huellago.backend.entities.RecomendacionIA(); r.setAccionId(ID); r.setVersionAccion("1");
                    f.realizadas.add(new com.huellago.backend.entities.UsuarioRecomendacionIA(4,"completado",LocalDateTime.now(),LocalDateTime.now(),f.usuario,r));
                }
                case "editorial" -> f.permisos.clear();
                case "pendiente" -> {
                    var otraIA=new com.huellago.backend.entities.UsuarioRecomendacionIA();
                    otraIA.setEstado("pendiente");
                    otraIA.setMetodoGeneracion(PersistenciaRecomendacionesIAServiceImpl.METODO);
                    when(f.asociaciones.findByUsuario_IdAndEstadoIgnoreCaseAndRecomendacionIA_Categoria_Id(77L,"pendiente",2L))
                            .thenReturn(List.of(otraIA));
                }
                case "fallo" -> doThrow(new DataAccessResourceFailureException("Fallo sintetico")).when(f.acciones).saveAndFlush(any());
            }
            return f.seleccion.seleccion();
        });
        var aplicacion=new GeneracionManualIAServiceImpl(f.captura,f.editorial,
                new OrquestadorRecomendacionesIAServiceImpl(selector,catalogo,presentador),f.servicio);
        if(caso.equals("fallo")) assertThrows(DataAccessResourceFailureException.class,()->aplicacion.generarParaPruebaInterna(f.auth));
        else {
            var error=assertThrows(ResponseStatusException.class,()->aplicacion.generarParaPruebaInterna(f.auth));
            assertEquals(switch(caso) {
                case "editorial" -> "SIN_AUTORIZACION_EDITORIAL";
                case "pendiente" -> "CATEGORIA_PENDIENTE_OTRO_CONTEXTO";
                default -> "CONTEXTO_MODIFICADO";
            },error.getReason());
        }
        assertTrue(f.guardadas.isEmpty()); verify(f.asociaciones,never()).saveAndFlush(any());
        verify(selector,times(1)).generarV2ConAlcanceInterno(any(),anyList(),anySet());
    }

    @Test void selectorInternoRechazaTransaccionAntesDeValidarOTransmitir() {
        var builder=RestClient.builder().baseUrl("http://localhost:11434");
        var servidor=MockRestServiceServer.bindTo(builder).build();
        var cliente=new OllamaRecomendacionesServiceImpl(builder.build(),new OllamaConfiguration.Ajustes(
                URI.create("http://localhost:11434"),"qwen3:4b-instruct",Duration.ofSeconds(3),Duration.ofSeconds(120),65536));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class,()->cliente.generarV2ConAlcanceInterno(contexto,
                editorial.evaluar(contexto.contexto(),Set.of()).elegibles(),Set.of())); }
        finally { TransactionSynchronizationManager.clear(); }
        servidor.verify();
    }
}
