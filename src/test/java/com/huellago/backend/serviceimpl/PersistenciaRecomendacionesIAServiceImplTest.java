package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.huellago.backend.services.PersistenciaRecomendacionesIAService.Destino.*;

/** Selecciones SINTÉTICAS; proxies Spring y gestor sin recursos, repositorios Mockito.
 * No certifica exclusión, rollback ni restricciones físicas PostgreSQL. */
class PersistenciaRecomendacionesIAServiceImplTest {
    static final String ACCION="TR_PLANIFICAR_DESPLAZAMIENTOS";
    final UsuarioRepository usuarios=mock(UsuarioRepository.class);
    final RecomendacionIARepository acciones=mock(RecomendacionIARepository.class);
    final UsuarioRecomendacionIARepository asociaciones=mock(UsuarioRecomendacionIARepository.class);
    final CategoriaHabitoRepository categorias=mock(CategoriaHabitoRepository.class);
    final HabitoRepository habitos=mock(HabitoRepository.class);
    final UsuarioRetoRepository retos=mock(UsuarioRetoRepository.class);
    final TransaccionesAisladasIA tx=new TransaccionesAisladasIA();
    final jakarta.persistence.EntityManager em=mock(jakarta.persistence.EntityManager.class);
    final CatalogoAccionesAmbientalesService catalogo=new CatalogoAccionesAmbientalesServiceImpl();
    final PresentacionRecomendacionesIAService presentador=new PresentacionRecomendacionesIAServiceImpl();
    final JsonMapper mapper=JsonMapper.builder().build();
    final List<PoliticaAutorizacionEditorialIAService.Autorizacion> permisos=new ArrayList<>();
    final List<UsuarioRecomendacionIA> guardadas=new ArrayList<>();
    final List<UsuarioRecomendacionIA> realizadas=new ArrayList<>();
    final List<Habito> datos=new ArrayList<>();
    Usuario usuario; Authentication auth; CapturaContextoAmbientalIADTO inicial;
    CapturaContextoAmbientalIAService captura;
    PoliticaAutorizacionEditorialIAService editorial;
    PersistenciaRecomendacionesIAService servicio;
    OrquestadorRecomendacionesIAService.Resultado seleccion;
    String receta;
    @BeforeEach void preparar() {
        usuario=new Usuario(); usuario.setId(77L); usuario.setEcoPuntos(30);
        auth=new UsernamePasswordAuthenticationToken(new UserSecurity(usuario),null,List.of());
        var categoria=new CategoriaHabito(); categoria.setId(2L); categoria.setNombre("Transporte");
        String[] nombres={"medio","kmSemana","diasSemana"}, valores={"0","100","5"}, unidades={"Auto","km/semana","dias/semana"};
        for(int i=0;i<3;i++) {
            var h=new Habito(); h.setId((long)i+1); h.setNombre(nombres[i]); h.setValor(new BigDecimal(valores[i]));
            h.setUnidad(unidades[i]); h.setUsuario(usuario); h.setCategoria(categoria); h.setFechaRegistro(LocalDateTime.of(2026,11,8,10,0)); datos.add(h);
        }
        when(usuarios.existsById(77L)).thenReturn(true);
        when(usuarios.bloquearParaRecomendaciones(77L)).thenAnswer(i->{
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); return Optional.of(77L);
        });
        when(usuarios.findById(77L)).thenReturn(Optional.of(usuario));
        when(habitos.findByUsuario_Id(77L)).thenAnswer(i->List.copyOf(datos));
        when(asociaciones.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(77L,"completado")).thenAnswer(i->List.copyOf(realizadas));
        captura=proxy(new CapturaContextoAmbientalIAServiceImpl(usuarios,asociaciones,retos,new ContextoRecomendacionesIAServiceImpl(habitos)),CapturaContextoAmbientalIAService.class);
        inicial=captura.capturar(auth);
        editorial=new PoliticaAutorizacionEditorialIAServiceImpl(catalogo,presentador,()->List.copyOf(permisos));
        for(var d:presentador.definicionesParaRevision()) if(d.accionId().equals(ACCION))
            permisos.add(new PoliticaAutorizacionEditorialIAService.Autorizacion(d.accionId(),d.versionAccion(),d.enfoque(),d.versionPresentacion(),editorial.huellaDefinicion(d),d.camposContextoPermitidos()));
        servicio=proxy(new PersistenciaRecomendacionesIAServiceImpl(usuarios,acciones,asociaciones,categorias,captura,em,editorial,true),PersistenciaRecomendacionesIAService.class);
        seleccion=seleccionar(null,false); receta=servicio.referenciaEditorial(auth,inicial);
        when(categorias.findByNombreIgnoreCase("Transporte")).thenReturn(categoria);
        when(categorias.bloquearParaRecomendaciones(2L)).thenReturn(Optional.of(2L));
        when(acciones.saveAndFlush(any())).thenAnswer(i->{ RecomendacionIA r=i.getArgument(0); r.setId(8); return r; });
        when(asociaciones.saveAndFlush(any())).thenAnswer(i->{ UsuarioRecomendacionIA a=i.getArgument(0); a.setId(30+guardadas.size()); guardadas.add(a); return a; });
        when(asociaciones.findByUsuario_IdAndReferenciaContextoAndMetodoGeneracion(eq(77L),anyString(),anyString()))
                .thenAnswer(i->guardadas.stream().filter(g->g.getReferenciaContexto().equals(i.getArgument(1))).toList());
        clearInvocations(usuarios,asociaciones,habitos,retos);
    }
    <T> T proxy(Object target,Class<T> tipo) {
        var p=new ProxyFactory(target); p.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource())); return tipo.cast(p.getProxy());
    }
    OrquestadorRecomendacionesIAService.Resultado seleccionar(String enfoque,boolean abstenerse) {
        var permitidas=editorial.evaluar(inicial.contexto().contexto(),inicial.realizadas()).elegibles();
        var alcance=new ValidadorRecomendacionFundamentadaIA().construirAlcanceExperimental(inicial.contexto().contexto(),permitidas,inicial.realizadas());
        List<RecomendacionFundamentadaIADTO.Evidencia> ev=List.of(
                new RecomendacionFundamentadaIADTO.Evidencia("/transporte/medio",new RecomendacionFundamentadaIADTO.Texto("Auto"),RecomendacionFundamentadaIADTO.Ambito.NO_APLICA),
                new RecomendacionFundamentadaIADTO.Evidencia("/transporte/kmSemana",new RecomendacionFundamentadaIADTO.Numero(inicial.contexto().contexto().transporte().kmSemana()),RecomendacionFundamentadaIADTO.Ambito.NO_APLICA),
                new RecomendacionFundamentadaIADTO.Evidencia("/transporte/diasSemana",new RecomendacionFundamentadaIADTO.Numero(new BigDecimal("5")),RecomendacionFundamentadaIADTO.Ambito.NO_APLICA));
        var accion=abstenerse?null:new RecomendacionFundamentadaIADTO.Accion(ACCION,"1",enfoque==null?Map.of():Map.of("enfoque",new RecomendacionFundamentadaIADTO.Texto(enfoque)));
        var p=new RecomendacionFundamentadaIADTO.Propuesta("Transporte",abstenerse?RecomendacionFundamentadaIADTO.Decision.ABSTENERSE:RecomendacionFundamentadaIADTO.Decision.PROPONER,
                accion,abstenerse?List.of():ev,null,null,null,List.of(),abstenerse?RecomendacionFundamentadaIADTO.MotivoAbstencion.NO_VERIFICABLE:null);
        var dto=new RecomendacionFundamentadaIADTO("2",List.of(p)); String json=mapper.writeValueAsString(dto);
        var evaluacion=new ValidadorRecomendacionFundamentadaIA().validarConAlcanceExperimental(json,inicial.contexto().contexto(),alcance,inicial.realizadas());
        var s=new ResultadoGeneracionFundamentadaIADTO(json,dto,evaluacion);
        var presentacion=presentador.presentarConAlcanceParaPruebasSinteticas(inicial.contexto().contexto(),s,alcance,inicial.realizadas());
        assertEquals(ResultadoPresentacionRecomendacionesIADTO.Estado.SELECCION_ACEPTADA,presentacion.estado());
        return new OrquestadorRecomendacionesIAService.Resultado(s,presentacion,alcance,Set.of("Transporte"));
    }
    ResultadoPersistenciaRecomendacionesIADTO guardar() { return servicio.persistir(auth,inicial,seleccion,receta,PRUEBA_INTERNA); }
    void sinEscrituras() { verify(acciones,never()).saveAndFlush(any()); verify(asociaciones,never()).saveAndFlush(any()); }
    void conflicto(String motivo) { assertEquals(motivo,assertThrows(ResponseStatusException.class,this::guardar).getReason()); sinEscrituras(); }
    @Test void guardaSoloInstantaneaExactaBajoBloqueoSinPuntosNiModificarSeleccion() {
        String antes=mapper.writeValueAsString(seleccion); var r=guardar(); var a=guardadas.getFirst(); var p=seleccion.presentacion().resultados().getFirst().presentacion();
        assertFalse(r.publicable()); assertEquals(p.titulo(),a.getTituloInstantanea()); assertEquals(p.descripcion(),a.getDescripcionInstantanea());
        assertEquals(p.motivo(),a.getMotivoInstantanea()); assertNull(a.getEnfoque()); assertEquals("1",a.getVersionPresentacion());
        assertEquals(74,a.getReferenciaContexto().length()); assertFalse(a.getReferenciaContexto().contains("Auto"));
        assertEquals(30,usuario.getEcoPuntos()); assertNull(a.getFechaCompletado()); assertEquals("Pendiente",a.getEstado());
        assertNotEquals(p.titulo(),a.getRecomendacionIA().getTitulo()); assertFalse(a.getRecomendacionIA().getDescripcion().contains("100"));
        assertEquals(antes,mapper.writeValueAsString(seleccion));
        var orden=inOrder(em,usuarios,habitos,asociaciones);
        orden.verify(em).clear();
        orden.verify(usuarios).bloquearParaRecomendaciones(77L); orden.verify(usuarios).existsById(77L);
        orden.verify(habitos).findByUsuario_Id(77L); orden.verify(asociaciones).saveAndFlush(any());
        verify(retos,never()).save(any()); verify(usuarios,never()).save(any()); verify(habitos,never()).save(any());
    }
    @ParameterizedTest @ValueSource(strings={"AGRUPACION","NECESIDAD"}) void conservaEnfoqueRealDeSeleccionSintetica(String enfoque) {
        seleccion=seleccionar(enfoque,false); guardar(); assertEquals(enfoque,guardadas.getFirst().getEnfoque());
    }
    @Test void repeticionIdenticaReutilizaPrimerResultadoSinCambiarlo() {
        var primero=guardar(); var a=guardadas.getFirst(); var fecha=a.getFechaGeneracion();
        seleccion=seleccionar("AGRUPACION",false); var segundo=guardar();
        assertEquals(primero.resultados().getFirst().asociacionId(),segundo.resultados().getFirst().asociacionId());
        assertTrue(segundo.resultados().getFirst().reutilizada()); assertEquals(fecha,a.getFechaGeneracion()); assertNull(a.getEnfoque());
        verify(asociaciones,times(1)).saveAndFlush(any()); verify(acciones,times(1)).saveAndFlush(any());
    }
    @ParameterizedTest @ValueSource(strings={"sinPermiso","familiaIncompleta","hashEditorial","versionEditorial"})
    void fallaCerradoAntePermisoEditorialInvalido(String caso) {
        if(caso.equals("sinPermiso")) permisos.clear();
        else if(caso.equals("familiaIncompleta")) permisos.removeFirst();
        else { var p=permisos.getFirst(); permisos.set(0,new PoliticaAutorizacionEditorialIAService.Autorizacion(p.accionId(),p.versionAccion(),p.enfoque(),
                caso.equals("versionEditorial")?"99":p.versionPresentacion(),caso.equals("hashEditorial")?"0".repeat(64):p.sha256(),p.camposContextoPermitidos())); }
        conflicto("SIN_AUTORIZACION_EDITORIAL");
    }
    @Test void cambioDeRecetaAntesDeConfirmarRechaza() { receta="0".repeat(64); conflicto("RECETA_EDITORIAL_MODIFICADA"); }
    @Test void sinIdentidadNoLeeRepositorios() {
        auth=null; conflicto("IDENTIDAD_INVALIDA"); verifyNoInteractions(usuarios,habitos);
    }
    @Test void capturaAjenaNoLeeRepositorios() {
        var otro=new Usuario(); otro.setId(78L); auth=new UsernamePasswordAuthenticationToken(new UserSecurity(otro),null,List.of());
        conflicto("CAPTURA_AJENA"); verifyNoInteractions(usuarios,habitos);
    }
    @Test void usuarioInexistenteEs404() {
        doReturn(Optional.empty()).when(usuarios).bloquearParaRecomendaciones(77L);
        assertEquals(404,assertThrows(ResponseStatusException.class,this::guardar).getStatusCode().value()); sinEscrituras();
    }
    @Test void cambioHabitualSeDetectaDentroDelBloqueo() { datos.get(1).setValor(new BigDecimal("101")); conflicto("CONTEXTO_MODIFICADO"); }
    @Test void realizadaDuranteInferenciaImpideGuardar() {
        var r=new RecomendacionIA(); r.setAccionId(ACCION); r.setVersionAccion("1");
        realizadas.add(new UsuarioRecomendacionIA(4,"completado",LocalDateTime.now(),LocalDateTime.now(),usuario,r));
        conflicto("CONTEXTO_MODIFICADO");
    }
    @Test void nuevoLegadoCompletadoInvalidaSinInventarIdentidad() {
        realizadas.add(new UsuarioRecomendacionIA(4,"completado",LocalDateTime.now(),LocalDateTime.now(),usuario,new RecomendacionIA()));
        conflicto("CONTEXTO_MODIFICADO");
    }
    @Test void pendienteDeOtraCapturaEnCategoriaEsConflicto() {
        var otraIA=new UsuarioRecomendacionIA(); otraIA.setMetodoGeneracion(PersistenciaRecomendacionesIAServiceImpl.METODO);
        when(asociaciones.findByUsuario_IdAndEstadoIgnoreCaseAndRecomendacionIA_Categoria_Id(77L,"pendiente",2L)).thenReturn(List.of(otraIA));
        conflicto("CATEGORIA_PENDIENTE_OTRO_CONTEXTO");
        assertTrue(EntradaRecomendacionesIA.respuestaPara("CATEGORIA_PENDIENTE_OTRO_CONTEXTO","USO_UNIVERSITARIO_LOCAL")
                .getMensaje().contains("recomendación IA pendiente"));
    }
    @Test void pendienteDeterministaHu37CoexisteConIaSinSobrescribirNiDuplicar() {
        var legado=new UsuarioRecomendacionIA(); legado.setId(42); legado.setUsuario(usuario);
        legado.setEstado("Pendiente"); legado.setFechaGeneracion(LocalDateTime.of(2026,11,24,10,0));
        var original=legado.getFechaGeneracion();
        when(asociaciones.findByUsuario_IdAndEstadoIgnoreCaseAndRecomendacionIA_Categoria_Id(77L,"pendiente",2L))
                .thenReturn(List.of(legado));
        var primera=guardar(); var segunda=guardar();
        assertEquals(1,guardadas.size()); assertEquals(42,legado.getId());
        assertEquals("Pendiente",legado.getEstado()); assertEquals(original,legado.getFechaGeneracion());
        assertEquals(primera.resultados().getFirst().asociacionId(),segunda.resultados().getFirst().asociacionId());
        assertTrue(segunda.resultados().getFirst().reutilizada());
        verify(asociaciones,times(1)).saveAndFlush(any());
    }
    @Test void nuevaCapturaProduceOtraClaveSinSobrescribirAnterior() {
        guardar(); var primera=guardadas.getFirst(); String clave=primera.getReferenciaContexto();
        datos.get(1).setValor(new BigDecimal("200")); inicial=captura.capturar(auth); seleccion=seleccionar(null,false); receta=servicio.referenciaEditorial(auth,inicial);
        guardar(); assertNotEquals(clave,guardadas.getLast().getReferenciaContexto()); assertEquals(clave,primera.getReferenciaContexto());
    }
    @Test void falloDeUnicidadPropagaYProxySolicitaRollbackSinInsercionAlternativa() {
        doThrow(new DataIntegrityViolationException("UNIQUE sintético")).when(asociaciones).saveAndFlush(any()); int antes=tx.reversiones;
        assertThrows(DataIntegrityViolationException.class,this::guardar); assertEquals(antes+1,tx.reversiones);
        verify(asociaciones,times(1)).saveAndFlush(any());
    }
    @Test void falloDeCatalogoNoIntentaAsociacion() {
        doThrow(new DataIntegrityViolationException("UNIQUE sintético")).when(acciones).saveAndFlush(any());
        assertThrows(DataIntegrityViolationException.class,this::guardar); verify(asociaciones,never()).saveAndFlush(any());
    }
    @Test void versionCanonicaDeOtraCategoriaNoSeReutiliza() {
        var r=new RecomendacionIA(); r.setId(8); var c=new CategoriaHabito(); c.setId(9L); r.setCategoria(c);
        when(acciones.findByAccionIdAndVersionAccionOrderByIdAsc(ACCION,"1")).thenReturn(List.of(r)); conflicto("CATEGORIA_CANONICA_INVALIDA");
    }
    @Test void abstencionNoGuardaNada() { seleccion=seleccionar(null,true); assertTrue(guardar().resultados().isEmpty()); sinEscrituras(); }
    @Test void noAceptaPresentacionIncompletaDeclaradaValida() {
        seleccion=new OrquestadorRecomendacionesIAService.Resultado(seleccion.seleccion(),new ResultadoPresentacionRecomendacionesIADTO(
                ResultadoPresentacionRecomendacionesIADTO.Estado.SELECCION_ACEPTADA,seleccion.presentacion().evaluacionVerificada(),List.of()),seleccion.alcance(),Set.of("Transporte"));
        conflicto("SELECCION_O_PRESENTACION_INVALIDA");
    }
    @Test void noHayPublicacionAunqueExistaAutorizacionInterna() {
        assertEquals("AUTORIZACION_PUBLICABLE_NO_DISPONIBLE",assertThrows(ResponseStatusException.class,()->servicio.persistir(auth,inicial,seleccion,receta,PUBLICABLE)).getReason());
        sinEscrituras();
    }
    @Test void constructorProductivoTieneCeroPermisosYNoReutilizaFixture() {
        var cerrado=proxy(new PersistenciaRecomendacionesIAServiceImpl(usuarios,acciones,asociaciones,categorias,captura,em),PersistenciaRecomendacionesIAService.class);
        assertThrows(ResponseStatusException.class,()->cerrado.persistir(auth,inicial,seleccion,receta,PRUEBA_INTERNA)); sinEscrituras();
    }
    @Test void invocacionDirectaSinProxyNoPuedeGuardar() {
        var directo=new PersistenciaRecomendacionesIAServiceImpl(usuarios,acciones,asociaciones,categorias,captura,em,editorial,true);
        assertThrows(IllegalStateException.class,()->directo.persistir(auth,inicial,seleccion,receta,PRUEBA_INTERNA)); sinEscrituras();
    }
    @Test void dosUsuariosCompartenSoloIdentidadCanonicaConInstantaneasPropias() {
        guardar(); var primera=guardadas.getFirst(); var canonica=primera.getRecomendacionIA();
        var otro=new Usuario(); otro.setId(78L); otro.setEcoPuntos(0);
        datos.forEach(h->h.setUsuario(otro)); datos.get(1).setValor(new BigDecimal("200"));
        when(usuarios.existsById(78L)).thenReturn(true);
        when(usuarios.bloquearParaRecomendaciones(78L)).thenReturn(Optional.of(78L));
        when(usuarios.findById(78L)).thenReturn(Optional.of(otro)); when(habitos.findByUsuario_Id(78L)).thenReturn(datos);
        auth=new UsernamePasswordAuthenticationToken(new UserSecurity(otro),null,List.of());
        inicial=captura.capturar(auth); seleccion=seleccionar(null,false); receta=servicio.referenciaEditorial(auth,inicial);
        when(acciones.findByAccionIdAndVersionAccionOrderByIdAsc(ACCION,"1")).thenReturn(List.of(canonica));
        guardar(); var segunda=guardadas.getLast();
        assertSame(canonica,segunda.getRecomendacionIA()); assertNotEquals(primera.getUsuario().getId(),segunda.getUsuario().getId());
        assertNotEquals(primera.getMotivoInstantanea(),segunda.getMotivoInstantanea()); assertNotEquals(primera.getReferenciaContexto(),segunda.getReferenciaContexto());
        verify(acciones,times(1)).saveAndFlush(any()); assertEquals(30,usuario.getEcoPuntos());
    }
    @ParameterizedTest @ValueSource(strings={"version","accion","evidencia","json","parametro"})
    void noConfiaEnEvaluacionPreviaNiReparaSeleccionAlterada(String caso) {
        var s=seleccion.seleccion(); String json=s.propuestaJson();
        json=switch(caso) {
            case "version" -> json.replace("\"version\":\"1\"","\"version\":\"99\"");
            case "accion" -> json.replace(ACCION,"TR_EXPLORAR_ALTERNATIVAS_AUTO");
            case "evidencia" -> json.replace("Auto","Bus");
            case "parametro" -> json.replace("\"parametros\":{}","\"parametros\":{\"otro\":{\"tipo\":\"TEXTO\",\"valor\":\"x\"}}");
            default -> "{malformado";
        };
        var alterada=new ResultadoGeneracionFundamentadaIADTO(json,s.propuesta(),s.evaluacion());
        seleccion=new OrquestadorRecomendacionesIAService.Resultado(alterada,seleccion.presentacion(),seleccion.alcance(),seleccion.categoriasRespondidas());
        conflicto("SELECCION_O_PRESENTACION_INVALIDA");
    }
    @Test void filaCanonicaDuplicadaEsConflictoNoSeleccionDelPrimerId() {
        when(acciones.findByAccionIdAndVersionAccionOrderByIdAsc(ACCION,"1")).thenReturn(List.of(new RecomendacionIA(),new RecomendacionIA()));
        conflicto("IDENTIDAD_CANONICA_DUPLICADA");
    }
    @Test void reintentoNoEncubreInstantaneaPersistidaIncompleta() {
        guardar(); guardadas.getFirst().setMotivoInstantanea(null); clearInvocations(asociaciones,acciones);
        conflicto("INSTANTANEA_INCOMPLETA");
    }
    @Test void procesoDePersistenciaNoTienePuertoOllamaNiEventosNiRegistroAutomatico() {
        assertFalse(Arrays.stream(PersistenciaRecomendacionesIAServiceImpl.class.getDeclaredFields())
                .anyMatch(f->f.getType().getName().contains("Generador") || f.getType().getName().contains("EventPublisher")));
        assertNull(PersistenciaRecomendacionesIAServiceImpl.class.getAnnotation(org.springframework.stereotype.Service.class));
    }
    @Test void escritorYComprobacionMandatoryCompartenUnaUnicaUnidadTransaccional() {
        int comienzos=tx.comienzos, commits=tx.confirmaciones;
        var r=guardar(); assertEquals(comienzos+1,tx.comienzos); assertEquals(commits+1,tx.confirmaciones);
        assertFalse(r.resultados().getFirst().publicable()); assertEquals("BORRADOR",r.resultados().getFirst().estadoEditorial());
        assertEquals("PENDIENTE_REVISION",r.resultados().getFirst().revisionHumana()); verify(em,never()).flush();
    }
    @Test void firmasDeConsultasDerivadasCorrespondenAPropiedadesReales() {
        assertDoesNotThrow(()->new org.springframework.data.repository.query.parser.PartTree(
                "findByUsuario_IdAndReferenciaContextoAndMetodoGeneracion",UsuarioRecomendacionIA.class));
        assertDoesNotThrow(()->new org.springframework.data.repository.query.parser.PartTree(
                "findByUsuario_IdAndEstadoIgnoreCaseAndRecomendacionIA_Categoria_Id",UsuarioRecomendacionIA.class));
    }
}
