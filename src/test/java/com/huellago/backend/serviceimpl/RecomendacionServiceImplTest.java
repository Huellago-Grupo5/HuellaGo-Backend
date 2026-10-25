package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecomendacionServiceImplTest {

    @Mock UsuarioRepository usuarioRepository;
    @Mock HabitoRepository habitoRepository;
    @Mock HuellaCarbonoRepository huellaCarbonoRepository;
    @Mock CategoriaHabitoRepository categoriaHabitoRepository;
    @Mock RecomendacionIARepository recomendacionIARepository;
    @Mock UsuarioRecomendacionIARepository usuarioRecomendacionIARepository;
    @Mock UsuarioRetoRepository usuarioRetoRepository;
    @InjectMocks RecomendacionServiceImpl service;

    private final Map<Long, Usuario> usuarios = new HashMap<>();
    private final Map<Long, List<Habito>> habitos = new HashMap<>();
    private final Map<Long, HuellaCarbono> huellas = new HashMap<>();
    private final Map<String, RecomendacionIA> catalogo = new HashMap<>();
    private final Map<String, UsuarioRecomendacionIA> asociaciones = new HashMap<>();
    private final LocalDateTime fechaHabitos = LocalDateTime.of(2026, 10, 20, 10, 0);

    @BeforeEach
    void setUp() {
        for (Long id : List.of(22L, 23L)) {
            Usuario usuario = new Usuario();
            usuario.setId(id);
            usuario.setCorreo("usuario" + id + "@huellago.com");
            usuarios.put(id, usuario);
        }
        // Almacenamiento simulado con las mismas claves que usa el repositorio real.
        lenient().when(usuarioRepository.bloquearParaRecomendaciones(anyLong()))
                .thenAnswer(i -> Optional.ofNullable(usuarios.get(i.getArgument(0)))
                        .map(Usuario::getId));
        lenient().when(usuarioRepository.findById(anyLong()))
                .thenAnswer(i -> Optional.ofNullable(usuarios.get(i.getArgument(0))));
        lenient().when(habitoRepository.findByUsuario_Id(anyLong()))
                .thenAnswer(i -> habitos.getOrDefault(i.getArgument(0), List.of()));
        lenient().when(huellaCarbonoRepository.findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(anyLong()))
                .thenAnswer(i -> huellas.get(i.getArgument(0)));
        lenient().when(categoriaHabitoRepository.bloquearParaRecomendaciones(anyLong()))
                .thenAnswer(i -> Optional.of(i.getArgument(0)));
        lenient().when(recomendacionIARepository
                .findFirstByCategoria_IdAndTituloIgnoreCaseOrderByIdAsc(anyLong(), anyString()))
                .thenAnswer(i -> Optional.ofNullable(catalogo.get(i.getArgument(0) + ":"
                        + i.getArgument(1).toString().toLowerCase(Locale.ROOT))));
        lenient().when(recomendacionIARepository.save(any(RecomendacionIA.class))).thenAnswer(i -> {
            RecomendacionIA rec = i.getArgument(0);
            rec.setId(catalogo.size() + 1);
            catalogo.put(rec.getCategoria().getId() + ":" + rec.getTitulo().toLowerCase(Locale.ROOT), rec);
            return rec;
        });
        lenient().when(usuarioRecomendacionIARepository
                .findFirstByUsuario_IdAndRecomendacionIA_IdOrderByIdAsc(anyLong(), anyInt()))
                .thenAnswer(i -> Optional.ofNullable(asociaciones.get(i.getArgument(0) + ":" + i.getArgument(1))));
        lenient().when(usuarioRecomendacionIARepository.save(any(UsuarioRecomendacionIA.class)))
                .thenAnswer(i -> {
                    UsuarioRecomendacionIA rec = i.getArgument(0);
                    rec.setId(asociaciones.size() + 1);
                    asociaciones.put(rec.getUsuario().getId() + ":" + rec.getRecomendacionIA().getId(), rec);
                    return rec;
                });
    }

    @Test
    void generaPersonalizaYPersisteConHabitosCompletosSinCalcularOtraHuella() {
        habitosCompletos(22L, "Auto", "Electricidad", "Mixta", "Alto", false);
        huella(22L, "12", "1.8", "2", "1.8");

        var resultado = service.generar(usuarios.get(22L));

        assertEquals("GENERADAS", resultado.getEstado());
        assertEquals("REGLAS_DETERMINISTAS", resultado.getMetodo());
        assertEquals("HUELLA_REGISTRADA", resultado.getBasePriorizacion());
        assertTrue(resultado.getDatosFaltantes().isEmpty());
        assertEquals(4, resultado.getRecomendaciones().size());
        var primera = resultado.getRecomendaciones().getFirst();
        assertEquals("Transporte", primera.getCategoria());
        assertEquals("ALTA", primera.getPrioridad());
        assertEquals(new BigDecimal("12"), primera.getCo2Categoria());
        assertTrue(primera.getMotivo().contains("Auto, 100 km por semana y 5 días"));
        assertEquals("Pendiente", primera.getEstado());
        assertNotNull(primera.getFechaGeneracion());
        assertEquals(4, asociaciones.size());
        assertEquals(4, catalogo.size());
        assertTrue(asociaciones.values().stream().allMatch(a -> a.getUsuario().getId().equals(22L)));
        assertTrue(catalogo.values().stream().allMatch(r -> r.getPuntosRecompensa() == 0));
        verify(huellaCarbonoRepository, never()).save(any());
        verify(habitoRepository, never()).save(any());
    }

    @Test
    void priorizaLaCategoriaDeMayorImpactoAunqueNoSeaTransporte() {
        habitosCompletos(22L, "Bus", "Gas", "Vegetariana", "Medio", true);
        huella(22L, "0.5", "4", "1", "0.7");

        var resultado = service.generar(usuarios.get(22L));

        assertEquals("Energía", resultado.getRecomendaciones().getFirst().getCategoria());
        assertTrue(resultado.getRecomendaciones().getFirst().getMotivo().contains("fuente Gas"));
        assertTrue(resultado.getRecomendaciones().getFirst().getDescripcion().contains("cocinar"));
    }

    @Test
    void usuariosConHabitosDistintosRecibenAccionesDistintasYDatosAislados() {
        habitosCompletos(22L, "Auto", "Electricidad", "Mixta", "Alto", false);
        habitosCompletos(23L, "Bicicleta", "Solar", "Vegana", "Bajo", true);

        var uno = service.generar(usuarios.get(22L));
        var dos = service.generar(usuarios.get(23L));

        assertNotEquals(uno.getRecomendaciones().stream().map(r -> r.getTitulo()).toList(),
                dos.getRecomendaciones().stream().map(r -> r.getTitulo()).toList());
        assertTrue(dos.getRecomendaciones().getFirst().getMotivo().contains("Bicicleta"));
        assertEquals(23L, dos.getUsuarioId());
        assertEquals(4, asociaciones.values().stream()
                .filter(a -> a.getUsuario().getId().equals(23L)).count());
        verify(habitoRepository).findByUsuario_Id(22L);
        verify(habitoRepository).findByUsuario_Id(23L);
    }

    @Test
    void informaElCampoFaltanteYGeneraSoloSobreCategoriasCompletas() {
        habitosCompletos(22L, "Moto", "Electricidad", "Mixta", "Alto", false);
        habitos.get(22L).removeIf(h -> "personas".equals(h.getNombre()));

        var resultado = service.generar(usuarios.get(22L));

        assertEquals("PARCIALES", resultado.getEstado());
        assertEquals(1, resultado.getDatosFaltantes().size());
        assertTrue(resultado.getDatosFaltantes().getFirst().startsWith("Energía.personas:"));
        assertEquals(3, resultado.getRecomendaciones().size());
        assertTrue(resultado.getRecomendaciones().stream().noneMatch(r -> "Energía".equals(r.getCategoria())));
        assertEquals("HABITOS_REGISTRADOS", resultado.getBasePriorizacion());
    }

    @Test
    void sinHabitosInformaQueCompletarSinCrearRegistros() {
        var resultado = service.generar(usuarios.get(22L));

        assertEquals("DATOS_INSUFICIENTES", resultado.getEstado());
        assertEquals(9, resultado.getDatosFaltantes().size());
        assertTrue(resultado.getRecomendaciones().isEmpty());
        verify(recomendacionIARepository, never()).save(any());
        verify(usuarioRecomendacionIARepository, never()).save(any());
        verify(categoriaHabitoRepository, never()).bloquearParaRecomendaciones(anyLong());
    }

    @Test
    void solicitudesRepetidasReutilizanCatalogoYAsociacionesSinReiniciarLasCompletadas() {
        habitosCompletos(22L, "Auto", "Electricidad", "Mixta", "Alto", false);
        var primera = service.generar(usuarios.get(22L));
        UsuarioRecomendacionIA completada = asociaciones.values().iterator().next();
        completada.setEstado("Completado");
        completada.setFechaCompletado(fechaHabitos.plusDays(1));
        var segunda = service.generar(usuarios.get(22L));

        assertEquals(4, catalogo.size());
        assertEquals(4, asociaciones.size());
        assertEquals(3, segunda.getRecomendaciones().size());
        assertTrue(segunda.getRecomendaciones().stream().allMatch(actual ->
                primera.getRecomendaciones().stream().anyMatch(previa ->
                        previa.getUsuarioRecomendacionId().equals(actual.getUsuarioRecomendacionId())
                                && previa.getFechaGeneracion().equals(actual.getFechaGeneracion()))));
        assertTrue(segunda.getRecomendaciones().stream().noneMatch(r -> "Completado".equals(r.getEstado())));
        assertEquals(fechaHabitos.plusDays(1), completada.getFechaCompletado());
        verify(recomendacionIARepository, times(4)).save(any());
        verify(usuarioRecomendacionIARepository, times(4)).save(any());
    }

    @Test
    void catalogoCompartidoNoComparteLasAsociacionesEntreUsuarios() {
        habitosCompletos(22L, "Auto", "Electricidad", "Mixta", "Alto", false);
        habitosCompletos(23L, "Auto", "Electricidad", "Mixta", "Alto", false);

        var uno = service.generar(usuarios.get(22L));
        var dos = service.generar(usuarios.get(23L));

        assertEquals(4, catalogo.size());
        assertEquals(8, asociaciones.size());
        assertEquals(uno.getRecomendaciones().getFirst().getRecomendacionId(),
                dos.getRecomendaciones().getFirst().getRecomendacionId());
        assertNotEquals(uno.getRecomendaciones().getFirst().getUsuarioRecomendacionId(),
                dos.getRecomendaciones().getFirst().getUsuarioRecomendacionId());
    }

    @Test
    void noUsaUnDesgloseAnteriorALaEdicionDelHabitoYEligeElValorMasReciente() {
        habitosCompletos(22L, "Auto", "Electricidad", "Mixta", "Alto", false);
        huella(22L, "12", "1.8", "2", "1.8");
        Habito nuevo = habito(22L, 12L, "Transporte", "medio", "0", "Caminando");
        nuevo.setId(999L);
        nuevo.setFechaActualizacion(fechaHabitos.plusDays(2));
        habitos.get(22L).add(nuevo);

        var resultado = service.generar(usuarios.get(22L));

        assertEquals("HABITOS_REGISTRADOS", resultado.getBasePriorizacion());
        assertNull(resultado.getHuellaId());
        assertNull(resultado.getRecomendaciones().getFirst().getCo2Categoria());
        assertTrue(resultado.getRecomendaciones().getFirst().getMotivo().contains("Caminando"));
        assertFalse(resultado.getAdvertencias().isEmpty());
    }

    @Test
    void datosMalFormadosONulosSonInformadosSin500() {
        habitosCompletos(22L, "Auto", "Electricidad", "Mixta", "Alto", false);
        habitos.get(22L).stream().filter(h -> "medio".equals(h.getNombre()))
                .forEach(h -> h.setUnidad(null));
        habitos.get(22L).stream().filter(h -> "reciclas".equals(h.getNombre()))
                .forEach(h -> h.setValor(BigDecimal.TEN));
        huella(22L, "12", "1.8", "2", "1.8").setCo2Energia(null);

        var resultado = service.generar(usuarios.get(22L));

        assertEquals("PARCIALES", resultado.getEstado());
        assertEquals(2, resultado.getDatosFaltantes().size());
        assertEquals(2, resultado.getRecomendaciones().size());
    }

    @Test
    void desgloseIncompletoNoImpideGenerarRecomendacionesDeHabitosCompletos() {
        habitosCompletos(22L, "Auto", "Electricidad", "Mixta", "Alto", false);
        huella(22L, "12", "1.8", "2", "1.8").setCo2Residuos(null);

        var resultado = service.generar(usuarios.get(22L));

        assertEquals("GENERADAS", resultado.getEstado());
        assertEquals("HABITOS_REGISTRADOS", resultado.getBasePriorizacion());
        assertEquals(4, resultado.getRecomendaciones().size());
    }

    @Test
    void usaLosComponentesGuardadosAunqueElTotalTengaRedondeoIndependiente() {
        habitosCompletos(22L, "Auto", "Gas", "Mixta", "Alto", false);
        huella(22L, "12", "1.28", "2", "1.8").setCo2Total(new BigDecimal("17.07"));

        var resultado = service.generar(usuarios.get(22L));

        assertEquals("HUELLA_REGISTRADA", resultado.getBasePriorizacion());
        assertEquals("Transporte", resultado.getRecomendaciones().getFirst().getCategoria());
        assertEquals(new BigDecimal("12"), resultado.getRecomendaciones().getFirst().getCo2Categoria());
    }

    @Test
    void usuarioInexistenteDevuelve404SinAccederAHabitos() {
        Usuario inexistente = new Usuario();
        inexistente.setId(999L);

        var error = assertThrows(ResponseStatusException.class, () -> service.generar(inexistente));

        assertEquals(404, error.getStatusCode().value());
        verifyNoInteractions(habitoRepository);
    }

    @Test
    void cambioDeHabitoActualizaAccionSinEliminarAsignacionesAnteriores() {
        habitosCompletos(22L, "Auto", "Electricidad", "Mixta", "Alto", false);
        var anterior = service.generar(usuarios.get(22L));
        habitos.get(22L).stream().filter(h -> "medio".equals(h.getNombre())).forEach(h -> {
            h.setUnidad("Bus");
            h.setFechaActualizacion(fechaHabitos.plusDays(2));
        });
        var actual = service.generar(usuarios.get(22L));
        assertNotEquals(anterior.getRecomendaciones().getFirst().getRecomendacionId(),
                actual.getRecomendaciones().getFirst().getRecomendacionId());
        assertTrue(actual.getRecomendaciones().getFirst().getTitulo().contains("bus"));
        assertEquals(5, asociaciones.size());
        service.generar(usuarios.get(22L));
        assertEquals(5, asociaciones.size());
        verify(usuarioRecomendacionIARepository, never()).delete(any());
    }

    @Test
    void cambioDeCategoriaDominanteGeneraUnaAccionEnfocadaDistinta() {
        habitosCompletos(22L, "Auto", "Gas", "Mixta", "Alto", false);
        huella(22L, "12", "1.8", "2", "1.8");
        var anterior = service.generar(usuarios.get(22L));
        huella(22L, "1", "4", "2", "1.8");
        var actual = service.generar(usuarios.get(22L));
        assertEquals("Energía", actual.getRecomendaciones().getFirst().getCategoria());
        assertNotEquals(anterior.getRecomendaciones().getFirst().getTitulo(),
                actual.getRecomendaciones().getFirst().getTitulo());
        assertTrue(actual.getRecomendaciones().getFirst().getDescripcion().contains("lista de equipos"));
        assertEquals(6, asociaciones.size());
        verify(huellaCarbonoRepository, never()).save(any());
    }

    @Test
    void variacionNumericaConMismaAccionYPrioridadConservaAsignacionesYFechas() {
        habitosCompletos(22L, "Auto", "Gas", "Mixta", "Alto", false);
        huella(22L, "12", "1.8", "2", "1.8");
        var anterior = service.generar(usuarios.get(22L));
        huella(22L, "11.9", "1.8", "2", "1.8");
        var actual = service.generar(usuarios.get(22L));
        assertEquals(anterior.getRecomendaciones().stream().map(r -> r.getUsuarioRecomendacionId()).toList(),
                actual.getRecomendaciones().stream().map(r -> r.getUsuarioRecomendacionId()).toList());
        assertEquals(anterior.getRecomendaciones().getFirst().getFechaGeneracion(),
                actual.getRecomendaciones().getFirst().getFechaGeneracion());
        assertEquals(4, asociaciones.size());
    }

    @Test
    void retoPersonalCompletadoDejaDeSugerirseSinCompletarSuRecomendacion() {
        habitosCompletos(22L, "Auto", "Gas", "Mixta", "Alto", false);
        var anterior = service.generar(usuarios.get(22L));
        Integer origenId = anterior.getRecomendaciones().getFirst().getUsuarioRecomendacionId();
        UsuarioRecomendacionIA origen = asociaciones.values().stream()
                .filter(a -> a.getId().equals(origenId)).findFirst().orElseThrow();
        Reto reto = new Reto();
        reto.setRecomendacionOrigen(origen);
        UsuarioReto participacion = new UsuarioReto();
        participacion.setReto(reto);
        participacion.setEstado("completado");
        when(usuarioRetoRepository.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaAceptacionDesc(
                22L, "completado")).thenReturn(List.of(participacion));
        var actual = service.generar(usuarios.get(22L));
        assertEquals(3, actual.getRecomendaciones().size());
        assertTrue(actual.getRecomendaciones().stream().noneMatch(r -> origenId.equals(r.getUsuarioRecomendacionId())));
        assertEquals("Pendiente", origen.getEstado());
        assertNull(origen.getFechaCompletado());
        assertSame(origen, reto.getRecomendacionOrigen());
        verify(usuarioRetoRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void todasRealizadasConservanHistorialSinCrearRecomendacionesArtificiales() {
        habitosCompletos(22L, "Auto", "Gas", "Mixta", "Alto", false);
        service.generar(usuarios.get(22L));
        asociaciones.values().forEach(a -> {
            a.setEstado("completado");
            a.setFechaCompletado(fechaHabitos.plusDays(1));
        });
        var actual = service.generar(usuarios.get(22L));
        assertEquals("SIN_SUGERENCIAS_NUEVAS", actual.getEstado());
        assertTrue(actual.getRecomendaciones().isEmpty());
        assertEquals(4, asociaciones.size());
        assertTrue(asociaciones.values().stream().allMatch(a ->
                fechaHabitos.plusDays(1).equals(a.getFechaCompletado())));
    }

    private void habitosCompletos(Long usuarioId, String medio, String fuente,
                                  String tipo, String plasticos, boolean recicla) {
        // Las categorías sin tilde son las que ya existen en PostgreSQL.
        habitos.put(usuarioId, new ArrayList<>(List.of(
                habito(usuarioId, 12L, "Transporte", "medio", "0", medio),
                habito(usuarioId, 12L, "Transporte", "kmSemana", "100", "km/semana"),
                habito(usuarioId, 12L, "Transporte", "diasSemana", "5", "dias/semana"),
                habito(usuarioId, 10L, "Energia", "vivienda", "0", "Casa"),
                habito(usuarioId, 10L, "Energia", "personas", "3", "personas"),
                habito(usuarioId, 10L, "Energia", "fuente", "0", fuente),
                habito(usuarioId, 9L, "Alimentacion", "tipo", "0", tipo),
                habito(usuarioId, 11L, "Residuos", "plasticos", "0", plasticos),
                habito(usuarioId, 11L, "Residuos", "reciclas", recicla ? "1" : "0", "boolean"))));
    }

    private Habito habito(Long usuarioId, Long categoriaId, String categoriaNombre,
                          String campo, String valor, String unidad) {
        CategoriaHabito categoria = new CategoriaHabito();
        categoria.setId(categoriaId);
        categoria.setNombre(categoriaNombre);
        Habito habito = new Habito();
        habito.setId((long) Math.abs(campo.hashCode()));
        habito.setUsuario(usuarios.get(usuarioId));
        habito.setCategoria(categoria);
        habito.setNombre(campo);
        habito.setValor(new BigDecimal(valor));
        habito.setUnidad(unidad);
        habito.setFechaRegistro(fechaHabitos);
        habito.setFechaActualizacion(fechaHabitos);
        return habito;
    }

    private HuellaCarbono huella(Long usuarioId, String transporte, String energia,
                                 String alimentacion, String residuos) {
        HuellaCarbono huella = new HuellaCarbono();
        huella.setId(1L);
        huella.setUsuario(usuarios.get(usuarioId));
        huella.setFechaCalculo(fechaHabitos.plusDays(1));
        huella.setCo2Transporte(new BigDecimal(transporte));
        huella.setCo2Energia(new BigDecimal(energia));
        huella.setCo2Alimentacion(new BigDecimal(alimentacion));
        huella.setCo2Residuos(new BigDecimal(residuos));
        huella.setCo2Total(huella.getCo2Transporte().add(huella.getCo2Energia())
                .add(huella.getCo2Alimentacion()).add(huella.getCo2Residuos()));
        huellas.put(usuarioId, huella);
        return huella;
    }
}
