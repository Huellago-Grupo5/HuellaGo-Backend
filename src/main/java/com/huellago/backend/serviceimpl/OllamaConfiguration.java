package com.huellago.backend.serviceimpl;

import com.huellago.backend.services.GeneradorRecomendacionesIAService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import com.huellago.backend.services.*;
import com.huellago.backend.repositories.*;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
public class OllamaConfiguration {
    public record Ajustes(URI baseUrl, String modelo, Duration conexion, Duration lectura, int maxRespuestaBytes) {
        public Ajustes {
            if (baseUrl == null || !baseUrl.isAbsolute() || baseUrl.getHost() == null
                    || !("http".equalsIgnoreCase(baseUrl.getScheme()) || "https".equalsIgnoreCase(baseUrl.getScheme()))
                    || baseUrl.getUserInfo() != null || baseUrl.getQuery() != null || baseUrl.getFragment() != null
                    || !(baseUrl.getPath().isEmpty() || "/".equals(baseUrl.getPath())))
                throw new IllegalArgumentException("La URL de Ollama debe ser una base HTTP/HTTPS sin credenciales ni ruta adicional");
            if (baseUrl.getPort() == 0 || baseUrl.getPort() > 65535)
                throw new IllegalArgumentException("El puerto de Ollama no es válido");
            if (modelo == null || !modelo.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}"))
                throw new IllegalArgumentException("El modelo de Ollama no es válido");
            validarTiempo(conexion); validarTiempo(lectura);
            if (maxRespuestaBytes < 1024 || maxRespuestaBytes > 1_048_576)
                throw new IllegalArgumentException("El límite de respuesta de Ollama debe estar entre 1024 y 1048576 bytes");
        }
        private static void validarTiempo(Duration tiempo) {
            if (tiempo == null || tiempo.compareTo(Duration.ofMillis(1)) < 0
                    || tiempo.compareTo(Duration.ofMinutes(10)) > 0)
                throw new IllegalArgumentException("Los tiempos de Ollama deben estar entre 1 ms y 10 minutos");
        }
    }

    @Bean
    public Ajustes ajustesOllama(Environment entorno) {
        try {
            return new Ajustes(URI.create(entorno.getProperty("huellago.ollama.base-url", "http://localhost:11434")),
                    entorno.getProperty("huellago.ollama.model", "qwen3:4b-instruct"),
                    Duration.parse(entorno.getProperty("huellago.ollama.connect-timeout", "PT3S")),
                    Duration.parse(entorno.getProperty("huellago.ollama.read-timeout", "PT120S")),
                    Integer.parseInt(entorno.getProperty("huellago.ollama.max-response-bytes", "65536")));
        } catch (IllegalArgumentException | java.time.DateTimeException error) {
            throw new IllegalArgumentException("Configuración de Ollama inválida; revise URL, modelo, tiempos y límite de respuesta");
        }
    }

    @Bean("ollamaRestClient")
    public RestClient ollamaRestClient(Ajustes ajustes) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(ajustes.conexion())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(ajustes.lectura());
        return RestClient.builder().baseUrl(ajustes.baseUrl().toString()).requestFactory(factory).build();
    }

    @Bean
    public GeneradorRecomendacionesIAService generadorRecomendacionesIAService(
            @Qualifier("ollamaRestClient") RestClient cliente, Ajustes ajustes) {
        return new OllamaRecomendacionesServiceImpl(cliente, ajustes);
    }

    /** Composicion habitual, sin otro perfil, servidor, seguridad o fuente de datos.
     * Deshabilitada por defecto. Activacion no equivale a aprobacion editorial. */
    @Configuration(proxyBeanMethods=false)
    @ConditionalOnProperty(name="huellago.ollama.enabled",havingValue="true")
    public static class IntegracionLocal {
        @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor soloUsoLocal(Environment e) {
            return b->{
                if(!EntradaRecomendacionesIA.esLocal(e.getProperty("server.address")))
                    throw new IllegalStateException("La integracion universitaria IA requiere server.address=127.0.0.1 o ::1");
                var url=URI.create(e.getProperty("huellago.ollama.base-url","http://localhost:11434"));
                if(!"localhost".equals(url.getHost()) && !EntradaRecomendacionesIA.esLocal(url.getHost()))
                    throw new IllegalStateException("Ollama debe ejecutarse localmente");
                if(!"qwen3:4b-instruct".equals(e.getProperty("huellago.ollama.model","qwen3:4b-instruct")))
                    throw new IllegalStateException("El modelo debe coincidir con el motor validado qwen3:4b-instruct");
            };
        }
        @Bean PresentacionRecomendacionesIAService presentador() { return new PresentacionRecomendacionesIAServiceImpl(); }
        @Bean PoliticaAutorizacionEditorialIAService editorial(CatalogoAccionesAmbientalesService c,PresentacionRecomendacionesIAService p) {
            // Decisión humana limitada a uso universitario local; el escritor rechaza PUBLICABLE.
            // Las 30 huellas se comprueban antes de exponer una sola familia.
            return new PoliticaAutorizacionEditorialIAServiceImpl(c,p,new RegistroEditorialUniversitarioLocal(p));
        }
        @Bean OrquestadorRecomendacionesIAService orquestador(GeneradorRecomendacionesIAService s,CatalogoAccionesAmbientalesService c,PresentacionRecomendacionesIAService p) {
            return new OrquestadorRecomendacionesIAServiceImpl(s,c,p);
        }
        @Bean PersistenciaRecomendacionesIAService escritor(UsuarioRepository u,RecomendacionIARepository r,UsuarioRecomendacionIARepository a,
                CategoriaHabitoRepository c,CapturaContextoAmbientalIAService captura,EntityManagerFactory emf,PoliticaAutorizacionEditorialIAService editorial,
                NotificacionService notificaciones) {
            return new PersistenciaRecomendacionesIAServiceImpl(u,r,a,c,captura,
                    org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(emf),editorial,false,true,notificaciones);
        }
        @Bean GeneracionManualIAService generacion(CapturaContextoAmbientalIAService c,PoliticaAutorizacionEditorialIAService e,
                OrquestadorRecomendacionesIAService o,PersistenciaRecomendacionesIAService p,CatalogoAccionesAmbientalesService catalogo) {
            return new GeneracionManualIAServiceImpl(c,e,o,p,catalogo);
        }
        @Bean EntradaDemostracionRecomendacionesIA entradaIA(GeneracionManualIAService g,UsuarioRecomendacionIARepository a,
                org.springframework.transaction.PlatformTransactionManager tm) {
            return new EntradaRecomendacionesIA(g,a,tm);
        }
    }
}
