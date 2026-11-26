package com.huellago.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class InternacionalizacionConfiguration {
    public static final Locale ESPANOL = Locale.forLanguageTag("es-419");
    public static final Locale INGLES = Locale.forLanguageTag("en-US");

    @Bean
    LocaleResolver localeResolver() {
        return new AcceptHeaderLocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                if (request.getHeader("Accept-Language") == null) return ESPANOL;
                try {
                    for (Locale preferida : Collections.list(request.getLocales())) {
                        String etiqueta = preferida.toLanguageTag();
                        if ("es".equalsIgnoreCase(etiqueta) || "es-419".equalsIgnoreCase(etiqueta)) return ESPANOL;
                        if ("en".equalsIgnoreCase(etiqueta) || "en-US".equalsIgnoreCase(etiqueta)) return INGLES;
                    }
                } catch (IllegalArgumentException formatoInvalido) {
                    return ESPANOL;
                }
                return ESPANOL;
            }
        };
    }
}
