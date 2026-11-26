package com.huellago.backend.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.LocaleResolver;

@Configuration
public class SecurityConfiguration {

    @Autowired
    JwtRequestFilter jwtRequestFilter;

    @Value("${huellago.cors.allowed-origins:http://localhost:4200,http://127.0.0.1:4200,http://localhost:5173,http://127.0.0.1:5173}")
    private String origenesPermitidos;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origenes = Arrays.stream(origenesPermitidos.split(","))
                .map(String::trim).filter(origen -> !origen.isEmpty()).toList();
        if (origenes.isEmpty() || origenes.contains("*")) {
            throw new IllegalArgumentException("Configura origenes CORS explicitos");
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origenes);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language"));
        cors.setExposedHeaders(List.of("Content-Disposition", "X-Reauthentication-Required", "Retry-After"));
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", cors);
        return fuente;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            MessageSource mensajes, LocaleResolver idiomas) throws Exception {
        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        http.cors(Customizer.withDefaults());
        http.csrf(AbstractHttpConfigurer::disable);
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((solicitud, respuesta, error) -> {
                    var idioma = idiomas.resolveLocale(solicitud);
                    respuesta.setStatus(HttpStatus.UNAUTHORIZED.value());
                    respuesta.setHeader("Content-Language", idioma.toLanguageTag());
                    respuesta.addHeader("Vary", "Accept-Language");
                    respuesta.setHeader("WWW-Authenticate", "Bearer realm=\"HuellaGo\", error=\"invalid_token\", error_description=\""
                            + mensajes.getMessage("auth.required", null, "Autenticación requerida", idioma) + "\"");
                })
                .accessDeniedHandler((solicitud, respuesta, error) -> {
                    var idioma = idiomas.resolveLocale(solicitud);
                    respuesta.setStatus(HttpStatus.FORBIDDEN.value());
                    respuesta.setCharacterEncoding("UTF-8");
                    respuesta.setContentType("application/json");
                    respuesta.setHeader("Content-Language", idioma.toLanguageTag());
                    respuesta.addHeader("Vary", "Accept-Language");
                    respuesta.getWriter().write("{\"mensaje\":\""
                            + mensajes.getMessage("auth.forbidden", null, "Acceso denegado", idioma) + "\"}");
                }));
        http.authorizeHttpRequests(authorizationRegistry -> authorizationRegistry
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/auth/register", "/auth/login",
                        "/auth/recuperar-contrasena", "/auth/restablecer-contrasena").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                        "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                .anyRequest().authenticated()
        );
        http.sessionManagement(sessionManager -> sessionManager
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
