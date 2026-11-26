package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ContextoRecomendacionesIADTO;
import com.huellago.backend.dtos.RecomendacionGeneradaIADTO;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.HabitoRepository;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Evaluación opt-in: seis solicitudes reales por fase; juicio semántico humano separado del resultado JUnit. */
@EnabledIfEnvironmentVariable(named = "HUELLAGO_OLLAMA_REAL_TEST", matches = "true")
@EnabledIfEnvironmentVariable(named = "HUELLAGO_OLLAMA_EVAL_PHASE", matches = "[AB]")
@Execution(ExecutionMode.SAME_THREAD)
class OllamaComparacionPoliticasRealTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void evaluarPerfil(int perfil) throws Exception {
        String fase = System.getenv("HUELLAGO_OLLAMA_EVAL_PHASE");
        assertTrue(Set.of("A", "B").contains(fase), "Definir HUELLAGO_OLLAMA_EVAL_PHASE=A o B");
        Path directorio = Path.of("target", "paso86", fase);
        Files.createDirectories(directorio);
        // CREATE_NEW evita sobrescribir evidencia anterior durante una repetición accidental.
        Path reporte = directorio.resolve("perfil-" + perfil + ".json");
        assertFalse(Files.exists(reporte), "No sobrescribir inferencias ya registradas");
        byte[] fuente = Files.readAllBytes(Path.of("src/main/java/com/huellago/backend/serviceimpl/ReglasInterpretacionAmbiental.java"));
        Path copia = directorio.resolve("ReglasInterpretacionAmbiental.java.txt");
        if (!Files.exists(copia)) Files.write(copia, fuente, StandardOpenOption.CREATE_NEW);
        assertArrayEquals(fuente, Files.readAllBytes(copia), "La política debe permanecer estable dentro de la fase");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fuente));

        var repositorio = mock(HabitoRepository.class);
        when(repositorio.findByUsuario_Id(8600L)).thenReturn(habitos(perfil));
        var contexto = new ContextoRecomendacionesIAServiceImpl(repositorio).construir(8600L);
        verify(repositorio).findByUsuario_Id(8600L); verifyNoMoreInteractions(repositorio);
        var datos = ValidadorRecomendacionesIA.contexto(contexto.contexto());
        var configuracion = new OllamaConfiguration();
        var ajustes = new OllamaConfiguration.Ajustes(URI.create("http://localhost:11434"),
                "qwen3:4b-instruct", Duration.ofSeconds(3), Duration.ofSeconds(120), 65536);
        var http = configuracion.ollamaRestClient(ajustes).mutate().requestInterceptor((peticion, cuerpo, ejecucion) -> {
            Files.write(directorio.resolve("solicitud-" + perfil + ".json"), cuerpo, StandardOpenOption.CREATE_NEW);
            return ejecucion.execute(peticion, cuerpo);
        }).build();
        var cliente = configuracion.generadorRecomendacionesIAService(http, ajustes);
        Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("fase", fase); registro.put("perfil", perfil); registro.put("politicaSha256", hash);
        registro.put("inicio", Instant.now().toString()); registro.put("modelo", ajustes.modelo());
        registro.put("contexto", contexto); registro.put("opcionesGeneracion", "Sin options ni seed: valores predeterminados de Ollama en ambas fases");
        long inicio = System.nanoTime();
        try {
            List<RecomendacionGeneradaIADTO> recomendaciones = cliente.generarDesdeContexto(contexto);
            registro.put("recomendaciones", recomendaciones);
            assertEquals(datos.size(), recomendaciones.size());
            assertEquals(datos.keySet(), new HashSet<>(recomendaciones.stream().map(RecomendacionGeneradaIADTO::categoria).toList()));
            assertEquals(recomendaciones.size(), recomendaciones.stream().map(RecomendacionGeneradaIADTO::categoria).distinct().count());
            for (var r : recomendaciones) {
                assertFalse(r.titulo().isBlank()); assertFalse(r.descripcion().isBlank()); assertFalse(r.motivo().isBlank());
            }
            registro.put("validacionAutomatica", "APROBADA: HTTP satisfactorio, JSON, categorías y campos; sin certificación semántica");
        } catch (RuntimeException | AssertionError error) {
            registro.put("error", error.getClass().getSimpleName() + ": " + error.getMessage());
            throw error;
        } finally {
            registro.put("duracionMs", Duration.ofNanos(System.nanoTime() - inicio).toMillis());
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(registro);
            Files.writeString(reporte, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            System.out.println(json);
        }
    }

    private List<Habito> habitos(int perfil) {
        List<Habito> datos = new ArrayList<>();
        switch (perfil) {
            case 1 -> {
                transporte(datos, "Bus", "80", "5"); energia(datos, "Casa", "3", "Electricidad", "12.345");
                agregar(datos, "Alimentación", "tipo", "0", "Mixta"); residuos(datos, "Medio", false);
            }
            case 2 -> { transporte(datos, "Bicicleta", "20", "4"); residuos(datos, "Bajo", true); }
            case 3 -> { transporte(datos, "Caminando", "15", "5"); agregar(datos, "Alimentación", "tipo", "0", "Vegana"); }
            case 4 -> { transporte(datos, "Auto", "100", "5"); residuos(datos, "Alto", false); }
            case 5 -> energia(datos, "Departamento", "2", "Electricidad", null);
            case 6 -> { agregar(datos, "Alimentación", "tipo", "0", "Vegetariana"); residuos(datos, "Medio", true); }
            default -> throw new IllegalArgumentException("Perfil no definido");
        }
        return datos;
    }

    private void transporte(List<Habito> d, String medio, String km, String dias) {
        agregar(d, "Transporte", "medio", "0", medio); agregar(d, "Transporte", "kmSemana", km, "km/semana");
        agregar(d, "Transporte", "diasSemana", dias, "dias/semana");
    }
    private void energia(List<Habito> d, String vivienda, String personas, String fuente, String consumo) {
        agregar(d, "Energía", "vivienda", "0", vivienda); agregar(d, "Energía", "personas", personas, "personas");
        agregar(d, "Energía", "fuente", "0", fuente);
        if (consumo != null) agregar(d, "Energía", "consumoKwhDiario", consumo, "kWh/dia");
    }
    private void residuos(List<Habito> d, String plasticos, boolean reciclas) {
        agregar(d, "Residuos", "plasticos", "0", plasticos); agregar(d, "Residuos", "reciclas", reciclas ? "1" : "0", "boolean");
    }
    private void agregar(List<Habito> datos, String categoria, String nombre, String valor, String unidad) {
        Usuario u = new Usuario(); u.setId(8600L);
        CategoriaHabito c = new CategoriaHabito(); c.setNombre(categoria);
        Habito h = new Habito(); h.setId((long) datos.size() + 1); h.setUsuario(u); h.setCategoria(c);
        h.setNombre(nombre); h.setValor(new BigDecimal(valor)); h.setUnidad(unidad);
        h.setFechaRegistro(LocalDateTime.of(2026, 11, 8, 12, 0)); h.setFechaActualizacion(h.getFechaRegistro()); datos.add(h);
    }
}
