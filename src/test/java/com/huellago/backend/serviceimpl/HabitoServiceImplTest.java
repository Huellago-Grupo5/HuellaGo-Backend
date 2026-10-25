package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.AlimentacionResiduosDTO;
import com.huellago.backend.dtos.EnergiaDTO;
import com.huellago.backend.dtos.TransporteDTO;
import com.huellago.backend.entities.CategoriaHabito;
import com.huellago.backend.entities.Habito;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.CategoriaHabitoRepository;
import com.huellago.backend.repositories.HabitoRepository;
import com.huellago.backend.repositories.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class HabitoServiceImplTest {
    @Mock org.springframework.context.ApplicationEventPublisher eventos;

    @Mock
    HabitoRepository habitoRepository;

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    CategoriaHabitoRepository categoriaHabitoRepository;

    @InjectMocks
    HabitoServiceImpl service;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(1L);
        lenient().when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
    }

    @Test
    void registraTransporteUnaSolaVez() {
        CategoriaHabito categoria = categoria(1L, "Transporte");
        when(categoriaHabitoRepository.findByNombreIgnoreCase("Transporte")).thenReturn(categoria);
        when(habitoRepository.countByUsuario_IdAndCategoria_NombreIgnoreCase(1L, "Transporte"))
                .thenReturn(0L);
        when(habitoRepository.save(any(Habito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registrarTransporte(new TransporteDTO(1L, "Bus", new BigDecimal("120"), 5));

        verify(habitoRepository, org.mockito.Mockito.times(3)).save(any(Habito.class));
    }

    @Test
    void rechazaTransporteDuplicadoSinGuardarFilas() {
        CategoriaHabito categoria = categoria(1L, "Transporte");
        when(categoriaHabitoRepository.findByNombreIgnoreCase("Transporte")).thenReturn(categoria);
        when(habitoRepository.countByUsuario_IdAndCategoria_NombreIgnoreCase(1L, "Transporte"))
                .thenReturn(3L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                service.registrarTransporte(new TransporteDTO(1L, "Bus", new BigDecimal("120"), 5)));

        assertEquals(409, exception.getStatusCode().value());
        verify(habitoRepository, never()).save(any(Habito.class));
    }

    @Test
    void rechazaEnergiaYAlimentacionSiAlgunaCategoriaYaExiste() {
        CategoriaHabito energia = categoria(5L, "Energía");
        CategoriaHabito alimentacion = categoria(6L, "Alimentación");
        CategoriaHabito residuos = categoria(4L, "Residuos");
        when(categoriaHabitoRepository.findByNombreIgnoreCase("Energía")).thenReturn(energia);
        when(categoriaHabitoRepository.findByNombreIgnoreCase("Alimentación")).thenReturn(alimentacion);
        when(categoriaHabitoRepository.findByNombreIgnoreCase("Residuos")).thenReturn(residuos);
        when(habitoRepository.countByUsuario_IdAndCategoria_NombreIgnoreCase(eq(1L), any(String.class)))
                .thenReturn(0L);
        when(habitoRepository.countByUsuario_IdAndCategoria_NombreIgnoreCase(1L, "Energía"))
                .thenReturn(1L);
        when(habitoRepository.countByUsuario_IdAndCategoria_NombreIgnoreCase(1L, "Alimentación"))
                .thenReturn(1L);

        ResponseStatusException energiaException = assertThrows(ResponseStatusException.class, () ->
                service.registrarEnergia(new EnergiaDTO(1L, "Casa", 3, "Electricidad")));
        ResponseStatusException alimentacionException = assertThrows(ResponseStatusException.class, () ->
                service.registrarAlimentacionResiduos(
                        new AlimentacionResiduosDTO(1L, "Mixta", "Medio", true)));

        assertEquals(409, energiaException.getStatusCode().value());
        assertEquals(409, alimentacionException.getStatusCode().value());
        verify(habitoRepository, never()).save(any(Habito.class));
    }

    @Test
    void registraEnergiaConCategoriaAlmacenadaSinTilde() {
        CategoriaHabito energia = categoria(10L, "Energia");
        when(categoriaHabitoRepository.findByNombreIgnoreCase("Energía")).thenReturn(null);
        when(categoriaHabitoRepository.findAll()).thenReturn(List.of(energia));
        when(habitoRepository.countByUsuario_IdAndCategoria_NombreIgnoreCase(1L, "Energia"))
                .thenReturn(0L);
        when(habitoRepository.save(any(Habito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var resultado = service.registrarEnergia(
                new EnergiaDTO(1L, "Casa", 3, "Electricidad"));

        assertEquals(10L, resultado.getCategoriaId());
        verify(habitoRepository, org.mockito.Mockito.times(3)).save(any(Habito.class));
        verify(eventos).publishEvent(new com.huellago.backend.events.InformacionAmbientalActualizada(1L));
    }

    @Test
    void registraAlimentacionYResiduosConCategoriaAlmacenadaSinTilde() {
        CategoriaHabito alimentacion = categoria(9L, "Alimentacion");
        CategoriaHabito residuos = categoria(11L, "Residuos");
        when(categoriaHabitoRepository.findByNombreIgnoreCase("Alimentación")).thenReturn(null);
        when(categoriaHabitoRepository.findByNombreIgnoreCase("Residuos")).thenReturn(residuos);
        when(categoriaHabitoRepository.findAll()).thenReturn(List.of(alimentacion, residuos));
        when(habitoRepository.countByUsuario_IdAndCategoria_NombreIgnoreCase(eq(1L), any(String.class)))
                .thenReturn(0L);
        when(habitoRepository.save(any(Habito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var resultado = service.registrarAlimentacionResiduos(
                new AlimentacionResiduosDTO(1L, "Mixta", "Medio", true));

        assertEquals(9L, resultado.getCategoriaAlimentacionId());
        assertEquals(11L, resultado.getCategoriaResiduosId());
        verify(habitoRepository, org.mockito.Mockito.times(3)).save(any(Habito.class));
    }

    @Test
    void actualizaSoloElHabitoDelUsuarioAutenticado() {
        Habito habito = new Habito();
        habito.setId(10L);
        habito.setUsuario(usuario);
        habito.setCategoria(categoria(1L, "Transporte"));
        habito.setNombre("kmSemana");
        habito.setValor(new BigDecimal("120"));
        habito.setUnidad("km/semana");
        when(habitoRepository.findById(10L)).thenReturn(Optional.of(habito));
        when(habitoRepository.save(any(Habito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.actualizar(10L, usuario,
                new com.huellago.backend.dtos.ActualizarHabitoDTO(
                        "kmSemana", new BigDecimal("150"), "km/semana"));

        assertEquals(new BigDecimal("150"), habito.getValor());
        verify(habitoRepository).save(habito);
        verify(eventos).publishEvent(new com.huellago.backend.events.InformacionAmbientalActualizada(1L));
    }

    @Test
    void rechazaActualizarHabitoDeOtroUsuario() {
        Usuario propietario = new Usuario();
        propietario.setId(2L);
        Habito habito = new Habito();
        habito.setId(10L);
        habito.setUsuario(propietario);
        habito.setCategoria(categoria(1L, "Transporte"));
        when(habitoRepository.findById(10L)).thenReturn(Optional.of(habito));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                service.actualizar(10L, usuario,
                        new com.huellago.backend.dtos.ActualizarHabitoDTO(
                                "kmSemana", new BigDecimal("150"), "km/semana")));

        assertEquals(403, exception.getStatusCode().value());
        verify(habitoRepository, never()).save(any(Habito.class));
    }

    @Test
    void actualizaCamposTextualesDeLasCuatroCategoriasSinInsertarFilas() {
        String[][] casos = {
                {"Transporte", "medio", "Auto", "Bus"},
                {"Energia", "fuente", "Gas", "Solar"},
                {"Alimentacion", "tipo", "Mixta", "Vegana"},
                {"Residuos", "plasticos", "Alto", "Bajo"}
        };
        for (int i = 0; i < casos.length; i++) {
            Habito habito = habito(10L + i, casos[i][0], casos[i][1], BigDecimal.ZERO, casos[i][2]);
            when(habitoRepository.findById(10L + i)).thenReturn(Optional.of(habito));
            when(habitoRepository.save(habito)).thenReturn(habito);
            var respuesta = service.actualizar(10L + i, usuario,
                    new com.huellago.backend.dtos.ActualizarHabitoDTO(casos[i][1], BigDecimal.ZERO, casos[i][3]));
            assertEquals(casos[i][3], respuesta.getUnidad());
            assertEquals(casos[i][1], respuesta.getNombre());
            assertEquals(10L + i, respuesta.getId());
            assertEquals(1L, respuesta.getUsuarioId());
            assertTrue(respuesta.getFechaActualizacion() != null);
            verify(habitoRepository).save(habito);
        }
    }

    @Test
    void actualizaNumerosCompatiblesConRecalculoYHu31() {
        String[][] casos = {
                {"Transporte", "kmSemana", "km/semana", "35.5"},
                {"Transporte", "diasSemana", "dias/semana", "6"},
                {"Energia", "personas", "personas", "3"},
                {"Residuos", "reciclas", "boolean", "1"}
        };
        for (int i = 0; i < casos.length; i++) {
            Habito habito = habito(20L + i, casos[i][0], casos[i][1],
                    "reciclas".equals(casos[i][1]) ? BigDecimal.ZERO : BigDecimal.ONE, casos[i][2]);
            when(habitoRepository.findById(20L + i)).thenReturn(Optional.of(habito));
            when(habitoRepository.save(habito)).thenReturn(habito);
            var respuesta = service.actualizar(20L + i, usuario,
                    new com.huellago.backend.dtos.ActualizarHabitoDTO(
                            casos[i][1], new BigDecimal(casos[i][3]), casos[i][2]));
            assertEquals(new BigDecimal(casos[i][3]), respuesta.getValor());
            verify(habitoRepository).save(habito);
        }
    }

    @Test
    void rechazaNulosYCamposOmitidos() {
        for (var dto : List.of(
                new com.huellago.backend.dtos.ActualizarHabitoDTO(null, BigDecimal.ZERO, "Auto"),
                new com.huellago.backend.dtos.ActualizarHabitoDTO("medio", null, "Auto"),
                new com.huellago.backend.dtos.ActualizarHabitoDTO("medio", BigDecimal.ZERO, null))) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.actualizar(30L, usuario, dto)).getStatusCode().value());
        }
        verify(habitoRepository, never()).save(any(Habito.class));
    }

    @Test
    void rechazaRenombrarHabitoYValoresInvalidosSinDuplicar() {
        Habito habito = habito(40L, "Transporte", "medio", BigDecimal.ZERO, "Bus");
        when(habitoRepository.findById(40L)).thenReturn(Optional.of(habito));
        var invalidos = List.of(
                new com.huellago.backend.dtos.ActualizarHabitoDTO("kmSemana", BigDecimal.ONE, "km/semana"),
                new com.huellago.backend.dtos.ActualizarHabitoDTO("medio", BigDecimal.ONE, "Auto"),
                new com.huellago.backend.dtos.ActualizarHabitoDTO("medio", BigDecimal.ZERO, "Avion"));
        for (var dto : invalidos) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.actualizar(40L, usuario, dto)).getStatusCode().value());
        }
        assertEquals("medio", habito.getNombre());
        verify(habitoRepository, never()).save(any(Habito.class));
    }

    @Test
    void rechazaUnidadYEnterosInvalidos() {
        Habito habito = habito(50L, "Transporte", "diasSemana", BigDecimal.ONE, "dias/semana");
        when(habitoRepository.findById(50L)).thenReturn(Optional.of(habito));
        for (var dto : List.of(
                new com.huellago.backend.dtos.ActualizarHabitoDTO("diasSemana", new BigDecimal("1.5"), "dias/semana"),
                new com.huellago.backend.dtos.ActualizarHabitoDTO("diasSemana", new BigDecimal("8"), "dias/semana"),
                new com.huellago.backend.dtos.ActualizarHabitoDTO("diasSemana", BigDecimal.ONE, "personas"))) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.actualizar(50L, usuario, dto)).getStatusCode().value());
        }
        verify(habitoRepository, never()).save(any(Habito.class));
    }

    @Test
    void devuelve404CuandoNoExiste() {
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.actualizar(999L, usuario,
                        new com.huellago.backend.dtos.ActualizarHabitoDTO("medio", BigDecimal.ZERO, "Bus")))
                .getStatusCode().value());
    }

    @Test
    void repetirDatosSinVariacionesNoInvalidaHuellaNiPublicaRefresco() {
        Habito habito = habito(70L, "Transporte", "kmSemana", new BigDecimal("100"), "km/semana");
        var fecha = java.time.LocalDateTime.of(2026, 1, 1, 10, 0);
        habito.setFechaActualizacion(fecha);
        when(habitoRepository.findById(70L)).thenReturn(Optional.of(habito));
        var respuesta = service.actualizar(70L, usuario,
                new com.huellago.backend.dtos.ActualizarHabitoDTO("kmSemana", new BigDecimal("100.00"), "km/semana"));
        assertEquals(fecha, respuesta.getFechaActualizacion());
        verify(habitoRepository, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(eventos);
    }

    private Habito habito(Long id, String categoria, String nombre, BigDecimal valor, String unidad) {
        Habito habito = new Habito();
        habito.setId(id);
        habito.setUsuario(usuario);
        habito.setCategoria(categoria(id, categoria));
        habito.setNombre(nombre);
        habito.setValor(valor);
        habito.setUnidad(unidad);
        habito.setFechaRegistro(java.time.LocalDateTime.now().minusDays(1));
        return habito;
    }

    private CategoriaHabito categoria(Long id, String nombre) {
        CategoriaHabito categoria = new CategoriaHabito();
        categoria.setId(id);
        categoria.setNombre(nombre);
        return categoria;
    }
}
