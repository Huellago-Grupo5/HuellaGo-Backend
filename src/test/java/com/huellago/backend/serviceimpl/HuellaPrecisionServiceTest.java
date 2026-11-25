package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.HuellaCarbonoRespuestaDTO;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Factores y umbrales de fixtures prueban aritmética, no acreditan metodología científica. */
@ExtendWith(MockitoExtension.class)
class HuellaPrecisionServiceTest {
    @Mock UsuarioRepository usuarios;
    @Mock HabitoRepository habitos;
    @Mock HuellaCarbonoRepository huellas;
    @Mock ApplicationEventPublisher eventos;
    @Spy PoliticaMedicionInicial politica = new PoliticaMedicionInicial();
    @InjectMocks HuellaCarbonoServiceImpl servicio;
    final List<Habito> datos = new ArrayList<>();
    final List<HuellaCarbono> guardadas = new ArrayList<>();
    Usuario usuario;

    @BeforeEach void preparar() {
        usuario = new Usuario(); usuario.setId(68L);
        lenient().when(usuarios.findById(68L)).thenReturn(Optional.of(usuario));
        lenient().when(usuarios.bloquearParaRecomendaciones(68L)).thenReturn(Optional.of(68L));
        lenient().when(habitos.findByUsuario_Id(68L)).thenReturn(datos);
        lenient().when(huellas.save(any())).thenAnswer(i -> {
            HuellaCarbono h = i.getArgument(0); h.setId((long) guardadas.size() + 1); guardadas.add(h); return h;
        });
        politica.factorElectricidad = "0.23825";
        politica.referenciaElectricidad = "Factor provisional exclusivo de pruebas, sin aprobación científica";
        agregar("Transporte", "medio", "0", "Bus");
        agregar("Transporte", "kmSemana", "80", "km/semana");
        agregar("Transporte", "diasSemana", "5", "dias/semana");
        agregar("Energia", "vivienda", "0", "Casa");
        agregar("Energia", "personas", "3", "personas");
        agregar("Energia", "fuente", "0", "Electricidad");
        agregar("Energia", "consumoKwhDiario", "12", "kWh/dia");
        agregar("Alimentacion", "tipo", "0", "Mixta");
        agregar("Residuos", "plasticos", "0", "Medio");
        agregar("Residuos", "reciclas", "1", "boolean");
    }

    @Test void casosDeReferenciaInicialYRecalculoSonCanonicosYSinAlterarCalculoAnterior() {
        var inicial = servicio.calcularHuellaInicial(68L);
        comprobarCanonico(inicial, guardadas.getFirst());
        assertEquals(new BigDecimal("20.01"), inicial.getCo2Energia());
        assertEquals(new BigDecimal("26.71"), inicial.getCo2Total());
        campo("consumoKwhDiario").setValor(new BigDecimal("20"));

        var recalculada = servicio.recalcularHuella(68L);
        comprobarCanonico(recalculada, guardadas.getLast());
        assertEquals(new BigDecimal("33.36"), recalculada.getCo2Energia());
        assertEquals(new BigDecimal("40.06"), recalculada.getCo2Total());
        assertEquals(new BigDecimal("26.71"), guardadas.getFirst().getCo2Total());
        assertEquals("0.23825", politica.factorElectricidad);
        assertEquals("BAJO", inicial.getNivelImpacto());
        assertEquals("MEDIO", recalculada.getNivelImpacto());
        verify(huellas, times(2)).save(any());
    }

    @ParameterizedTest @CsvSource({"0.098,0.00", "0.100,0.01", "0.102,0.01"})
    void transporteRedondeaDebajoEnYEncimaDeMediaCentesima(String km, String esperado) {
        campo("kmSemana").setValor(new BigDecimal(km));
        var r = servicio.calcularHuellaInicial(68L);
        assertEquals(new BigDecimal(esperado), r.getCo2Transporte());
        comprobarCanonico(r, guardadas.getFirst());
    }

    @ParameterizedTest @CsvSource({"0.999,0.03", "1,0.04", "1.001,0.04"})
    void energiaRedondeaDebajoEnYEncimaDeMediaCentesima(String consumo, String esperado) {
        politica.factorElectricidad = "0.005"; // Exclusivo de esta prueba: valores brutos 0.034965/0.035/0.035035.
        campo("consumoKwhDiario").setValor(new BigDecimal(consumo));
        var r = servicio.calcularHuellaInicial(68L);
        assertEquals(new BigDecimal(esperado), r.getCo2Energia());
        comprobarCanonico(r, guardadas.getFirst());
    }

    @Test void totalSumaComponentesRedondeadosEnVezDeRedondearLaSumaBruta() {
        campo("kmSemana").setValor(new BigDecimal("0.1")); // 0.005 -> 0.01
        politica.factorElectricidad = "0.005";
        campo("consumoKwhDiario").setValor(BigDecimal.ONE); // 0.035 -> 0.04
        var r = servicio.calcularHuellaInicial(68L);
        // Bruto: 0.005 + 0.035 + 2 + 0.7 = 2.740; componentes redondeados suman 2.75.
        assertEquals(new BigDecimal("2.75"), r.getCo2Total());
        assertNotEquals(new BigDecimal("2.74"), r.getCo2Total());
        comprobarCanonico(r, guardadas.getFirst());
    }

