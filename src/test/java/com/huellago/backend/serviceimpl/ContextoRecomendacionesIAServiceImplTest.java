package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.HabitoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Repositorio simulado: no contexto Spring, DataSource, HTTP ni inferencia. */
class ContextoRecomendacionesIAServiceImplTest {
    private final HabitoRepository repositorio = mock(HabitoRepository.class);
    private final ContextoRecomendacionesIAServiceImpl servicio = new ContextoRecomendacionesIAServiceImpl(repositorio);
    private final List<Habito> datos = new ArrayList<>();
    private final LocalDateTime fecha = LocalDateTime.of(2026, 11, 8, 12, 0);

    @BeforeEach void preparar() {
        agregar("Transporte", "medio", "0", "Bus");
        agregar("Transporte", "kmSemana", "80.50", "km/semana");
        agregar("Transporte", "diasSemana", "5", "dias/semana");
        agregar("Energía", "vivienda", "0", "Casa");
        agregar("Energía", "personas", "3", "personas");
        agregar("Energía", "fuente", "0", "Electricidad");
        agregar("Energía", "consumoKwhDiario", "12.345", "kWh/dia");
        agregar("Alimentación", "tipo", "0", "Mixta");
        agregar("Residuos", "plasticos", "0", "Bajo");
        agregar("Residuos", "reciclas", "1", "boolean");
        when(repositorio.findByUsuario_Id(68L)).thenReturn(datos);
    }

    @Test void cuatroCategoriasCompatiblesSinTransformarConsumoDelHogar() {
        var resultado = servicio.construir(68L);
        var c = resultado.contexto();
        assertTrue(resultado.categoriasIncompletas().isEmpty());
        assertTrue(resultado.observaciones().isEmpty());
        assertEquals("Bus", c.transporte().medio());
        assertEquals(new BigDecimal("80.50"), c.transporte().kmSemana());
        assertEquals(5, c.transporte().diasSemana());
        assertEquals("Casa", c.energia().vivienda());
        assertEquals(3, c.energia().personas());
        assertEquals("Electricidad", c.energia().fuente());
        assertEquals(new BigDecimal("12.345"), c.energia().consumoKwhDiario());
        assertEquals("Mixta", c.alimentacion().tipo());
        assertEquals("Bajo", c.residuos().plasticos());
        assertTrue(c.residuos().reciclas());
        assertEquals(4, ValidadorRecomendacionesIA.contexto(c).size());
        assertTrue(resultado.reglasInterpretacion().stream().anyMatch(s -> s.contains("no por persona")));
        verify(repositorio).findByUsuario_Id(68L);
        verifyNoMoreInteractions(repositorio);
    }

    @Test void categoriasParcialesNoInventanDatos() {
        datos.removeIf(h -> !h.getCategoria().getNombre().equals("Residuos"));
        var r = servicio.construir(68L);
        assertEquals(List.of("Transporte", "Energía", "Alimentación"), r.categoriasIncompletas());
        assertNull(r.contexto().transporte()); assertNull(r.contexto().energia());
        assertNull(r.contexto().alimentacion()); assertNotNull(r.contexto().residuos());
        assertEquals(1, ValidadorRecomendacionesIA.contexto(r.contexto()).size());
    }

    @Test void sinHabitosTodasIncompletasSinLlamarGenerador() {
        datos.clear();
        var r = servicio.construir(68L);
        assertEquals(4, r.categoriasIncompletas().size());
        assertNull(r.contexto().transporte()); assertNull(r.contexto().energia());
        assertNull(r.contexto().alimentacion()); assertNull(r.contexto().residuos());
    }

    @Test void consumoOpcionalAusenteNoInvalidaEnergia() {
        datos.removeIf(h -> h.getNombre().equals("consumoKwhDiario"));
        var r = servicio.construir(68L);
        assertTrue(r.categoriasIncompletas().isEmpty());
        assertNull(r.contexto().energia().consumoKwhDiario());
        assertEquals(4, ValidadorRecomendacionesIA.contexto(r.contexto()).size());
    }

