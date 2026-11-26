package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.services.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;

/** SINTETICO: servicios reales y proxies con gestor sin recursos; ninguna conexion. */
final class FixtureDemoIA127 {
    final UsuarioRepository usuarios=mock(UsuarioRepository.class);
    final HabitoRepository habitos=mock(HabitoRepository.class);
    final UsuarioRecomendacionIARepository asociaciones=mock(UsuarioRecomendacionIARepository.class);
    final UsuarioRetoRepository retos=mock(UsuarioRetoRepository.class);
    final RecomendacionIARepository acciones=mock(RecomendacionIARepository.class);
    final CategoriaHabitoRepository categorias=mock(CategoriaHabitoRepository.class);
    final GeneradorRecomendacionesIAService selector=mock(GeneradorRecomendacionesIAService.class);
    final jakarta.persistence.EntityManager em=mock(jakarta.persistence.EntityManager.class);
    final TransaccionesAisladasIA tm=new TransaccionesAisladasIA();
    final Usuario usuario=new Usuario(); final CategoriaHabito categoria=new CategoriaHabito();
    final List<Habito> datos=new ArrayList<>(); final List<UsuarioRecomendacionIA> guardadas=new ArrayList<>();
    final List<RecomendacionIA> canonicas=new ArrayList<>();
    final List<PoliticaAutorizacionEditorialIAService.Autorizacion> permisos=new ArrayList<>(new RegistroEditorialPruebaInternaFixture().consultar());
    final CapturaContextoAmbientalIAService captura;
    final GeneracionManualIAService generacion;
    final PersistenciaRecomendacionesIAService escritor;
    final EntradaDemoIA127 entrada;
    String enfoque; boolean abstener; String accion=ProteccionDemoIA127.ACCION;
    Runnable duranteInferencia=()->{};
    FixtureDemoIA127() {
        usuario.setId(91L); usuario.setCorreo(EntornoHttpIA123.CORREO); usuario.setEcoPuntos(0);
        categoria.setId(2L); categoria.setNombre("Transporte");
        String[] nombres={"medio","kmSemana","diasSemana"},valores={"0","100","5"},unidades={"Auto","km/semana","dias/semana"};
        for(int i=0;i<3;i++) { var h=new Habito(); h.setId(10L+i); h.setUsuario(usuario); h.setCategoria(categoria);
            h.setNombre(nombres[i]); h.setValor(new BigDecimal(valores[i])); h.setUnidad(unidades[i]); datos.add(h); }
        when(usuarios.existsById(91L)).thenReturn(true); when(usuarios.findById(91L)).thenReturn(Optional.of(usuario));
        when(usuarios.findByCorreoNormalizado(EntornoHttpIA123.CORREO)).thenReturn(usuario);
        when(usuarios.bloquearParaRecomendaciones(91L)).thenAnswer(i->{ assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); return Optional.of(91L); });
        when(habitos.findByUsuario_Id(91L)).thenAnswer(i->List.copyOf(datos));
        when(asociaciones.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(eq(91L),anyString()))
                .thenAnswer(i->guardadas.stream().filter(g->g.getEstado().equalsIgnoreCase(i.getArgument(1))).toList());
        when(categorias.findByNombreIgnoreCase("Transporte")).thenReturn(categoria); when(categorias.bloquearParaRecomendaciones(2L)).thenReturn(Optional.of(2L));
        when(acciones.findByAccionIdAndVersionAccionOrderByIdAsc(anyString(),anyString())).thenAnswer(i->List.copyOf(canonicas));
        when(acciones.saveAndFlush(any())).thenAnswer(i->{ RecomendacionIA a=i.getArgument(0); a.setId(500); canonicas.add(a); return a; });
        when(asociaciones.saveAndFlush(any())).thenAnswer(i->{ UsuarioRecomendacionIA a=i.getArgument(0); a.setId(600+guardadas.size()); guardadas.add(a); return a; });
        when(asociaciones.findByUsuario_IdAndReferenciaContextoAndMetodoGeneracion(eq(91L),anyString(),anyString()))
                .thenAnswer(i->guardadas.stream().filter(a->a.getReferenciaContexto().equals(i.getArgument(1))).toList());
        when(asociaciones.findByIdAndUsuario_Id(anyInt(),eq(91L))).thenAnswer(i->guardadas.stream().filter(a->a.getId().equals(i.getArgument(0))).findFirst());
        var catalogo=new CatalogoAccionesAmbientalesServiceImpl(); var presentador=new PresentacionRecomendacionesIAServiceImpl();
        var politica=ProteccionDemoIA127.editorial(new PoliticaAutorizacionEditorialIAServiceImpl(catalogo,presentador,()->List.copyOf(permisos)));
        captura=ProteccionDemoIA127.captura(proxy(new CapturaContextoAmbientalIAServiceImpl(usuarios,asociaciones,retos,new ContextoRecomendacionesIAServiceImpl(habitos)),CapturaContextoAmbientalIAService.class));
        escritor=proxy(new PersistenciaRecomendacionesIAServiceImpl(usuarios,acciones,asociaciones,categorias,captura,em,politica,true),PersistenciaRecomendacionesIAService.class);
        generacion=new GeneracionManualIAServiceImpl(captura,politica,new OrquestadorRecomendacionesIAServiceImpl(selector,catalogo,presentador),escritor);
        entrada=new EntradaDemoIA127(generacion,asociaciones,tm);
        when(selector.generarV2ConAlcanceInterno(any(),anyList(),anySet())).thenAnswer(i->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); duranteInferencia.run();
            var propuesta=new Propuesta("Transporte",abstener?Decision.ABSTENERSE:Decision.PROPONER,
                    abstener?null:new Accion(accion,"1",enfoque==null?Map.of():Map.of("enfoque",new Texto(enfoque))),
                    abstener?List.of():List.of(new Evidencia("/transporte/medio",new Texto("Auto"),Ambito.NO_APLICA),
                            new Evidencia("/transporte/kmSemana",new Numero(new BigDecimal("100")),Ambito.NO_APLICA),
                            new Evidencia("/transporte/diasSemana",new Numero(new BigDecimal("5")),Ambito.NO_APLICA)),
                    null,null,null,List.of(),abstener?MotivoAbstencion.NO_VERIFICABLE:null);
            var dto=new RecomendacionFundamentadaIADTO("2",List.of(propuesta)); var mapper=JsonMapper.builder().build();
            return new ResultadoGeneracionFundamentadaIADTO(mapper.writeValueAsString(dto),dto,new ResultadoEvaluacionRecomendacionIADTO(ResultadoEvaluacionRecomendacionIADTO.Estado.ACEPTADA_ESTRUCTURALMENTE,List.of(),List.of()));
        });
    }
    private <T>T proxy(Object target,Class<T> tipo) {
        var p=new ProxyFactory(target); p.addAdvice(new TransactionInterceptor(tm,new AnnotationTransactionAttributeSource())); return tipo.cast(p.getProxy());
    }
}
