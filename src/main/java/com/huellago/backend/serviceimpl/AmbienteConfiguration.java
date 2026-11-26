package com.huellago.backend.serviceimpl;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AmbienteConfiguration {
    @Bean("openMeteoRestClient")
    RestClient openMeteoRestClient(@Value("${huellago.ambiente.open-meteo.url}") URI url,
                                   @Value("${huellago.ambiente.connect-timeout-seconds:3}") int conexion,
                                   @Value("${huellago.ambiente.read-timeout-seconds:8}") int lectura) {
        return cliente(url, conexion, lectura);
    }

    @Bean("geoapifyRestClient")
    RestClient geoapifyRestClient(@Value("${huellago.ambiente.geoapify.url}") URI url,
                                  @Value("${huellago.ambiente.connect-timeout-seconds:3}") int conexion,
                                  @Value("${huellago.ambiente.geoapify.read-timeout-seconds:8}") int lectura) {
        return cliente(url, conexion, lectura);
    }

    private RestClient cliente(URI url, int conexion, int lectura) {
        if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null
                || url.getUserInfo() != null || url.getFragment() != null || url.getQuery() != null
                || conexion < 1 || conexion > 15 || lectura < 1 || lectura > 30) {
            throw new IllegalArgumentException("Configuración ambiental externa inválida");
        }
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(conexion))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(lectura));
        return RestClient.builder().requestFactory(factory).build();
    }
}
