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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static com.huellago.backend.services.CapturaContextoAmbientalIAService.Estado.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Constructor ambiental real y repositorios Mockito; sin DataSource, contexto Spring Boot ni HTTP. */
class CapturaContextoAmbientalIAServiceImplTest {
    final UsuarioRepository usuarios=mock(UsuarioRepository.class);
    final HabitoRepository habitos=mock(HabitoRepository.class);
    final UsuarioRecomendacionIARepository recomendaciones=mock(UsuarioRecomendacionIARepository.class);
    final UsuarioRetoRepository retos=mock(UsuarioRetoRepository.class);
    final CapturaContextoAmbientalIAServiceImpl servicio=new CapturaContextoAmbientalIAServiceImpl(usuarios,recomendaciones,retos,
            new ContextoRecomendacionesIAServiceImpl(habitos));
    final JsonMapper mapper=JsonMapper.builder().build();
    final List<Habito> datos=new ArrayList<>();
    final List<UsuarioRecomendacionIA> completadas=new ArrayList<>(), pendientes=new ArrayList<>();
    final List<UsuarioReto> completados=new ArrayList<>();
    final Usuario usuario=new Usuario();
    Authentication autenticacion;
    @BeforeEach void preparar() {
        usuario.setId(68L); usuario.setCorreo("NO_DEBE_SALIR"); usuario.setContrasena("NO_DEBE_SALIR");
        autenticacion=new UsernamePasswordAuthenticationToken(new UserSecurity(usuario),null,List.of());
        when(usuarios.existsById(68L)).thenReturn(true);
        when(habitos.findByUsuario_Id(68L)).thenAnswer(i -> new ArrayList<>(datos));
        when(recomendaciones.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(68L,"completado"))
                .thenAnswer(i -> new ArrayList<>(completadas));
        when(recomendaciones.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(68L,"pendiente"))
                .thenAnswer(i -> new ArrayList<>(pendientes));
        when(retos.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaAceptacionDesc(68L,"completado"))
                .thenAnswer(i -> new ArrayList<>(completados));
        agregar("Transporte","medio","0","Auto"); agregar("Transporte","kmSemana","100","km/semana");
        agregar("Transporte","diasSemana","5","dias/semana");
        agregar("Energía","vivienda","0","Casa"); agregar("Energía","personas","3","personas");
        agregar("Energía","fuente","0","Electricidad"); agregar("Energía","consumoKwhDiario","12.345","kWh/dia");
        agregar("Alimentación","tipo","0","Vegetariana");
        agregar("Residuos","plasticos","0","Medio"); agregar("Residuos","reciclas","0","boolean");
    }
    void agregar(String categoria,String nombre,String valor,String unidad) {
        var c=new CategoriaHabito(); c.setNombre(categoria);
        var h=new Habito(); h.setId((long)datos.size()+1); h.setUsuario(usuario); h.setCategoria(c);
        h.setNombre(nombre); h.setValor(new BigDecimal(valor)); h.setUnidad(unidad);
        h.setFechaRegistro(LocalDateTime.of(2026,11,8,10,0)); datos.add(h);
    }
    Habito campo(String nombre) { return datos.stream().filter(h -> nombre.equals(h.getNombre())).findFirst().orElseThrow(); }
    UsuarioRecomendacionIA asociacion(int id,String estado,String accion,String version) {
        var r=new RecomendacionIA(); r.setId(id); r.setTitulo("Título heredado no es identidad"); r.setAccionId(accion); r.setVersionAccion(version);
        return new UsuarioRecomendacionIA(id,estado,LocalDateTime.of(2026,11,8,10,0),null,usuario,r);
    }
    @Test void cuatroCategoriasUsanConstructorRealSinCredencialesNiEntidadesEnCaptura() {
        var c=servicio.capturar(autenticacion);
        assertTrue(c.contexto().categoriasIncompletas().isEmpty()); assertEquals("Auto",c.contexto().contexto().transporte().medio());
        assertEquals(new BigDecimal("12.345"),c.contexto().contexto().energia().consumoKwhDiario());
        assertTrue(c.huellaVigencia().matches("[0-9a-f]{64}"));
        assertFalse(mapper.writeValueAsString(c).contains("NO_DEBE_SALIR")); assertFalse(c.toString().contains("12.345"));
        assertEquals(VIGENTE,servicio.revalidar(autenticacion,c).estado());
        verifyNoWrites();
    }
    @Test void categoriasParcialesYAusentesSeConservanSinInventarValores() {
        datos.removeIf(h -> !h.getCategoria().getNombre().equals("Transporte"));
        var c=servicio.capturar(autenticacion);
        assertEquals(Set.of("Energía","Alimentación","Residuos"),new HashSet<>(c.contexto().categoriasIncompletas()));
        assertNull(c.contexto().contexto().energia()); assertNull(c.contexto().contexto().alimentacion());
        datos.clear(); var vacio=servicio.capturar(autenticacion);
        assertEquals(4,vacio.contexto().categoriasIncompletas().size()); assertNull(vacio.contexto().contexto().transporte());
        assertEquals(VIGENTE,servicio.revalidar(autenticacion,vacio).estado());
    }
    @Test void categoriaInvalidaSeMantieneIncompletaYConsumoOpcionalNoSeInventa() {
        campo("diasSemana").setValor(new BigDecimal("8")); datos.remove(campo("consumoKwhDiario"));
        var c=servicio.capturar(autenticacion);
        assertNull(c.contexto().contexto().transporte()); assertTrue(c.contexto().categoriasIncompletas().contains("Transporte"));
        assertNull(c.contexto().contexto().energia().consumoKwhDiario());
    }
    @Test void capturaEsDefensivaAnteCambiosDeEntidadesYConjuntos() {
        completadas.add(asociacion(1,"completado","TR_PLANIFICAR_DESPLAZAMIENTOS","1"));
        var c=servicio.capturar(autenticacion);
        campo("kmSemana").setValor(new BigDecimal("200")); completadas.clear();
        assertEquals(new BigDecimal("100"),c.contexto().contexto().transporte().kmSemana());
        assertEquals(1,c.realizadas().size());
        assertThrows(UnsupportedOperationException.class, () -> c.realizadas().clear());
        assertThrows(UnsupportedOperationException.class, () -> c.contexto().categoriasIncompletas().add("Otra"));
        assertThrows(UnsupportedOperationException.class, () -> c.recomendacionesLegadasRealizadas().add(9));
    }
    @Test void ordenEscalasYNombresNormalizadosNoCambianHuella() {
        completadas.add(asociacion(1,"completado","RE_INICIAR_SEPARACION","1"));
        completadas.add(asociacion(2,"completado","TR_PLANIFICAR_DESPLAZAMIENTOS","1"));
        var c=servicio.capturar(autenticacion);
        Collections.reverse(datos); Collections.reverse(completadas);
        campo("kmSemana").setValor(new BigDecimal("100.00")); campo("medio").setUnidad("  AUTO  ");
        datos.forEach(h -> { if(h.getCategoria().getNombre().equals("Energía")) h.getCategoria().setNombre("Energia"); });
        assertEquals(c.huellaVigencia(),servicio.capturar(autenticacion).huellaVigencia());
    }
    @ParameterizedTest @ValueSource(strings={"medio","kmSemana","consumoKwhDiario","tipo","reciclas","categoriaCompleta"})
    void cambiosMaterialesSeDetectanSinModificarOriginal(String campo) {
        var c=servicio.capturar(autenticacion); String original=mapper.writeValueAsString(c);
        switch(campo) {
            case "medio" -> campo("medio").setUnidad("Bus");
            case "kmSemana" -> campo("kmSemana").setValor(new BigDecimal("101"));
            case "consumoKwhDiario" -> campo("consumoKwhDiario").setValor(new BigDecimal("20"));
            case "tipo" -> campo("tipo").setUnidad("Vegana");
            case "reciclas" -> campo("reciclas").setValor(BigDecimal.ONE);
            default -> datos.remove(campo("personas"));
        }
        assertEquals(CAMBIO_DETECTADO,servicio.revalidar(autenticacion,c).estado());
        assertEquals(original,mapper.writeValueAsString(c));
    }
    @Test void completadaDuranteEsperaInvalidaPeroPendienteNormalNo() {
        var c=servicio.capturar(autenticacion);
        var a=asociacion(1,"pendiente","TR_PLANIFICAR_DESPLAZAMIENTOS","1"); pendientes.add(a);
        assertEquals(VIGENTE,servicio.revalidar(autenticacion,c).estado());
        a.setEstado("completado"); pendientes.clear(); completadas.add(a);
        assertEquals(CAMBIO_DETECTADO,servicio.revalidar(autenticacion,c).estado());
        assertEquals(Set.of(new AccionAmbientalAdmisible.Referencia("TR_PLANIFICAR_DESPLAZAMIENTOS","1")),servicio.capturar(autenticacion).realizadas());
    }
    @Test void fechaCompletadoEnPendienteTambienExcluyeSegunLogicaExistente() {
        var a=asociacion(1,"pendiente","TR_PLANIFICAR_DESPLAZAMIENTOS","1"); a.setFechaCompletado(LocalDateTime.now()); pendientes.add(a);
        assertEquals(1,servicio.capturar(autenticacion).realizadas().size());
    }
    @Test void retoSoloExcluyeAlCompletarseYConOrigenPropio() {
        var c=servicio.capturar(autenticacion); var a=asociacion(1,"pendiente","TR_PLANIFICAR_DESPLAZAMIENTOS","1");
        var r=new Reto(); r.setRecomendacionOrigen(a);
        var p=new UsuarioReto(); p.setId(5); p.setUsuario(usuario); p.setReto(r); p.setEstado("activo"); completados.add(p);
        assertEquals(VIGENTE,servicio.revalidar(autenticacion,c).estado());
        p.setEstado("completado"); assertEquals(CAMBIO_DETECTADO,servicio.revalidar(autenticacion,c).estado());
        assertEquals(1,servicio.capturar(autenticacion).realizadas().size());
    }
    @Test void legadoSinIdentidadNoSeDeducePorTituloYSeHaceVisible() {
        var c=servicio.capturar(autenticacion); completadas.add(asociacion(7,"completado",null,null));
        var a=servicio.capturar(autenticacion);
        assertTrue(a.realizadas().isEmpty()); assertEquals(Set.of(7),a.recomendacionesLegadasRealizadas());
        assertEquals(CAMBIO_DETECTADO,servicio.revalidar(autenticacion,c).estado());
        var origen=asociacion(8,"pendiente","TR_PLANIFICAR_DESPLAZAMIENTOS",null);
        var reto=new Reto(); reto.setRecomendacionOrigen(origen);
        var p=new UsuarioReto(); p.setId(9); p.setUsuario(usuario); p.setReto(reto); p.setEstado("completado"); completados.add(p);
        assertEquals(Set.of(9),servicio.capturar(autenticacion).retosLegadosRealizados());
        assertTrue(servicio.capturar(autenticacion).realizadas().isEmpty());
    }
    @Test void duplicadosDeLaMismaAccionNoCambianElegibilidad() {
        completadas.add(asociacion(1,"completado","TR_PLANIFICAR_DESPLAZAMIENTOS","1"));
        var c=servicio.capturar(autenticacion);
        completadas.add(asociacion(2,"completado","TR_PLANIFICAR_DESPLAZAMIENTOS","1"));
        assertEquals(VIGENTE,servicio.revalidar(autenticacion,c).estado());
    }
    @Test void aislamientoDefensivoYRevalidacionNoPermiteCambiarDeUsuario() {
        var c=servicio.capturar(autenticacion); var otro=new Usuario(); otro.setId(69L);
        var ajena=asociacion(1,"completado","TR_PLANIFICAR_DESPLAZAMIENTOS","1"); ajena.setUsuario(otro); completadas.add(ajena);
        assertEquals(VIGENTE,servicio.revalidar(autenticacion,c).estado());
        var auth=new UsernamePasswordAuthenticationToken(new UserSecurity(otro),null,List.of());
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,() -> servicio.revalidar(auth,c)).getStatusCode());
        when(usuarios.existsById(69L)).thenReturn(true);
        var capturaOtro=servicio.capturar(auth);
        assertEquals(4,capturaOtro.contexto().categoriasIncompletas().size()); assertTrue(capturaOtro.realizadas().isEmpty());
    }
    @Test void identidadNoConfiableYUsuarioInexistenteSonErroresControlados() {
        clearInvocations(usuarios,habitos,recomendaciones,retos);
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ResponseStatusException.class,() -> servicio.capturar(null)).getStatusCode());
        var noAuth=new UsernamePasswordAuthenticationToken(new UserSecurity(usuario),null);
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ResponseStatusException.class,() -> servicio.capturar(noAuth)).getStatusCode());
        var arbitrario=new UsernamePasswordAuthenticationToken(68L,null,List.of());
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ResponseStatusException.class,() -> servicio.capturar(arbitrario)).getStatusCode());
        verifyNoInteractions(usuarios,habitos,recomendaciones,retos);
        when(usuarios.existsById(68L)).thenReturn(false);
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,() -> servicio.capturar(autenticacion)).getStatusCode());
        verifyNoInteractions(habitos,recomendaciones,retos);
    }
    @ParameterizedTest @ValueSource(strings={"usuarios","habitos","completadas","pendientes","retos"})
    void errorDeLecturaNoSeConvierteEnCambioLegitimo(String origen) {
        var c=servicio.capturar(autenticacion); var error=new DataAccessResourceFailureException("detalle privado");
        switch(origen) {
            case "usuarios" -> when(usuarios.existsById(68L)).thenThrow(error);
            case "habitos" -> when(habitos.findByUsuario_Id(68L)).thenThrow(error);
            case "completadas" -> when(recomendaciones.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(68L,"completado")).thenThrow(error);
            case "pendientes" -> when(recomendaciones.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(68L,"pendiente")).thenThrow(error);
            default -> when(retos.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaAceptacionDesc(68L,"completado")).thenThrow(error);
        }
        var e=assertThrows(ResponseStatusException.class,() -> servicio.revalidar(autenticacion,c));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,e.getStatusCode()); assertFalse(e.getReason().contains("privado"));
    }
    @Test void capturaManipuladaNoSeActualizaSilenciosamente() {
        var c=servicio.capturar(autenticacion);
        var alterada=new CapturaContextoAmbientalIADTO(c.versionHuella(),c.usuarioId(),c.contexto(),c.realizadas(),
                c.recomendacionesLegadasRealizadas(),c.retosLegadosRealizados(),"0".repeat(64));
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,() -> servicio.revalidar(autenticacion,alterada)).getStatusCode());
    }
    @Test void interceptorSolicitaDosTransaccionesIndependientesRepetiblesSoloLectura() {
        PlatformTransactionManager tx=mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenAnswer(i -> {
            TransactionDefinition d=i.getArgument(0); assertTrue(d.isReadOnly());
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW,d.getPropagationBehavior());
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,d.getIsolationLevel()); return new SimpleTransactionStatus();
        });
        ProxyFactory proxy=new ProxyFactory(servicio);
        proxy.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));
        var s=(CapturaContextoAmbientalIAService)proxy.getProxy();
        var c=s.capturar(autenticacion); assertEquals(VIGENTE,s.revalidar(autenticacion,c).estado());
        verify(tx,times(2)).getTransaction(any()); verify(tx,times(2)).commit(any());
        verifyNoWrites();
    }
    void verifyNoWrites() {
        verify(usuarios,never()).save(any()); verify(usuarios,never()).bloquearParaRecomendaciones(any());
        verify(habitos,never()).save(any()); verify(habitos,never()).saveAll(any());
        verify(recomendaciones,never()).saveAndFlush(any()); verify(recomendaciones,never()).save(any());
        verify(retos,never()).saveAndFlush(any()); verify(retos,never()).save(any());
    }

    CapturaContextoAmbientalIAService proxyMandatory(TransaccionesAisladasIA tx) {
        ProxyFactory proxy=new ProxyFactory(servicio);
        proxy.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));
        return (CapturaContextoAmbientalIAService)proxy.getProxy();
    }
    @Test void mandatorySinTransaccionEsRechazadoPorProxySpring() {
        var c=servicio.capturar(autenticacion); clearInvocations(usuarios,habitos,recomendaciones,retos);
        var tx=new TransaccionesAisladasIA();
        assertThrows(IllegalTransactionStateException.class,()->proxyMandatory(tx).comprobarVigenciaEnTransaccion(autenticacion,c));
        assertEquals(0,tx.comienzos); verifyNoInteractions(usuarios,habitos,recomendaciones,retos);
    }
    @ParameterizedTest @ValueSource(strings={"vigente","habito","accion","legado","identidad","propietario","error"})
    void mandatoryParticipaEnTransaccionExistenteSinAbrirOtra(String caso) {
        var c=servicio.capturar(autenticacion); var tx=new TransaccionesAisladasIA(); var proxy=proxyMandatory(tx);
        var template=new org.springframework.transaction.support.TransactionTemplate(tx);
        template.executeWithoutResult(status -> {
            assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            switch(caso) {
                case "habito" -> campo("kmSemana").setValor(new BigDecimal("101"));
                case "accion" -> completadas.add(asociacion(1,"completado","TR_PLANIFICAR_DESPLAZAMIENTOS","1"));
                case "legado" -> completadas.add(asociacion(8,"completado",null,null));
                case "error" -> when(usuarios.existsById(68L)).thenThrow(new DataAccessResourceFailureException("privado"));
            }
            if(caso.equals("identidad")) assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ResponseStatusException.class,
                    ()->proxy.comprobarVigenciaEnTransaccion(null,c)).getStatusCode());
            else if(caso.equals("propietario")) {
                var otro=new Usuario(); otro.setId(69L);
                var auth=new UsernamePasswordAuthenticationToken(new UserSecurity(otro),null,List.of());
                assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,
                        ()->proxy.comprobarVigenciaEnTransaccion(auth,c)).getStatusCode());
            } else if(caso.equals("error")) assertEquals(HttpStatus.SERVICE_UNAVAILABLE,assertThrows(ResponseStatusException.class,
                    ()->proxy.comprobarVigenciaEnTransaccion(autenticacion,c)).getStatusCode());
            else {
                assertEquals(caso.equals("vigente")?VIGENTE:CAMBIO_DETECTADO,
                        proxy.comprobarVigenciaEnTransaccion(autenticacion,c).estado());
                assertEquals(servicio.revalidar(autenticacion,c),proxy.comprobarVigenciaEnTransaccion(autenticacion,c));
            }
            assertEquals(1,tx.comienzos);
        });
        verifyNoWrites();
    }
    @Test void mandatoryNoDeclaraLecturaIndependienteNiAislamientoNuevo() throws Exception {
        var a=CapturaContextoAmbientalIAServiceImpl.class.getMethod("comprobarVigenciaEnTransaccion",Authentication.class,CapturaContextoAmbientalIADTO.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertEquals(org.springframework.transaction.annotation.Propagation.MANDATORY,a.propagation());
        assertEquals(org.springframework.transaction.annotation.Isolation.DEFAULT,a.isolation()); assertFalse(a.readOnly());
    }
}
