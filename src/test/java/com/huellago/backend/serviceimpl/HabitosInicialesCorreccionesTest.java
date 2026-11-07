package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Persistencia simulada: no sustituye la verificacion de bloqueos PostgreSQL. */
@ExtendWith(MockitoExtension.class)
class HabitosInicialesCorreccionesTest {
    @Mock UsuarioRepository usuarios;
    @Mock HabitoRepository habitos;
    @Mock CategoriaHabitoRepository categorias;
    @Mock HuellaCarbonoRepository huellas;
    @Mock org.springframework.context.ApplicationEventPublisher eventos;
    @Spy PoliticaMedicionInicial politica = new PoliticaMedicionInicial();
    @InjectMocks HabitoServiceImpl registro;
    @InjectMocks HuellaCarbonoServiceImpl calculo;
    final List<Habito> filas = new CopyOnWriteArrayList<>();
    Usuario usuario;

    @BeforeEach void datos() {
        usuario = new Usuario(); usuario.setId(9001L);
        lenient().when(usuarios.findById(9001L)).thenReturn(Optional.of(usuario));
        lenient().when(usuarios.bloquearParaRecomendaciones(9001L)).thenReturn(Optional.of(9001L));
        lenient().when(categorias.findByNombreIgnoreCase(anyString())).thenAnswer(i -> {
            var categoria = new CategoriaHabito(); categoria.setId(1L); categoria.setNombre(i.getArgument(0)); return categoria;
        });
        lenient().when(habitos.countByUsuario_IdAndCategoria_NombreIgnoreCase(eq(9001L),anyString()))
                .thenAnswer(i -> filas.stream().filter(h -> h.getCategoria().getNombre().equalsIgnoreCase(i.getArgument(1))).count());
        lenient().when(habitos.save(any())).thenAnswer(i -> {
            Habito h=i.getArgument(0); h.setId((long) filas.size()+1); filas.add(h); return h;
        });
        lenient().when(habitos.findByUsuario_Id(9001L)).thenAnswer(i -> List.copyOf(filas));
        lenient().when(huellas.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    TransporteDTO transporte(int dias) { return new TransporteDTO(9001L,"Bus",new BigDecimal("120"),dias); }
    EnergiaDTO energia() { return new EnergiaDTO(9001L,"Casa",3,"Electricidad"); }
    AlimentacionResiduosDTO alimentacion() { return new AlimentacionResiduosDTO(9001L,"Mixta","Medio",true); }
    void anteriores() { registro.registrarTransporte(transporte(5)); registro.registrarEnergia(energia()); }
    void completos() { anteriores(); registro.registrarAlimentacionResiduos(alimentacion()); }
    void error400(Runnable accion) { assertEquals(400,assertThrows(ResponseStatusException.class,accion::run).getStatusCode().value()); }

    @ParameterizedTest @ValueSource(ints={-1,0,8,100})
    void rechazaFrecuenciaFueraDeRangoSinGuardar(int dias) {
        error400(() -> registro.registrarTransporte(transporte(dias)));
        assertTrue(filas.isEmpty()); verifyNoInteractions(usuarios);
    }
    @ParameterizedTest @ValueSource(ints={1,7})
    void aceptaLimitesYBloqueaAntesDeComprobarDuplicados(int dias) {
        registro.registrarTransporte(transporte(dias));
        assertEquals(3,filas.size());
        var orden=inOrder(usuarios,habitos);
        orden.verify(usuarios).bloquearParaRecomendaciones(9001L);
        orden.verify(usuarios).findById(9001L);
        orden.verify(habitos).countByUsuario_IdAndCategoria_NombreIgnoreCase(9001L,"Transporte");
    }
    @Test void camposObligatoriosYKilometrosInvalidos() {
        error400(() -> registro.registrarTransporte(null));
        for (String medio : Arrays.asList(null,""," ","Avion")) {
            var dto=transporte(5); dto.setMedio(medio); error400(() -> registro.registrarTransporte(dto));
        }
        for (BigDecimal km : Arrays.asList(null,BigDecimal.ZERO,BigDecimal.ONE.negate())) {
            var dto=transporte(5); dto.setKmSemana(km); error400(() -> registro.registrarTransporte(dto));
        }
        var sinDias=transporte(5); sinDias.setDiasSemana(null); error400(() -> registro.registrarTransporte(sinDias));
        var sinUsuario=transporte(5); sinUsuario.setUsuarioId(null);
        error400(() -> registro.registrarTransporte(sinUsuario));
        assertTrue(filas.isEmpty());
    }
    @Test void lasTresOperacionesRechazanDuplicados() {
        completos();
        for (Runnable accion : List.<Runnable>of(() -> registro.registrarTransporte(transporte(5)),
                () -> registro.registrarEnergia(energia()), () -> registro.registrarAlimentacionResiduos(alimentacion())))
            assertEquals(409,assertThrows(ResponseStatusException.class,accion::run).getStatusCode().value());
        assertEquals(9,filas.size());
    }
    @Test void energiaConservaContratoAnteriorYRegistraConsumoExplicito() {
        var dto=energia(); dto.setConsumoKwhDiario(new BigDecimal("4.5"));
        var respuesta=registro.registrarEnergia(dto);
        assertEquals(dto.getConsumoKwhDiario(),respuesta.getConsumoKwhDiario());
        assertEquals(4,filas.size()); assertEquals("kWh/dia",filas.get(3).getUnidad());
    }
    @Test void energiaRechazaDatosInvalidosAntesDePersistir() {
        error400(() -> registro.registrarEnergia(null));
        var negativa=energia(); negativa.setConsumoKwhDiario(BigDecimal.ONE.negate()); error400(() -> registro.registrarEnergia(negativa));
        var gas=energia(); gas.setFuente("Gas"); gas.setConsumoKwhDiario(BigDecimal.ONE); error400(() -> registro.registrarEnergia(gas));
        for (Integer personas : Arrays.asList(null,0,-1)) {
            var dto=energia(); dto.setPersonas(personas); error400(() -> registro.registrarEnergia(dto));
        }
        var vivienda=energia(); vivienda.setVivienda(" "); error400(() -> registro.registrarEnergia(vivienda));
        var fuente=energia(); fuente.setFuente("Desconocida"); error400(() -> registro.registrarEnergia(fuente));
        assertTrue(filas.isEmpty());
    }
    @Test void alimentacionIncompletaNoFinalizaYEnumeraCategorias() {
        var respuesta=registro.registrarAlimentacionResiduos(alimentacion());
        assertFalse(respuesta.isConfiguracionInicialCompleta());
        assertEquals(List.of("Transporte","Energía"),respuesta.getCategoriasPendientes());
        error400(() -> calculo.calcularHuellaInicial(9001L)); verify(huellas,never()).save(any());
    }
    @Test void alimentacionCompletaFinalizaSinModificarLogin() {
        anteriores(); var respuesta=registro.registrarAlimentacionResiduos(alimentacion());
        assertTrue(respuesta.isConfiguracionInicialCompleta()); assertTrue(respuesta.getCategoriasPendientes().isEmpty());
        var resultado=calculo.calcularHuellaInicial(9001L);
        assertEquals(0,new BigDecimal("10.5").compareTo(resultado.getCo2Total()));
        assertEquals("kg CO2e",resultado.getUnidad()); assertEquals("semanal",resultado.getPeriodo());
        assertEquals("NO_DEFINIDO",resultado.getNivelImpacto()); verify(huellas).save(any());
    }
    @Test void categoriaConCampoInvalidoODuplicadoNoCuentaComoCompleta() {
        completos(); filas.get(2).setValor(new BigDecimal("8"));
        assertEquals(List.of("Transporte"),ValidacionHabitosIniciales.categoriasPendientes(filas));
        error400(() -> calculo.calcularHuellaInicial(9001L));
        filas.get(2).setValor(BigDecimal.ONE); filas.add(filas.get(2));
        error400(() -> calculo.calcularHuellaInicial(9001L)); verify(huellas,never()).save(any());
    }
    @Test void consumoRealNoSeConvierteConFactoresInventados() {
        registro.registrarTransporte(transporte(5)); var dto=energia(); dto.setConsumoKwhDiario(BigDecimal.ONE);
        registro.registrarEnergia(dto); registro.registrarAlimentacionResiduos(alimentacion());
        assertEquals(503,assertThrows(ResponseStatusException.class,() -> calculo.calcularHuellaInicial(9001L)).getStatusCode().value());
        verify(huellas,never()).save(any());
        politica.factorElectricidad="0.4"; politica.referenciaElectricidad="Factor ficticio exclusivo del test";
        var resultado=calculo.calcularHuellaInicial(9001L);
        assertEquals(0,new BigDecimal("2.8").compareTo(resultado.getCo2Energia()));
    }
    @Test void noCambiaFuenteDeUnConsumoElectricoAUnaIncompatible() {
        var dto=energia(); dto.setConsumoKwhDiario(BigDecimal.ZERO); registro.registrarEnergia(dto);
        Habito fuente=filas.get(2); when(habitos.findById(fuente.getId())).thenReturn(Optional.of(fuente));
        error400(() -> registro.actualizar(fuente.getId(),usuario,new ActualizarHabitoDTO("fuente",BigDecimal.ZERO,"Gas")));
        assertEquals("Electricidad",fuente.getUnidad());
    }
    @Test void alimentacionRechazaDatosFaltantes() {
        error400(() -> registro.registrarAlimentacionResiduos(null));
        var tipo=alimentacion(); tipo.setTipo(" "); error400(() -> registro.registrarAlimentacionResiduos(tipo));
        var plasticos=alimentacion(); plasticos.setPlasticos("Desconocido"); error400(() -> registro.registrarAlimentacionResiduos(plasticos));
        var reciclas=alimentacion(); reciclas.setReciclas(null); error400(() -> registro.registrarAlimentacionResiduos(reciclas));
        assertTrue(filas.isEmpty());
    }
    @Test void solicitudesConcurrentesConBloqueoDeFilaSimuladoSoloRegistranUnaCategoria() throws Exception {
        ReentrantLock filaUsuario=new ReentrantLock();
        when(usuarios.bloquearParaRecomendaciones(9001L)).thenAnswer(i -> { filaUsuario.lock(); return Optional.of(9001L); });
        var inicio=new CountDownLatch(1);
        try (var executor=Executors.newFixedThreadPool(2)) {
            Callable<Integer> solicitud=() -> {
                inicio.await();
                try { registro.registrarTransporte(transporte(5)); return 201; }
                catch (ResponseStatusException error) { return error.getStatusCode().value(); }
                finally { if (filaUsuario.isHeldByCurrentThread()) filaUsuario.unlock(); }
            };
            var a=executor.submit(solicitud); var b=executor.submit(solicitud); inicio.countDown();
            assertEquals(Set.of(201,409),Set.of(a.get(5,TimeUnit.SECONDS),b.get(5,TimeUnit.SECONDS)));
            assertEquals(3,filas.size());
        }
    }
}