    @Test void ceroYEscalasDistintasSeNormalizanSinModificarHabitos() {
        campo("medio").setUnidad("Bicicleta");
        campo("kmSemana").setValor(new BigDecimal("80.00000"));
        campo("consumoKwhDiario").setValor(new BigDecimal("0.0000"));
        campo("tipo").setUnidad("Vegana");
        campo("plasticos").setUnidad("Alto");
        var r = servicio.calcularHuellaInicial(68L);
        assertEquals(new BigDecimal("0.00"), r.getCo2Transporte());
        assertEquals(new BigDecimal("0.00"), r.getCo2Energia());
        assertEquals(new BigDecimal("0.60"), r.getCo2Alimentacion());
        assertEquals(new BigDecimal("1.26"), r.getCo2Residuos());
        assertEquals(new BigDecimal("1.86"), r.getCo2Total());
        assertEquals(new BigDecimal("80.00000"), campo("kmSemana").getValor());
        assertEquals(new BigDecimal("0.0000"), campo("consumoKwhDiario").getValor());
        comprobarCanonico(r, guardadas.getFirst());
        verify(habitos, never()).save(any());
    }

    @Test void energiaEstimadaTambienRedondeaSoloElResultadoFinal() {
        datos.remove(campo("consumoKwhDiario"));
        campo("personas").setValor(new BigDecimal("2"));
        campo("fuente").setUnidad("Gas"); // 1.2 * 0.85 * 1.25 = 1.275 -> 1.28
        var r = servicio.calcularHuellaInicial(68L);
        assertEquals(new BigDecimal("1.28"), r.getCo2Energia());
        comprobarCanonico(r, guardadas.getFirst());
    }

    @Test void clasificaConElMismoTotalCanonicoQueSeGuardaYDevuelve() {
        politica.limiteBajo = "26.71";
        politica.limiteMedio = "40.06";
        politica.referencia = "Umbrales ficticios exclusivos de pruebas, sin validez científica";
        var inicial = servicio.calcularHuellaInicial(68L);
        assertEquals("MEDIO", inicial.getNivelImpacto()); // Se clasifica el total canónico de 26.71.
        campo("consumoKwhDiario").setValor(new BigDecimal("20"));
        var recalculada = servicio.recalcularHuella(68L);
        assertEquals("ALTO", recalculada.getNivelImpacto());
        verify(politica, times(2)).nivel(new BigDecimal("26.71"));
        verify(politica, times(2)).nivel(new BigDecimal("40.06"));
    }

    @Test void lecturasHistoricasNoNormalizanNiReconstruyenImportesNulosOInconsistentes() {
        HuellaCarbono historica = new HuellaCarbono(); historica.setId(5L); historica.setUsuario(usuario);
        historica.setFechaCalculo(LocalDateTime.of(2026, 11, 7, 10, 0));
        historica.setCo2Total(new BigDecimal("17.07"));
        historica.setCo2Transporte(new BigDecimal("12.000"));
        historica.setCo2Energia(new BigDecimal("1.28"));
        historica.setCo2Alimentacion(null); historica.setCo2Residuos(new BigDecimal("1.8"));
        when(huellas.findByUsuario_IdOrderByFechaCalculoDescIdDesc(68L)).thenReturn(List.of(historica));
        when(huellas.findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(68L)).thenReturn(historica);
        when(usuarios.existsById(68L)).thenReturn(true);

        var lectura = servicio.obtenerHistorial(68L).getFirst();
        assertEquals(new BigDecimal("17.07"), lectura.getCo2Total());
        assertEquals(new BigDecimal("12.000"), lectura.getCo2Transporte());
        assertNull(lectura.getCo2Alimentacion());
        var desglose = servicio.obtenerDesglose(68L);
        assertFalse(desglose.isCompleto());
        assertEquals(new BigDecimal("17.07"), desglose.getCo2Total());
        historica.setCo2Total(null);
        assertNull(servicio.obtenerHistorial(68L).getFirst().getCo2Total());
        assertEquals("NO_DEFINIDO", servicio.obtenerHistorial(68L).getFirst().getNivelImpacto());
        assertEquals(new BigDecimal("12.000"), historica.getCo2Transporte());
        assertNull(historica.getCo2Alimentacion());
        verify(huellas, never()).save(any());
    }

    private void comprobarCanonico(HuellaCarbonoRespuestaDTO r, HuellaCarbono h) {
        assertEquals(h.getCo2Total(), r.getCo2Total());
        assertEquals(h.getCo2Transporte(), r.getCo2Transporte());
        assertEquals(h.getCo2Energia(), r.getCo2Energia());
        assertEquals(h.getCo2Alimentacion(), r.getCo2Alimentacion());
        assertEquals(h.getCo2Residuos(), r.getCo2Residuos());
        for (BigDecimal valor : List.of(r.getCo2Total(), r.getCo2Transporte(), r.getCo2Energia(),
                r.getCo2Alimentacion(), r.getCo2Residuos())) assertEquals(2, valor.scale());
        assertEquals(r.getCo2Total(), r.getCo2Transporte().add(r.getCo2Energia())
                .add(r.getCo2Alimentacion()).add(r.getCo2Residuos()));
        assertEquals("semanal", r.getPeriodo());
    }
    private Habito campo(String nombre) {
        return datos.stream().filter(h -> nombre.equals(h.getNombre())).findFirst().orElseThrow();
    }
    private void agregar(String categoria, String nombre, String valor, String unidad) {
        var c = new CategoriaHabito(); c.setNombre(categoria);
        var h = new Habito(); h.setCategoria(c); h.setUsuario(usuario); h.setNombre(nombre);
        h.setValor(new BigDecimal(valor)); h.setUnidad(unidad); datos.add(h);
    }
}
