package com.huellago.backend.serviceimpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Configuración en memoria, sin ApplicationContext, servidor, datasource o solicitudes HTTP. */
class OllamaConfigurationTest {
    final OllamaConfiguration configuracion = new OllamaConfiguration();

    @Test void defaultsNoConectanAOllamaNiRequierenNuevasDependencias() {
        var ajustes = configuracion.ajustesOllama(new MockEnvironment());
        assertEquals("http://localhost:11434", ajustes.baseUrl().toString());
        assertEquals("qwen3:4b-instruct", ajustes.modelo());
        assertEquals(Duration.ofSeconds(3), ajustes.conexion());
        assertEquals(Duration.ofSeconds(120), ajustes.lectura());
        assertEquals(65536, ajustes.maxRespuestaBytes());
    }

    @Test void variablesExternasSeResuelvenSinModificarElEntornoReal() {
        var entorno = new MockEnvironment();
        entorno.getPropertySources().addFirst(new SystemEnvironmentPropertySource("variablesSimuladas", Map.of(
                "HUELLAGO_OLLAMA_BASE_URL", "http://127.0.0.1:11434",
                "HUELLAGO_OLLAMA_MODEL", "qwen3:4b-instruct",
                "HUELLAGO_OLLAMA_CONNECT_TIMEOUT", "PT2S",
                "HUELLAGO_OLLAMA_READ_TIMEOUT", "PT90S",
                "HUELLAGO_OLLAMA_MAX_RESPONSE_BYTES", "32768")));
        var ajustes = configuracion.ajustesOllama(entorno);
        assertEquals("http://127.0.0.1:11434", ajustes.baseUrl().toString());
        assertEquals(Duration.ofSeconds(2), ajustes.conexion());
        assertEquals(Duration.ofSeconds(90), ajustes.lectura());
        assertEquals(32768, ajustes.maxRespuestaBytes());
    }

    @ParameterizedTest @ValueSource(strings = {"", "ftp://localhost", "http://usuario:clave@localhost:11434",
            "http://localhost:11434/api", "http://localhost:11434?secreto=valor", "http://localhost:11434#fragmento",
            "http://localhost:0", "http://localhost:99999", "/relativa"})
    void configuracionUrlInvalidaNoExponeSuContenido(String url) {
        var error = assertThrows(IllegalArgumentException.class, () -> configuracion.ajustesOllama(
                new MockEnvironment().withProperty("huellago.ollama.base-url", url)));
        assertFalse(error.getMessage().contains("clave")); assertFalse(error.getMessage().contains("secreto="));
    }

    @ParameterizedTest @ValueSource(strings = {"PT0S", "-PT1S", "PT11M", "indefinido", "120", ""})
    void tiemposInvalidosSeRechazanAntesDeCrearCliente(String tiempo) {
        for (String propiedad : java.util.List.of("huellago.ollama.connect-timeout", "huellago.ollama.read-timeout")) {
            assertThrows(IllegalArgumentException.class, () -> configuracion.ajustesOllama(
                    new MockEnvironment().withProperty(propiedad, tiempo)));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"0", "1023", "1048577", "no-numero"})
    void limiteInvalidoSeRechaza(String limite) {
        assertThrows(IllegalArgumentException.class, () -> configuracion.ajustesOllama(
                new MockEnvironment().withProperty("huellago.ollama.max-response-bytes", limite)));
    }

    @ParameterizedTest @ValueSource(strings = {"", "modelo con espacios", "modelo\ninstruccion"})
    void modeloInvalidoSeRechaza(String modelo) {
        assertThrows(IllegalArgumentException.class, () -> configuracion.ajustesOllama(
                new MockEnvironment().withProperty("huellago.ollama.model", modelo)));
    }
}
