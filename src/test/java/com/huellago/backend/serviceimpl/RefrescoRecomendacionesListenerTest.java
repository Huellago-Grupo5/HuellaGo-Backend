package com.huellago.backend.serviceimpl;

import com.huellago.backend.events.InformacionAmbientalActualizada;
import com.huellago.backend.repositories.UsuarioRepository;
import com.huellago.backend.services.RecomendacionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefrescoRecomendacionesListenerTest {
    @Configuration
    @EnableTransactionManagement
    static class Configuracion {
        @Bean RecomendacionService recomendaciones() { return mock(RecomendacionService.class); }
        @Bean UsuarioRepository usuarios() { return mock(UsuarioRepository.class); }
        @Bean EntityManager em() { return mock(EntityManager.class); }
        @Bean Transacciones transactionManager() { return new Transacciones(); }
        @Bean RefrescoRecomendacionesListener listener(RecomendacionService servicio, UsuarioRepository usuarios,
                                                       EntityManager em) {
            return new RefrescoRecomendacionesListener(servicio, usuarios, em);
        }
    }

    // Gestor sin recursos externos: prueba las sincronizaciones y el rollback reales de Spring.
    static class Transacciones extends AbstractPlatformTransactionManager {
        final AtomicInteger commits = new AtomicInteger();
        final AtomicInteger rollbacks = new AtomicInteger();
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits.incrementAndGet(); }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks.incrementAndGet(); }
    }

    @Test
    void eventoAntesDelCommitCargaDatosNuevosYBloqueaTodosLosUsuariosAntesDeEvaluar() {
        try (var contexto = new AnnotationConfigApplicationContext(Configuracion.class)) {
            var repositorio = contexto.getBean(UsuarioRepository.class);
            var servicio = contexto.getBean(RecomendacionService.class);
            var em = contexto.getBean(EntityManager.class);
            var tx = contexto.getBean(Transacciones.class);
            when(repositorio.bloquearParaRecomendaciones(anyLong())).thenAnswer(i -> Optional.of(i.getArgument(0)));
            new TransactionTemplate(tx).executeWithoutResult(status -> {
                contexto.publishEvent(new InformacionAmbientalActualizada(List.of(33L, 22L, 33L)));
                verifyNoInteractions(servicio);
            });
            var orden = inOrder(em, repositorio, servicio);
            orden.verify(em).flush();
            orden.verify(em).clear();
            orden.verify(repositorio).bloquearParaRecomendaciones(22L);
            orden.verify(repositorio).bloquearParaRecomendaciones(33L);
            orden.verify(servicio).generar(argThat(u -> u.getId().equals(22L)));
            orden.verify(servicio).generar(argThat(u -> u.getId().equals(33L)));
            assertEquals(1, tx.commits.get());
            assertEquals(0, tx.rollbacks.get());
        }
    }

    @Test
    void falloDeRefrescoRevierteLaTransaccionOriginal() {
        try (var contexto = new AnnotationConfigApplicationContext(Configuracion.class)) {
            var repositorio = contexto.getBean(UsuarioRepository.class);
            var servicio = contexto.getBean(RecomendacionService.class);
            var tx = contexto.getBean(Transacciones.class);
            when(repositorio.bloquearParaRecomendaciones(22L)).thenReturn(Optional.of(22L));
            when(servicio.generar(any())).thenThrow(new DataAccessResourceFailureException("fallo simulado"));
            assertThrows(DataAccessResourceFailureException.class, () ->
                    new TransactionTemplate(tx).executeWithoutResult(status ->
                            contexto.publishEvent(new InformacionAmbientalActualizada(22L))));
            assertEquals(0, tx.commits.get());
            assertEquals(1, tx.rollbacks.get());
        }
    }

    @Test
    void rollbackPrevioYEventosSinTransaccionNoRefrescan() {
        try (var contexto = new AnnotationConfigApplicationContext(Configuracion.class)) {
            var servicio = contexto.getBean(RecomendacionService.class);
            contexto.publishEvent(new InformacionAmbientalActualizada(22L));
            new TransactionTemplate(contexto.getBean(Transacciones.class)).executeWithoutResult(status -> {
                contexto.publishEvent(new InformacionAmbientalActualizada(22L));
                status.setRollbackOnly();
            });
            verifyNoInteractions(servicio, contexto.getBean(EntityManager.class));
        }
    }

    @Test
    void refrescosSimultaneosRespetanBloqueoPorUsuarioHastaFinDeTransaccion() throws Exception {
        try (var contexto = new AnnotationConfigApplicationContext(Configuracion.class)) {
            var repositorio = contexto.getBean(UsuarioRepository.class);
            var servicio = contexto.getBean(RecomendacionService.class);
            var tx = contexto.getBean(Transacciones.class);
            ReentrantLock bloqueo = new ReentrantLock();
            AtomicInteger simultaneas = new AtomicInteger();
            AtomicInteger maximo = new AtomicInteger();
            when(repositorio.bloquearParaRecomendaciones(22L)).thenAnswer(i -> {
                bloqueo.lock();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int estado) { bloqueo.unlock(); }
                });
                return Optional.of(22L);
            });
            when(servicio.generar(any())).thenAnswer(i -> {
                maximo.accumulateAndGet(simultaneas.incrementAndGet(), Math::max);
                simultaneas.decrementAndGet();
                return null;
            });
            CountDownLatch inicio = new CountDownLatch(1);
            try (ExecutorService ejecutor = Executors.newFixedThreadPool(2)) {
                Callable<Void> tarea = () -> {
                    inicio.await();
                    new TransactionTemplate(tx).executeWithoutResult(status ->
                            contexto.publishEvent(new InformacionAmbientalActualizada(22L)));
                    return null;
                };
                Future<Void> primera = ejecutor.submit(tarea);
                Future<Void> segunda = ejecutor.submit(tarea);
                inicio.countDown();
                primera.get(10, TimeUnit.SECONDS);
                segunda.get(10, TimeUnit.SECONDS);
            }
            assertEquals(1, maximo.get());
            assertEquals(2, tx.commits.get());
            verify(servicio, times(2)).generar(argThat(u -> u.getId().equals(22L)));
        }
    }
}
