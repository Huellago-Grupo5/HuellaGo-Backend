package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.services.RecomendacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecomendacionCompletadaServiceImplTest {
    @Mock UsuarioRecomendacionIARepository asociaciones;
    @Mock RetoRepository retos;
    @Mock UsuarioRetoRepository participaciones;
    @Mock UsuarioRepository usuarios;
    @Mock PlatformTransactionManager transacciones;
    @InjectMocks RecomendacionServiceImpl servicio;
    Usuario usuario;
    UsuarioRecomendacionIA asociacion;

    @BeforeEach
    void datos() {
        usuario = new Usuario();
        usuario.setId(77L);
        usuario.setEcoPuntos(30);
        usuario.setNivel(1);
        RecomendacionIA recomendacion = new RecomendacionIA();
        recomendacion.setId(8);
        recomendacion.setTitulo("Separar residuos");
        asociacion = new UsuarioRecomendacionIA();
        asociacion.setId(3);
        asociacion.setUsuario(usuario);
        asociacion.setRecomendacionIA(recomendacion);
        asociacion.setEstado("Pendiente");
        asociacion.setFechaGeneracion(LocalDateTime.of(2026, 1, 1, 10, 0));
    }

    private void propia() {
        when(asociaciones.bloquearPropia(3, 77L)).thenReturn(Optional.of(3));
        when(asociaciones.findByIdAndUsuario_Id(3, 77L)).thenReturn(Optional.of(asociacion));
    }

    @Test
    void completaYGuardaSinModificarRetosPuntosONivel() {
        propia();
        when(asociaciones.saveAndFlush(asociacion)).thenReturn(asociacion);
        var dto = servicio.completar(3, usuario);
        assertEquals("completado", dto.estado());
        assertNotNull(dto.fechaCompletado());
        assertEquals(asociacion.getFechaGeneracion(), dto.fechaGeneracion());
        assertEquals(8, dto.recomendacionId());
        InOrder orden = inOrder(asociaciones);
        orden.verify(asociaciones).bloquearPropia(3, 77L);
        orden.verify(asociaciones).findByIdAndUsuario_Id(3, 77L);
        orden.verify(asociaciones).saveAndFlush(asociacion);
        assertEquals(30, usuario.getEcoPuntos());
        assertEquals(1, usuario.getNivel());
        verifyNoInteractions(retos, participaciones, usuarios);
    }

    @Test
    void segundoIntentoTrasBloqueoConservaFechaOriginal() {
        propia();
        when(asociaciones.saveAndFlush(asociacion)).thenReturn(asociacion);
        var primera = servicio.completar(3, usuario);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> servicio.completar(3, usuario)).getStatusCode());
        assertEquals(primera.fechaCompletado(), asociacion.getFechaCompletado());
        verify(asociaciones, times(1)).saveAndFlush(any());
    }

    @Test
    void ajenaEInexistenteSon404SinLecturasPrivadasNiEscrituras() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> servicio.completar(3, usuario)).getStatusCode());
        verify(asociaciones).bloquearPropia(3, 77L);
        verifyNoMoreInteractions(asociaciones);
    }

    @Test
    void rechazaEstadoNoCompatibleSinGuardar() {
        propia();
        asociacion.setEstado("cancelado");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> servicio.completar(3, usuario)).getStatusCode());
        assertNull(asociacion.getFechaCompletado());
        verify(asociaciones, never()).saveAndFlush(any());
    }

    @Test
    void historialUsaPropietarioYEstadoSinEscrituras() {
        asociacion.setEstado("completado");
        asociacion.setFechaCompletado(LocalDateTime.now());
        when(asociaciones.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(
                77L, "completado")).thenReturn(List.of(asociacion));
        var historial = servicio.historialCompletadas(usuario);
        assertEquals(1, historial.size());
        assertEquals(3, historial.getFirst().usuarioRecomendacionId());
        assertEquals(asociacion.getFechaCompletado(), historial.getFirst().fechaCompletado());
        verify(asociaciones, never()).saveAndFlush(any());
    }

    @Test
    void historialVacioEsListaVacia() {
        assertTrue(servicio.historialCompletadas(usuario).isEmpty());
    }

    @Test
    void validaIdentidadEIdentificadorAntesDeConsultar() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> servicio.completar(3, null)).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> servicio.historialCompletadas(null)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> servicio.completar(0, usuario)).getStatusCode());
        verifyNoInteractions(asociaciones);
    }

    @Test
    void interceptorTransaccionalSolicitaRollbackAlFallarPersistencia() {
        propia();
        SimpleTransactionStatus estado = new SimpleTransactionStatus();
        when(transacciones.getTransaction(any())).thenReturn(estado);
        when(asociaciones.saveAndFlush(asociacion))
                .thenThrow(new DataIntegrityViolationException("detalle privado"));
        ProxyFactory proxy = new ProxyFactory(servicio);
        proxy.addAdvice(new TransactionInterceptor(transacciones, new AnnotationTransactionAttributeSource()));
        RecomendacionService transaccional = (RecomendacionService) proxy.getProxy();
        assertThrows(DataIntegrityViolationException.class, () -> transaccional.completar(3, usuario));
        verify(transacciones).rollback(estado);
        verify(transacciones, never()).commit(any());
        verifyNoInteractions(retos, participaciones, usuarios);
    }
}