    @Test void consumoCeroEsDatoRealYReciclajeCeroEsFalse() {
        campo("consumoKwhDiario").setValor(BigDecimal.ZERO);
        campo("reciclas").setValor(BigDecimal.ZERO);
        var c = servicio.construir(68L).contexto();
        assertEquals(BigDecimal.ZERO, c.energia().consumoKwhDiario());
        assertFalse(c.residuos().reciclas());
    }

    @Test void gasSinConsumoEsCompatiblePeroConConsumoEsIncompleto() {
        campo("fuente").setUnidad("Gas");
        assertNull(servicio.construir(68L).contexto().energia());
        datos.remove(campo("consumoKwhDiario"));
        assertEquals("Gas", servicio.construir(68L).contexto().energia().fuente());
    }

    @ParameterizedTest
    @ValueSource(strings = {"medio", "kmSemana", "diasSemana", "vivienda", "personas", "fuente", "tipo", "plasticos", "reciclas"})
    void obligatorioAusenteSeIdentifica(String nombre) {
        String categoria = campo(nombre).getCategoria().getNombre();
        datos.remove(campo(nombre));
        var r = servicio.construir(68L);
        assertEquals(List.of(categoria), r.categoriasIncompletas());
        assertTrue(r.observaciones().stream().anyMatch(s -> s.contains("." + nombre + ": dato obligatorio ausente")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"medio", "kmSemana", "diasSemana", "vivienda", "personas", "fuente", "tipo", "plasticos", "reciclas", "consumoKwhDiario"})
    void valorNuloNoSeSustituyePorCero(String nombre) {
        campo(nombre).setValor(null);
        var r = servicio.construir(68L);
        assertEquals(1, r.categoriasIncompletas().size());
        assertTrue(r.observaciones().stream().anyMatch(s -> s.contains("." + nombre + ": valor o unidad inválidos")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "1.5", "2147483648"})
    void personasInvalidasIncluyendoDesbordamiento(String valor) {
        campo("personas").setValor(new BigDecimal(valor));
        assertNull(servicio.construir(68L).contexto().energia());
    }

    @Test void valoresFueraDeRangoYUnidadesInvalidasSeRechazan() {
        campo("diasSemana").setValor(new BigDecimal("8"));
        campo("consumoKwhDiario").setValor(new BigDecimal("-1"));
        campo("tipo").setUnidad("Desconocida");
        campo("reciclas").setValor(new BigDecimal("2"));
        assertEquals(4, servicio.construir(68L).categoriasIncompletas().size());
        campo("kmSemana").setUnidad("km/dia");
        assertNull(servicio.construir(68L).contexto().transporte());
    }

    @Test void normalizacionReutilizadaSinModificarEntidades() {
        for (Habito h : datos) {
            h.getCategoria().setNombre(" " + h.getCategoria().getNombre().toUpperCase(Locale.ROOT) + " ");
            h.setNombre(" " + h.getNombre().toUpperCase(Locale.ROOT) + " ");
            h.setUnidad(" " + h.getUnidad().toUpperCase(Locale.ROOT) + " ");
        }
        datos.get(6).setUnidad(" kWh/día ");
        var c = servicio.construir(68L).contexto();
        assertEquals(4, ValidadorRecomendacionesIA.contexto(c).size());
        assertEquals("Bus", c.transporte().medio());
        assertEquals(" kWh/día ", datos.get(6).getUnidad());
        assertEquals(" MEDIO ", datos.getFirst().getNombre());
    }

    @Test void duplicadosUsanMayorFechaEntreRegistroYActualizacionLuegoId() {
        Habito viejo = campo("medio");
        viejo.setFechaActualizacion(fecha.plusDays(2));
        Habito nuevo = agregar("Transporte", "medio", "0", "Auto");
        nuevo.setFechaRegistro(fecha.plusDays(1));
        assertEquals("Bus", servicio.construir(68L).contexto().transporte().medio());
        nuevo.setFechaRegistro(fecha.plusDays(2));
        assertEquals("Auto", servicio.construir(68L).contexto().transporte().medio());
        Collections.reverse(datos);
        assertEquals("Auto", servicio.construir(68L).contexto().transporte().medio());
    }

    @Test void fechasEIdsNulosSonMenoresQueLosConocidos() {
        Habito duplicado = agregar("Transporte", "medio", "0", "Auto");
        duplicado.setFechaRegistro(null); duplicado.setFechaActualizacion(null); duplicado.setId(null);
        assertEquals("Bus", servicio.construir(68L).contexto().transporte().medio());
        duplicado.setFechaRegistro(fecha);
        assertEquals("Bus", servicio.construir(68L).contexto().transporte().medio());
    }

    @Test void ultimoInvalidoNoRecuperaSilenciosamenteDatoAntiguoValido() {
        Habito nuevo = agregar("Transporte", "diasSemana", "8", "dias/semana");
        nuevo.setFechaActualizacion(fecha.plusMinutes(1));
        assertNull(servicio.construir(68L).contexto().transporte());
    }

    @Test void aislamientoInclusoSiRepositorioDevuelveRegistroAjeno() {
        Habito ajeno = agregar("Transporte", "medio", "0", "Auto");
        ajeno.getUsuario().setId(999L); ajeno.setFechaActualizacion(fecha.plusYears(1));
        assertEquals("Bus", servicio.construir(68L).contexto().transporte().medio());
        when(repositorio.findByUsuario_Id(999L)).thenReturn(List.of(ajeno));
        var r = servicio.construir(999L);
        assertNull(r.contexto().transporte()); // No completar con datos del usuario 68.
        assertEquals(4, r.categoriasIncompletas().size());
    }

    @Test void noIncluyeIdentidadNiEntidadesEnResultado() {
        Usuario usuario = datos.getFirst().getUsuario();
        usuario.setCorreo("privado@example.invalid");
        String json = JsonMapper.builder().build().writeValueAsString(servicio.construir(68L));
        assertFalse(json.contains("privado@example.invalid"));
        for (String campo : List.of("usuarioId", "correo", "password", "jwt", "fechaRegistro", "categoria_id"))
            assertFalse(json.contains("\"" + campo + "\""));
        assertThrows(UnsupportedOperationException.class,
                () -> servicio.construir(68L).reglasInterpretacion().clear());
    }

    @Test void registrosSinCategoriaNombreOPropietarioNoGeneranValores() {
        datos.add(null);
        Habito incompleto = new Habito(); datos.add(incompleto);
        incompleto.setUsuario(datos.getFirst().getUsuario());
        incompleto.setCategoria(new CategoriaHabito());
        assertTrue(servicio.construir(68L).categoriasIncompletas().isEmpty());
    }

    @ParameterizedTest @NullSource @ValueSource(longs = {0, -1})
    void idInvalidoNoConsultaRepositorio(Long id) {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> servicio.construir(id)).getStatusCode().value());
        verifyNoInteractions(repositorio);
    }

    @Test void infraestructuraNoSeConvierteEnCategoriaIncompleta() {
        var error = new DataAccessResourceFailureException("Fallo simulado");
        when(repositorio.findByUsuario_Id(68L)).thenThrow(error);
        assertSame(error, assertThrows(DataAccessResourceFailureException.class, () -> servicio.construir(68L)));
    }

    private Habito campo(String nombre) {
        return datos.stream().filter(Objects::nonNull).filter(h -> nombre.equals(h.getNombre())).findFirst().orElseThrow();
    }

    private Habito agregar(String categoria, String nombre, String valor, String unidad) {
        Usuario usuario = new Usuario(); usuario.setId(68L);
        CategoriaHabito c = new CategoriaHabito(); c.setNombre(categoria);
        Habito h = new Habito(); h.setId((long) datos.size() + 1); h.setUsuario(usuario); h.setCategoria(c);
        h.setNombre(nombre); h.setValor(new BigDecimal(valor)); h.setUnidad(unidad);
        h.setFechaRegistro(fecha); h.setFechaActualizacion(fecha); datos.add(h);
        return h;
    }
}
