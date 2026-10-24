package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.CategoriaHabito;
import com.huellago.backend.entities.Habito;
import com.huellago.backend.entities.HuellaCarbono;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void calculaConCategoriasAlmacenadasSinTilde() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(habitoRepository.findByUsuario_Id(1L)).thenReturn(List.of(
                habito("Transporte", "medio", BigDecimal.ZERO, "Bus"),
                habito("Transporte", "kmSemana", new BigDecimal("120"), "km/semana"),
                habito("Energia", "vivienda", BigDecimal.ZERO, "Casa"),
                habito("Energia", "personas", new BigDecimal("3"), "personas"),
                habito("Energia", "fuente", BigDecimal.ZERO, "Electricidad"),
                habito("Alimentacion", "tipo", BigDecimal.ZERO, "Mixta"),
                habito("Residuos", "plasticos", BigDecimal.ZERO, "Medio"),
                habito("Residuos", "reciclas", BigDecimal.ONE, "boolean")
        ));
        when(huellaCarbonoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var resultado = service.calcularHuellaInicial(1L);

        assertEquals(0, resultado.getCo2Total().compareTo(new BigDecimal("10.5")));
        verify(huellaCarbonoRepository).save(any());
    }

    @Test
    void indicaQueElUsuarioEstaPorDebajoDelPromedio() {
        prepararComparacion(new BigDecimal("10"), List.of(
                new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30")));

        var resultado = service.compararConPromedio(1L);

        assertEquals("POR_DEBAJO_DEL_PROMEDIO", resultado.getPosicion());
        assertEquals(new BigDecimal("20.00"), resultado.getPromedioGeneral());
        assertEquals(new BigDecimal("10.00"), resultado.getDiferenciaAbsoluta());
        assertEquals(new BigDecimal("50.00"), resultado.getDiferenciaPorcentual());
    }

    @Test
    void indicaQueElUsuarioEstaPorEncimaDelPromedio() {
        prepararComparacion(new BigDecimal("30"), List.of(
                new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30")));

        var resultado = service.compararConPromedio(1L);

        assertEquals("POR_ENCIMA_DEL_PROMEDIO", resultado.getPosicion());
        assertEquals(new BigDecimal("10.00"), resultado.getDiferenciaAbsoluta());
        assertEquals(new BigDecimal("50.00"), resultado.getDiferenciaPorcentual());
    }

    @Test
    void indicaQueElUsuarioEstaIgualAlPromedio() {
        prepararComparacion(new BigDecimal("20"), List.of(
                new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30")));

        var resultado = service.compararConPromedio(1L);

        assertEquals("IGUAL_AL_PROMEDIO", resultado.getPosicion());
        assertEquals(new BigDecimal("0.00"), resultado.getDiferenciaAbsoluta());
        assertEquals(new BigDecimal("0.00"), resultado.getDiferenciaPorcentual());
    }

    @Test
    void informaCuandoNoHaySuficientesUsuarios() {
        prepararComparacion(new BigDecimal("10"), List.of(new BigDecimal("10")));

        var resultado = service.compararConPromedio(1L);

        assertFalse(resultado.isComparacionDisponible());
        assertEquals(1L, resultado.getUsuariosConDatos());
        assertNull(resultado.getPromedioGeneral());
        assertTrue(resultado.getMensaje().contains("No hay suficientes usuarios"));
    }

    @Test
    void rechazaUsuarioSinHuellaCalculada() {
        when(usuarioRepository.existsById(1L)).thenReturn(true);
        when(huellaCarbonoRepository.findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(1L))
                .thenReturn(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.compararConPromedio(1L));

        assertEquals(404, exception.getStatusCode().value());
        verify(huellaCarbonoRepository, never()).buscarUltimasHuellasValidasPorUsuario();
    }

    @Test
    void manejaPromedioCeroSinDividirEntreCero() {
        prepararComparacion(BigDecimal.ZERO, List.of(BigDecimal.ZERO, BigDecimal.ZERO));

        var resultado = service.compararConPromedio(1L);

        assertTrue(resultado.isComparacionDisponible());
        assertEquals("IGUAL_AL_PROMEDIO", resultado.getPosicion());
        assertEquals(new BigDecimal("0.00"), resultado.getPromedioGeneral());
        assertEquals(new BigDecimal("0.00"), resultado.getDiferenciaAbsoluta());
        assertNull(resultado.getDiferenciaPorcentual());
    }

    @Test
    void consultaUnaSolaHuellaRecientePorUsuario() throws Exception {
        var metodo = HuellaCarbonoRepository.class
                .getDeclaredMethod("buscarUltimasHuellasValidasPorUsuario");
        var query = metodo.getAnnotation(org.springframework.data.jpa.repository.Query.class);

        assertTrue(query.nativeQuery());
        assertTrue(query.value().contains("DISTINCT ON (usuario_id)"));
        assertTrue(query.value().contains("fecha_calculo DESC NULLS LAST, id DESC"));
    }

    private void prepararComparacion(BigDecimal huellaUsuario, List<BigDecimal> huellasGlobales) {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        HuellaCarbono huella = new HuellaCarbono();
        huella.setUsuario(usuario);
        huella.setCo2Total(huellaUsuario);
        when(usuarioRepository.existsById(1L)).thenReturn(true);
        when(huellaCarbonoRepository.findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(1L))
                .thenReturn(huella);
        when(huellaCarbonoRepository.buscarUltimasHuellasValidasPorUsuario())
                .thenReturn(huellasGlobales);
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
