package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.services.ObjetivoAmbientalService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Recurso simulado: comprueba el proxy transaccional; no certifica PostgreSQL. */
class ObjetivosTransaccionSimuladaTest {
    final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    final ObjetivoAmbientalRepository objetivos = mock(ObjetivoAmbientalRepository.class);
    final UsuarioObjetivoAmbientalRepository selecciones = mock(UsuarioObjetivoAmbientalRepository.class);
    final Map<Long, List<UsuarioObjetivoAmbiental>> filas = new ConcurrentHashMap<>();
    final Map<Long, ReentrantLock> bloqueos = new ConcurrentHashMap<>();
    final ObjetivoAmbientalService servicio;
    volatile boolean fallar;
    volatile CountDownLatch esperando;

    ObjetivosTransaccionSimuladaTest() {
        when(usuarios.findByCorreo(anyString())).thenAnswer(i -> {
            Usuario u = new Usuario(); u.setId(Long.parseLong(i.<String>getArgument(0))); return u;
        });
        when(objetivos.findAllById(anyList())).thenAnswer(i -> i.<List<Long>>getArgument(0).stream()
                .filter(id -> id < 10).map(id -> new ObjetivoAmbiental(id, "Objetivo", "Descripcion")).toList());
        when(usuarios.bloquearParaRecomendaciones(anyLong())).thenAnswer(i -> {
            long id = i.getArgument(0); var lock = bloqueos.computeIfAbsent(id, k -> new ReentrantLock());
            if (esperando != null && lock.isLocked() && !lock.isHeldByCurrentThread()) esperando.countDown();
            lock.lock(); var copia = new ArrayList<>(filas.getOrDefault(id, List.of()));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                public void afterCompletion(int estado) {
                    if (estado == STATUS_ROLLED_BACK) filas.put(id, copia);
                    lock.unlock();
                }
            });
            return Optional.of(id);
        });
        when(selecciones.findByUsuarioId(anyLong())).thenAnswer(i ->
                new ArrayList<>(filas.getOrDefault(i.getArgument(0), List.of())));
        doAnswer(i -> {
            for (var fila : i.<List<UsuarioObjetivoAmbiental>>getArgument(0)) filas.get(fila.getUsuario().getId()).remove(fila);
            return null;
        }).when(selecciones).deleteAll(anyList());
        when(selecciones.saveAll(anyList())).thenAnswer(i -> {
            var nuevas = i.<List<UsuarioObjetivoAmbiental>>getArgument(0);
            for (var fila : nuevas) filas.computeIfAbsent(fila.getUsuario().getId(), k -> new ArrayList<>()).add(fila);
            if (fallar) throw new IllegalStateException("Fallo de persistencia simulado");
            return nuevas;
        });
        var gestor = new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction() { return new Object(); }
            protected void doBegin(Object t, TransactionDefinition d) { }
            protected void doCommit(DefaultTransactionStatus s) { }
            protected void doRollback(DefaultTransactionStatus s) { }
        };
        var proxy = new ProxyFactory(new ObjetivoAmbientalServiceImpl(objetivos, selecciones, usuarios));
        proxy.addAdvice(new TransactionInterceptor(gestor, new AnnotationTransactionAttributeSource()));
        servicio = (ObjetivoAmbientalService) proxy.getProxy();
    }
    void guardar(String usuario, Long... ids) {
        var dto = new SeleccionObjetivosAmbientalesDTO(); dto.setObjetivoIds(Arrays.asList(ids));
        servicio.actualizarSeleccion(usuario, dto);
    }
    List<Long> ids(long usuario) {
        return filas.getOrDefault(usuario, List.of()).stream().map(a -> a.getObjetivo().getId()).sorted().toList();
    }
    @Test void rollbackTrasEliminarEInsertarConFallo() {
        guardar("22", 1L, 2L); fallar = true;
        assertThrows(IllegalStateException.class, () -> guardar("22", 2L, 3L));
        assertEquals(List.of(1L, 2L), ids(22)); assertFalse(bloqueos.get(22L).isLocked());
    }
    @Test void duplicadosSonIdempotentesYUsuariosAislados() {
        guardar("22", 1L, 1L, 2L); guardar("99", 3L);
        assertEquals(List.of(1L, 2L), ids(22)); assertEquals(List.of(3L), ids(99));
        var compartida = filas.get(22L).get(1); guardar("22", 2L, 4L);
        assertSame(compartida, filas.get(22L).getFirst());
    }
    @Test void entradasInvalidasNoModificanSeleccion() {
        guardar("22", 1L);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> guardar("22"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> guardar("22", 99L));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> guardar("22", (Long) null));
        assertEquals(List.of(1L), ids(22));
    }
    @Test void actualizacionesConcurrentesNoMezclanResultados() throws Exception {
        guardar("22", 1L);
        CountDownLatch primeraLeyendo = new CountDownLatch(1), liberar = new CountDownLatch(1);
        var contador = new java.util.concurrent.atomic.AtomicInteger();
        when(selecciones.findByUsuarioId(22L)).thenAnswer(i -> {
            if (contador.incrementAndGet() == 1) {
                primeraLeyendo.countDown(); assertTrue(liberar.await(5, TimeUnit.SECONDS));
            }
            return new ArrayList<>(filas.get(22L));
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(() -> guardar("22", 2L, 3L));
            assertTrue(primeraLeyendo.await(5, TimeUnit.SECONDS));
            esperando = new CountDownLatch(1);
            Future<?> b = pool.submit(() -> guardar("22", 4L, 5L));
            assertTrue(esperando.await(5, TimeUnit.SECONDS));
            assertEquals(1, contador.get());
            liberar.countDown(); a.get(5, TimeUnit.SECONDS); b.get(5, TimeUnit.SECONDS);
            assertEquals(List.of(4L, 5L), ids(22)); assertEquals(2, contador.get());
        } finally { liberar.countDown(); pool.shutdownNow(); }
    }
}
