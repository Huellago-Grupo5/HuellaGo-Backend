package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.AutorizacionInstitucionalService;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampanaInstitucionalServiceImplTest {
    @Mock InstitucionRepository instituciones;
    @Mock RetoRepository retos;
    @Mock AutorizacionInstitucionalService autorizacion;
    @Mock RetoColectivoService colectivos;
    @InjectMocks CampanaInstitucionalServiceImpl servicio;
    Usuario usuario;
    Institucion institucion;

    @BeforeEach
    void datos() {
        usuario = new Usuario();
        usuario.setId(77L);
        usuario.setEcoPuntos(30);
        usuario.setNivel(1);
        institucion = new Institucion();
        ReflectionTestUtils.setField(institucion, "id", 5L);
        lenient().when(instituciones.bloquearCampanas(5L)).thenReturn(Optional.of(5L));
        lenient().when(instituciones.findById(5L)).thenReturn(Optional.of(institucion));
        lenient().when(instituciones.existsById(5L)).thenReturn(true);
        lenient().when(retos.saveAndFlush(any())).thenAnswer(i -> {
            Reto reto = i.getArgument(0);
            reto.setId(50);
            return reto;
        });
    }

    private CampanaCrearDTO datosValidos() {
        return new CampanaCrearDTO(" Movilidad limpia ", " Compartir movilidad sostenible ",
                LocalDate.now(), LocalDate.now().plusDays(6));
    }

    @Test
    void creaPublicaYPersisteEnRetosConInstitucionSinInscribirNiRecompensar() {
        var dto = servicio.crear(5L, usuario, datosValidos());
        assertEquals(50, dto.id());
        assertEquals(5L, dto.institucionId());
        assertEquals("Movilidad limpia", dto.nombre());
        assertEquals("Compartir movilidad sostenible", dto.objetivo());
        assertEquals(7, dto.duracionDias());
        assertTrue(dto.publicada());
        assertEquals("ACTIVA", dto.estado());
        verify(retos).saveAndFlush(argThat(r -> r.getInstitucion() == institucion
                && r.getTipo() == TipoReto.COLECTIVO && r.getPuntosRecompensa() == 0
                && r.getRecomendacionOrigen() == null && Boolean.TRUE.equals(r.getActivo())));
        verify(autorizacion).exigirAdministrador(77L, 5L);
        verify(colectivos).obtener(50, 77L);
        verify(colectivos, never()).unirse(any(), any());
        verify(colectivos, never()).cerrarVencido(any());
        assertEquals(30, usuario.getEcoPuntos());
        assertEquals(1, usuario.getNivel());
    }

    @Test
    void nombreYObjetivoObligatoriosSinEscrituras() {
        for (var dto : List.of(
                new CampanaCrearDTO(" ", "Objetivo", LocalDate.now(), LocalDate.now()),
                new CampanaCrearDTO("Nombre", " ", LocalDate.now(), LocalDate.now()),
                new CampanaCrearDTO(null, "Objetivo", LocalDate.now(), LocalDate.now()),
                new CampanaCrearDTO("Nombre", null, LocalDate.now(), LocalDate.now()),
                new CampanaCrearDTO("x".repeat(256), "Objetivo", LocalDate.now(), LocalDate.now()))) {
            assertEquals(400, error(() -> servicio.crear(5L, usuario, dto)));
        }
        verify(retos, never()).saveAndFlush(any());
    }

    @Test
    void fechasAusentesPasadasInvertidasOFueraDeRangoSon400() {
        for (var dto : List.of(
                new CampanaCrearDTO("Nombre", "Objetivo", null, LocalDate.now()),
                new CampanaCrearDTO("Nombre", "Objetivo", LocalDate.now(), null),
                new CampanaCrearDTO("Nombre", "Objetivo", LocalDate.now().minusDays(1), LocalDate.now()),
                new CampanaCrearDTO("Nombre", "Objetivo", LocalDate.now().plusDays(2), LocalDate.now()),
                new CampanaCrearDTO("Nombre", "Objetivo", LocalDate.of(10000, 1, 1), LocalDate.of(10000, 1, 2)))) {
            assertEquals(400, error(() -> servicio.crear(5L, usuario, dto)));
        }
        verify(retos, never()).saveAndFlush(any());
    }

    @Test
    void campañaDeUnDiaEsDuracionPositiva() {
        assertEquals(1, servicio.crear(5L, usuario, new CampanaCrearDTO("Nombre", "Objetivo",
                LocalDate.now(), LocalDate.now())).duracionDias());
    }

    @Test
    void institucionInexistente404YAutorizacionAjena403() {
        assertEquals(404, error(() -> servicio.crear(999L, usuario, datosValidos())));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado"))
                .when(autorizacion).exigirAdministrador(77L, 5L);
        assertEquals(403, error(() -> servicio.crear(5L, usuario, datosValidos())));
        verify(retos, never()).saveAndFlush(any());
    }

    @Test
    void duplicadoDeNombreYPeriodoEs409() {
        when(retos.existsByInstitucion_IdAndTituloIgnoreCaseAndFechaInicioAndFechaFin(
                5L, "Movilidad limpia", datosValidos().fechaInicio(), datosValidos().fechaFin())).thenReturn(true);
        assertEquals(409, error(() -> servicio.crear(5L, usuario, datosValidos())));
        verify(retos, never()).saveAndFlush(any());
    }

    @Test
    void miembroPuedeConsultarPorInstitucionYDetalleConSeguimientoHu29() {
        Reto reto = campana();
        when(retos.findByInstitucion_Id(eq(5L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(reto)));
        when(retos.findByIdAndInstitucion_Id(50, 5L)).thenReturn(Optional.of(reto));
        var seguimiento = new RetoColectivoDTO(50, "Nombre", reto.getFechaInicio(), reto.getFechaFin(),
                2, new java.math.BigDecimal("75"), "ACTIVO", null);
        when(colectivos.obtener(50, 77L)).thenReturn(seguimiento);
        assertEquals(1, servicio.listar(5L, usuario, 0, 20).size());
        assertSame(seguimiento, servicio.detalle(5L, 50, usuario).seguimiento());
        verify(autorizacion, times(2)).exigirMiembro(77L, 5L);
        verify(retos, never()).save(any());
    }

    @Test
    void consultaAjenaYCampanaEnInstitucionIncorrectaNoRevelanDatos() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No miembro"))
                .when(autorizacion).exigirMiembro(77L, 5L);
        assertEquals(403, error(() -> servicio.listar(5L, usuario, 0, 20)));
        assertEquals(403, error(() -> servicio.detalle(5L, 50, usuario)));
        verify(retos, never()).findByIdAndInstitucion_Id(any(), any());
    }

    @Test
    void detalleNoMezclaIdsDeInstituciones() {
        assertEquals(404, error(() -> servicio.detalle(5L, 999, usuario)));
        verify(retos).findByIdAndInstitucion_Id(999, 5L);
    }

    @Test
    void misCampanasUtilizaConsultaPorVinculoAutenticado() {
        when(retos.buscarCampanasVinculadas(eq(77L), any(Pageable.class))).thenReturn(Page.empty());
        assertTrue(servicio.misCampanas(usuario, 0, 20).isEmpty());
        verify(retos).buscarCampanasVinculadas(eq(77L), any(Pageable.class));
    }

    @Test
    void distingueFuturaFinalizadaYNoDisponibleSinCerrarNiRecompensar() {
        Reto reto = campana();
        when(retos.findByIdAndInstitucion_Id(50, 5L)).thenReturn(Optional.of(reto));
        reto.setFechaInicio(LocalDate.now().plusDays(1));
        reto.setFechaFin(LocalDate.now().plusDays(2));
        assertEquals("FUTURA", servicio.detalle(5L, 50, usuario).estado());
        reto.setFechaInicio(LocalDate.now().minusDays(2));
        reto.setFechaFin(LocalDate.now().minusDays(1));
        assertEquals("FINALIZADA", servicio.detalle(5L, 50, usuario).estado());
        reto.setActivo(false);
        assertEquals("NO_DISPONIBLE", servicio.detalle(5L, 50, usuario).estado());
        verify(colectivos, never()).cerrarVencido(any());
    }

    @Test
    void validaIdentidadIdentificadoresYPaginacion() {
        assertEquals(401, error(() -> servicio.crear(5L, null, datosValidos())));
        assertEquals(400, error(() -> servicio.crear(-1L, usuario, datosValidos())));
        assertEquals(400, error(() -> servicio.misCampanas(usuario, -1, 20)));
        assertEquals(400, error(() -> servicio.misCampanas(usuario, 0, 51)));
    }

    @Test
    void falloPersistenciaSolicitaRollbackTransaccional() {
        Tx tx = new Tx();
        doThrow(new DataAccessResourceFailureException("fallo interno")).when(retos).saveAndFlush(any());
        assertThrows(DataAccessResourceFailureException.class, () -> proxy(tx).crear(5L, usuario, datosValidos()));
        assertEquals(1, tx.rollbacks.get());
        assertEquals(0, tx.commits.get());
    }

    @Test
    void creacionesConcurrentesDelMismoPeriodoSeSerializanPorInstitucion() throws Exception {
        Tx tx = new Tx();
        var proxy = proxy(tx);
        AtomicBoolean creada = new AtomicBoolean();
        ReentrantLock bloqueo = new ReentrantLock();
        doAnswer(i -> {
            bloqueo.lock();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int estado) { bloqueo.unlock(); }
            });
            return Optional.of(5L);
        }).when(instituciones).bloquearCampanas(5L);
        when(retos.existsByInstitucion_IdAndTituloIgnoreCaseAndFechaInicioAndFechaFin(
                eq(5L), anyString(), any(), any())).thenAnswer(i -> creada.get());
        doAnswer(i -> {
            Reto reto = i.getArgument(0);
            reto.setId(50);
            creada.set(true);
            return reto;
        }).when(retos).saveAndFlush(any());
        CountDownLatch inicio = new CountDownLatch(1);
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> tarea = () -> {
                inicio.await();
                try { proxy.crear(5L, usuario, datosValidos()); return 201; }
                catch (ResponseStatusException error) { return error.getStatusCode().value(); }
            };
            Future<Integer> a = ejecutor.submit(tarea);
            Future<Integer> b = ejecutor.submit(tarea);
            inicio.countDown();
            assertEquals(Set.of(201, 409), Set.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)));
        }
        verify(retos, times(1)).saveAndFlush(any());
    }

    private Reto campana() {
        Reto reto = new Reto();
        reto.setId(50);
        reto.setInstitucion(institucion);
        reto.setTipo(TipoReto.COLECTIVO);
        reto.setTitulo("Nombre");
        reto.setDescripcion("Objetivo");
        reto.setActivo(true);
        reto.setFechaInicio(LocalDate.now());
        reto.setFechaFin(LocalDate.now().plusDays(1));
        return reto;
    }

    private int error(Runnable tarea) { return assertThrows(ResponseStatusException.class, tarea::run).getStatusCode().value(); }
    private CampanaInstitucionalService proxy(Tx tx) {
        ProxyFactory proxy = new ProxyFactory(servicio);
        proxy.addAdvice(new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
        return (CampanaInstitucionalService) proxy.getProxy();
    }
    static class Tx extends AbstractPlatformTransactionManager {
        AtomicInteger commits = new AtomicInteger();
        AtomicInteger rollbacks = new AtomicInteger();
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits.incrementAndGet(); }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks.incrementAndGet(); }
    }
}
