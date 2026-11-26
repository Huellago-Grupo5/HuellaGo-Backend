package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.CompartirLogroDTO;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.services.PublicacionComunitariaService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicacionComunitariaServiceImplTest {
    @Mock PublicacionComunitariaRepository publicaciones;
    @Mock UsuarioRepository usuarios;
    @Mock UsuarioInsigniaRepository insigniasObtenidas;
    @Mock InsigniaRepository insignias;
    @Mock UsuarioRetoRepository participaciones;
    @Mock ResultadoRetoColectivoRepository resultados;
    @InjectMocks PublicacionComunitariaServiceImpl servicio;
    Usuario autor;
    Insignia insignia;
    UsuarioInsignia obtenida;
    UsuarioReto participacion;

    @BeforeEach
    void datos() {
        autor = new Usuario();
        autor.setId(77L);
        autor.setNombres("Ana");
        autor.setCorreo("privado@example.test");
        autor.setContrasena("secreto");
        autor.setEcoPuntos(30);
        autor.setNivel(1);
        autor.setVisibilidadComunidad(true);
        insignia = new Insignia();
        insignia.setId(8);
        insignia.setNombre("Primer paso verde");
        obtenida = new UsuarioInsignia();
        obtenida.setId(11);
        obtenida.setUsuario(autor);
        obtenida.setInsignia(insignia);
        obtenida.setFechaObtencion(LocalDateTime.now().minusDays(1));
        Reto reto = new Reto();
        reto.setId(18);
        reto.setTitulo("Movilidad sostenible");
        participacion = new UsuarioReto();
        participacion.setId(9);
        participacion.setUsuario(autor);
        participacion.setReto(reto);
        participacion.setEstado("completado");
        participacion.setProgreso(BigDecimal.valueOf(100));
        participacion.setFechaCompletado(LocalDateTime.now().minusHours(1));
        lenient().when(usuarios.bloquearParaRecomendaciones(77L)).thenReturn(Optional.of(77L));
        lenient().when(usuarios.findById(77L)).thenReturn(Optional.of(autor));
        lenient().when(insignias.findById(8)).thenReturn(Optional.of(insignia));
        lenient().when(insigniasObtenidas.findFirstByUsuario_IdAndInsignia_IdOrderByIdAsc(77L, 8))
                .thenReturn(Optional.of(obtenida));
        lenient().when(participaciones.findByIdAndUsuario_Id(9, 77L)).thenReturn(Optional.of(participacion));
        lenient().when(publicaciones.saveAndFlush(any())).thenAnswer(i -> {
            PublicacionComunitaria p = i.getArgument(0);
            p.setId(1L);
            return p;
        });
    }

    private CompartirLogroDTO solicitud(CompartirLogroDTO.Tipo tipo, int id) {
        return new CompartirLogroDTO(tipo, id);
    }

    @Test
    void publicaInsigniaObtenidaConConfirmacionYFecha() {
        var dto = servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.INSIGNIA, 8));
        assertEquals("Logro publicado en la comunidad", dto.mensaje());
        assertEquals(1L, dto.publicacion().id());
        assertEquals(8, dto.publicacion().origenId());
        assertEquals("INSIGNIA", dto.publicacion().tipo());
        assertNotNull(dto.publicacion().fechaPublicacion());
        verify(publicaciones).saveAndFlush(argThat(p -> p.getAutor() == autor
                && p.getInsignia() == insignia && p.getParticipacion() == null));
        verify(usuarios, never()).save(any());
        verify(insigniasObtenidas, never()).save(any());
        assertEquals(30, autor.getEcoPuntos());
        assertEquals(1, autor.getNivel());
    }

    @Test
    void publicaRetoIndividualCompletadoSinModificarSuOrigenHu35() {
        UsuarioRecomendacionIA origen = new UsuarioRecomendacionIA();
        origen.setId(10);
        origen.setEstado("Pendiente");
        participacion.getReto().setRecomendacionOrigen(origen);
        var fecha = participacion.getFechaCompletado();
        var dto = servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.RETO, 9));
        assertEquals("RETO", dto.publicacion().tipo());
        assertEquals(9, dto.publicacion().origenId());
        assertSame(origen, participacion.getReto().getRecomendacionOrigen());
        assertNull(origen.getFechaCompletado());
        assertEquals("Pendiente", origen.getEstado());
        assertEquals(fecha, participacion.getFechaCompletado());
        verify(participaciones, never()).save(any());
        verifyNoInteractions(resultados);
    }

    @Test
    void colectivoExitosoConProgresoPropio100EsCompartibleSinCambiarHu29() {
        participacion.getReto().setTipo(TipoReto.COLECTIVO);
        participacion.setEstado("activo");
        participacion.setFechaCompletado(null);
        ResultadoRetoColectivo resultado = new ResultadoRetoColectivo();
        resultado.setEstado("EXITOSO");
        resultado.setProgresoFinal(BigDecimal.valueOf(100));
        resultado.setFechaCierre(LocalDateTime.now());
        when(resultados.findByReto_Id(18)).thenReturn(Optional.of(resultado));
        assertNotNull(servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.RETO, 9)));
        assertEquals("activo", participacion.getEstado());
        assertNull(participacion.getFechaCompletado());
        verify(resultados, never()).save(any());
    }

    @Test
    void colectivoNoCerradoONoAlcanzadoNoSeComparte() {
        participacion.getReto().setTipo(TipoReto.COLECTIVO);
        assertEquals(409, error(() -> servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.RETO, 9))));
        ResultadoRetoColectivo resultado = new ResultadoRetoColectivo();
        resultado.setEstado("NO_ALCANZADO");
        when(resultados.findByReto_Id(18)).thenReturn(Optional.of(resultado));
        assertEquals(409, error(() -> servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.RETO, 9))));
        verify(publicaciones, never()).saveAndFlush(any());
    }

    @Test
    void insigniaNoObtenidaOAjenaEs403YLaInexistente404() {
        when(insigniasObtenidas.findFirstByUsuario_IdAndInsignia_IdOrderByIdAsc(77L, 8)).thenReturn(Optional.empty());
        assertEquals(403, error(() -> servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.INSIGNIA, 8))));
        assertEquals(404, error(() -> servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.INSIGNIA, 999))));
        verify(publicaciones, never()).saveAndFlush(any());
    }

    @Test
    void insigniaSinFechaDeObtencionNoSePublica() {
        obtenida.setFechaObtencion(null);
        assertEquals(409, error(() -> servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.INSIGNIA, 8))));
    }

    @Test
    void retoAjenoOInexistenteEs404() {
        assertEquals(404, error(() -> servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.RETO, 999))));
        verify(participaciones).findByIdAndUsuario_Id(999, 77L);
        verify(publicaciones, never()).saveAndFlush(any());
    }

    @Test
    void dosUsuariosConLaMismaInsigniaPuedenCompartirlaIndependientemente() {
        Usuario otro = new Usuario();
        otro.setId(88L);
        otro.setNombres("Luis");
        UsuarioInsignia otraObtencion = new UsuarioInsignia();
        otraObtencion.setUsuario(otro);
        otraObtencion.setInsignia(insignia);
        otraObtencion.setFechaObtencion(LocalDateTime.now());
        when(usuarios.bloquearParaRecomendaciones(88L)).thenReturn(Optional.of(88L));
        when(usuarios.findById(88L)).thenReturn(Optional.of(otro));
        when(insigniasObtenidas.findFirstByUsuario_IdAndInsignia_IdOrderByIdAsc(88L, 8))
                .thenReturn(Optional.of(otraObtencion));
        assertEquals(77L, servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.INSIGNIA, 8)).publicacion().autorId());
        assertEquals(88L, servicio.compartir(otro, solicitud(CompartirLogroDTO.Tipo.INSIGNIA, 8)).publicacion().autorId());
        verify(publicaciones, times(2)).saveAndFlush(any());
    }

    @Test
    void progresoInsuficienteActivoCanceladoOSinFechaSeRechazan() {
        participacion.setProgreso(BigDecimal.valueOf(50));
        assertEquals(409, error(() -> servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.RETO, 9))));
        participacion.setProgreso(BigDecimal.valueOf(100));
        participacion.setEstado("activo");
        assertEquals(409, error(() -> servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.RETO, 9))));
        participacion.setEstado("cancelado");
        assertEquals(409, error(() -> servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.RETO, 9))));
        participacion.setEstado("completado");
        participacion.setFechaCompletado(null);
        assertEquals(409, error(() -> servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.RETO, 9))));
        verify(publicaciones, never()).saveAndFlush(any());
    }

    @Test
    void duplicadosDeInsigniaYRetoSon409() {
        when(publicaciones.existsByAutor_IdAndInsignia_Id(77L, 8)).thenReturn(true);
        assertEquals(409, error(() -> servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.INSIGNIA, 8))));
        when(publicaciones.existsByParticipacion_Id(9)).thenReturn(true);
        assertEquals(409, error(() -> servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.RETO, 9))));
        verify(publicaciones, never()).saveAndFlush(any());
    }

    @Test
    void validaIdentidadDatosYTitulo() {
        assertEquals(401, error(() -> servicio.compartir(null, solicitud(CompartirLogroDTO.Tipo.RETO, 9))));
        assertEquals(400, error(() -> servicio.compartir(autor, null)));
        assertEquals(400, error(() -> servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.RETO, -1))));
        insignia.setNombre(" ");
        assertEquals(409, error(() -> servicio.compartir(autor, solicitud(CompartirLogroDTO.Tipo.INSIGNIA, 8))));
    }

    @Test
    void feedRespetaPrivacidadActualInclusoEnPublicacionesPrevias() {
        PublicacionComunitaria p = publicacion();
        when(publicaciones.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(p)));
        Usuario visor = new Usuario();
        visor.setId(88L);
        var publica = servicio.listar(visor, 0, 20).getFirst();
        assertEquals("Ana", publica.autor());
        autor.setVisibilidadComunidad(false);
        var privada = servicio.listar(visor, 0, 20).getFirst();
        assertEquals("Usuario privado", privada.autor());
        assertNull(privada.autorId());
        assertNull(privada.origenId());
        assertEquals("Ana", servicio.listar(autor, 0, 20).getFirst().autor());
        autor.setVisibilidadComunidad(null);
        assertNull(servicio.listar(visor, 0, 20).getFirst().autorId());
        verify(publicaciones, never()).save(any());
    }

    @Test
    void consultaPaginadaOrdenadaYLimitesValidos() {
        when(publicaciones.findAll(any(Pageable.class))).thenReturn(Page.empty());
        assertTrue(servicio.listar(autor, 0, 20).isEmpty());
        verify(publicaciones).findAll(argThat((Pageable p) -> p.getPageSize() == 20
                && p.getSort().getOrderFor("fechaPublicacion").isDescending()));
        assertEquals(400, error(() -> servicio.listar(autor, -1, 20)));
        assertEquals(400, error(() -> servicio.listar(autor, 0, 51)));
        assertEquals(401, error(() -> servicio.listar(null, 0, 20)));
    }

    @Test
    void errorDePersistenciaActivaRollbackDeSpring() {
        Tx tx = new Tx();
        doThrow(new DataAccessResourceFailureException("fallo interno")).when(publicaciones).saveAndFlush(any());
        PublicacionComunitariaService proxy = transaccional(tx);
        assertThrows(DataAccessResourceFailureException.class,
                () -> proxy.compartir(autor, solicitud(CompartirLogroDTO.Tipo.INSIGNIA, 8)));
        assertEquals(1, tx.rollbacks.get());
        assertEquals(0, tx.commits.get());
        assertEquals(30, autor.getEcoPuntos());
    }

    @Test
    void solicitudesSimultaneasSoloPublicanUnaVezBajoBloqueoDeUsuario() throws Exception {
        Tx tx = new Tx();
        PublicacionComunitariaService proxy = transaccional(tx);
        ReentrantLock bloqueo = new ReentrantLock();
        AtomicBoolean publicada = new AtomicBoolean();
        doAnswer(i -> {
            bloqueo.lock();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int estado) { bloqueo.unlock(); }
            });
            return Optional.of(77L);
        }).when(usuarios).bloquearParaRecomendaciones(77L);
        when(publicaciones.existsByAutor_IdAndInsignia_Id(77L, 8)).thenAnswer(i -> publicada.get());
        doAnswer(i -> {
            publicada.set(true);
            PublicacionComunitaria p = i.getArgument(0);
            p.setId(1L);
            return p;
        }).when(publicaciones).saveAndFlush(any());
        CountDownLatch inicio = new CountDownLatch(1);
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> tarea = () -> {
                inicio.await();
                try {
                    proxy.compartir(autor, solicitud(CompartirLogroDTO.Tipo.INSIGNIA, 8));
                    return 201;
                } catch (ResponseStatusException error) {
                    return error.getStatusCode().value();
                }
            };
            Future<Integer> primera = ejecutor.submit(tarea);
            Future<Integer> segunda = ejecutor.submit(tarea);
            inicio.countDown();
            assertEquals(Set.of(201, 409), Set.of(primera.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS)));
        }
        verify(publicaciones, times(1)).saveAndFlush(any());
    }

    private PublicacionComunitaria publicacion() {
        PublicacionComunitaria p = new PublicacionComunitaria();
        p.setId(1L);
        p.setAutor(autor);
        p.setInsignia(insignia);
        p.setTitulo(insignia.getNombre());
        p.setFechaPublicacion(LocalDateTime.now());
        return p;
    }

    private PublicacionComunitariaService transaccional(Tx tx) {
        ProxyFactory proxy = new ProxyFactory(servicio);
        proxy.addAdvice(new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
        return (PublicacionComunitariaService) proxy.getProxy();
    }

    static class Tx extends AbstractPlatformTransactionManager {
        AtomicInteger commits = new AtomicInteger();
        AtomicInteger rollbacks = new AtomicInteger();
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits.incrementAndGet(); }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks.incrementAndGet(); }
    }

    private int error(Runnable tarea) {
        return assertThrows(ResponseStatusException.class, tarea::run).getStatusCode().value();
    }
}
