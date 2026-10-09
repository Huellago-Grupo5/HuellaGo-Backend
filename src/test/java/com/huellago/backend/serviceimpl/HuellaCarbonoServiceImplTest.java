package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.CategoriaHabito;
import com.huellago.backend.entities.Habito;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.HabitoRepository;
import com.huellago.backend.repositories.HuellaCarbonoRepository;
import com.huellago.backend.repositories.UsuarioRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HuellaCarbonoServiceImplTest {

    @Mock
    HuellaCarbonoRepository huellaCarbonoRepository;

    @Mock
    HabitoRepository habitoRepository;

    @Mock
    UsuarioRepository usuarioRepository;

    @InjectMocks
    HuellaCarbonoServiceImpl service;

    @Test
    void noCalculaSiFaltaUnaCategoria() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(habitoRepository.findByUsuario_Id(1L)).thenReturn(List.of(
                habito("Transporte", "medio", BigDecimal.ZERO, "Bus"),
                habito("Transporte", "kmSemana", new BigDecimal("120"), "km/semana"),
                habito("Transporte", "diasSemana", new BigDecimal("5"), "dias/semana")
        ));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.calcularHuellaInicial(1L));

        assertEquals(400, exception.getStatusCode().value());
        verify(huellaCarbonoRepository, never()).save(any());
    }

    @Test
    void calculaCuandoExistenLasCuatroCategorias() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(habitoRepository.findByUsuario_Id(1L)).thenReturn(List.of(
                habito("Transporte", "medio", BigDecimal.ZERO, "Bus"),
                habito("Transporte", "kmSemana", new BigDecimal("120"), "km/semana"),
                habito("Transporte", "diasSemana", new BigDecimal("5"), "dias/semana"),
                habito("Energía", "vivienda", BigDecimal.ZERO, "Casa"),
                habito("Energía", "personas", new BigDecimal("3"), "personas"),
                habito("Energía", "fuente", BigDecimal.ZERO, "Electricidad"),
                habito("Alimentación", "tipo", BigDecimal.ZERO, "Mixta"),
                habito("Residuos", "plasticos", BigDecimal.ZERO, "Medio"),
                habito("Residuos", "reciclas", BigDecimal.ONE, "boolean")
        ));
        when(huellaCarbonoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var resultado = service.calcularHuellaInicial(1L);

        assertEquals(0, resultado.getCo2Total().compareTo(new BigDecimal("10.5")));
        verify(huellaCarbonoRepository).save(any());
    }

    private Habito habito(String categoriaNombre, String nombre, BigDecimal valor, String unidad) {
        Habito habito = new Habito();
        CategoriaHabito categoria = new CategoriaHabito();
        categoria.setNombre(categoriaNombre);
        habito.setCategoria(categoria);
        habito.setNombre(nombre);
        habito.setValor(valor);
        habito.setUnidad(unidad);
        return habito;
    }
}
