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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class HabitoServiceImplTest {

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

    private CategoriaHabito categoria(Long id, String nombre) {
        CategoriaHabito categoria = new CategoriaHabito();
        categoria.setId(id);
        categoria.setNombre(nombre);
        return categoria;
    }
}
