package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Habito;
import com.huellago.backend.entities.CategoriaHabito;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.HabitoRepository;
import com.huellago.backend.services.ContextoRecomendacionesIAService.Resultado;
import com.huellago.backend.dtos.RecomendacionGeneradaIADTO;
import com.huellago.backend.services.GeneradorRecomendacionesIAService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** HTTP real opt-in; constructor real con repositorio simulado, sin Spring ni PostgreSQL. */
@EnabledIfEnvironmentVariable(named = "HUELLAGO_OLLAMA_REAL_TEST", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class OllamaRecomendacionesRealIntegrationTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private GeneradorRecomendacionesIAService cliente;
    private boolean primeraInferenciaCorrecta;

    @BeforeAll
    void crearClienteSinContextoSpring() {
        var configuracion = new OllamaConfiguration();
        var ajustes = new OllamaConfiguration.Ajustes(URI.create("http://localhost:11434"),
                "qwen3:4b-instruct", Duration.ofSeconds(3), Duration.ofSeconds(120), 65536);
        cliente = configuracion.generadorRecomendacionesIAService(
                configuracion.ollamaRestClient(ajustes), ajustes);
    }

    @Test
    @Order(1)
    void inferenciaRealConLasCuatroCategorias() {
        var datos = new ArrayList<Habito>();
        agregar(datos, "Transporte", "medio", "0", "Bus");
        agregar(datos, "Transporte", "kmSemana", "80", "km/semana");
        agregar(datos, "Transporte", "diasSemana", "5", "dias/semana");
        agregar(datos, "Energía", "vivienda", "0", "Casa");
        agregar(datos, "Energía", "personas", "3", "personas");
        agregar(datos, "Energía", "fuente", "0", "Electricidad");
        agregar(datos, "Energía", "consumoKwhDiario", "12.345", "kWh/dia");
        agregar(datos, "Alimentación", "tipo", "0", "Mixta");
        agregar(datos, "Residuos", "plasticos", "0", "Medio");
        agregar(datos, "Residuos", "reciclas", "0", "boolean");
        var contexto = construir(datos);
        assertTrue(contexto.categoriasIncompletas().isEmpty());
        assertEquals(new BigDecimal("12.345"), contexto.contexto().energia().consumoKwhDiario());
        assertFalse(contexto.contexto().residuos().reciclas());
        generarYVerificar("CUATRO_CATEGORIAS", contexto,
                Set.of("Transporte", "Energía", "Alimentación", "Residuos"));
        primeraInferenciaCorrecta = true;
    }

    @Test
    @Order(2)
    void inferenciaRealConCategoriasParcialesSoloSiLaPrimeraFunciona() {
        Assumptions.assumeTrue(primeraInferenciaCorrecta,
                "No se realiza una segunda inferencia si falla la primera");
        var datos = new ArrayList<Habito>();
        agregar(datos, "Transporte", "medio", "0", "Bicicleta");
        agregar(datos, "Transporte", "kmSemana", "20", "km/semana");
        agregar(datos, "Transporte", "diasSemana", "4", "dias/semana");
        agregar(datos, "Residuos", "plasticos", "0", "Bajo");
        agregar(datos, "Residuos", "reciclas", "1", "boolean");
        var contexto = construir(datos);
        assertEquals(List.of("Energía", "Alimentación"), contexto.categoriasIncompletas());
        assertNull(contexto.contexto().energia()); assertNull(contexto.contexto().alimentacion());
        assertTrue(contexto.contexto().residuos().reciclas());
        generarYVerificar("CATEGORIAS_PARCIALES", contexto, Set.of("Transporte", "Residuos"));
    }

    private void generarYVerificar(String caso, Resultado contexto,
                                  Set<String> categoriasEsperadas) {
        System.out.println("CASO=" + caso + " POST http://localhost:11434/api/chat modelo=qwen3:4b-instruct");
        assertFalse(contexto.reglasInterpretacion().isEmpty());
        System.out.println("CONTEXTO_FICTICIO=" + mapper.writeValueAsString(contexto.contexto()));
        System.out.println("REGLAS_INTERPRETACION=" + mapper.writeValueAsString(contexto.reglasInterpretacion()));
        long inicio = System.nanoTime();
        List<RecomendacionGeneradaIADTO> resultado;
        try {
            resultado = cliente.generarDesdeContexto(contexto);
        } finally {
            System.out.println("DURACION_" + caso + "_MS="
                    + Duration.ofNanos(System.nanoTime() - inicio).toMillis());
        }
        System.out.println("RESULTADO_REAL_" + caso + "="
                + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado));
        assertNotNull(resultado);
        assertEquals(categoriasEsperadas.size(), resultado.size());
        var categoriasRecibidas = resultado.stream().map(RecomendacionGeneradaIADTO::categoria)
                .collect(Collectors.toSet());
        assertEquals(resultado.size(), categoriasRecibidas.size(), "No debe haber categorías duplicadas");
        assertEquals(categoriasEsperadas, categoriasRecibidas, "Sin categorías desconocidas ni ausentes");
        for (var recomendacion : resultado) {
            assertAll(recomendacion.categoria(),
                    () -> assertNotNull(recomendacion.titulo()),
                    () -> assertFalse(recomendacion.titulo().isBlank()),
                    () -> assertNotNull(recomendacion.descripcion()),
                    () -> assertFalse(recomendacion.descripcion().isBlank()),
                    () -> assertNotNull(recomendacion.motivo()),
                    () -> assertFalse(recomendacion.motivo().isBlank()));
        }
        // La validez estructural no certifica pertinencia ni metodología científica del contenido.
    }

    private Resultado construir(List<Habito> datos) {
        HabitoRepository repositorio = mock(HabitoRepository.class);
        when(repositorio.findByUsuario_Id(68L)).thenReturn(datos);
        Resultado resultado = new ContextoRecomendacionesIAServiceImpl(repositorio).construir(68L);
        verify(repositorio).findByUsuario_Id(68L);
        verifyNoMoreInteractions(repositorio);
        return resultado;
    }

    private void agregar(List<Habito> datos, String categoria, String nombre, String valor, String unidad) {
        Usuario propietario = new Usuario(); propietario.setId(68L); // Identificador ficticio, sin consulta real.
        CategoriaHabito c = new CategoriaHabito(); c.setNombre(categoria);
        Habito h = new Habito(); h.setId((long) datos.size() + 1); h.setUsuario(propietario); h.setCategoria(c);
        h.setNombre(nombre); h.setValor(new BigDecimal(valor)); h.setUnidad(unidad);
        h.setFechaRegistro(LocalDateTime.of(2026, 11, 8, 12, 0));
        h.setFechaActualizacion(h.getFechaRegistro()); datos.add(h);
    }
}
