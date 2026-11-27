package com.huellago.backend.config;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Comparator;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/** Traduce únicamente textos de presentación conocidos; los identificadores y datos de terceros permanecen intactos. */
@Component
public class TextoLocalizado {
    private final MessageSource mensajes;

    public TextoLocalizado(MessageSource mensajes) {
        this.mensajes = mensajes;
    }

    public String mensaje(String clave) {
        return mensajes.getMessage(clave, null, LocaleContextHolder.getLocale());
    }

    public String traducir(String original) {
        if (original == null || original.isBlank()) return original;
        Locale idioma = LocaleContextHolder.getLocale();
        if (!"en".equals(idioma.getLanguage()) && !"pt".equals(idioma.getLanguage())) return original;
        String directo = mensajes.getMessage(original, null, null, idioma);
        if (directo != null) return directo;
        int separador = original.indexOf(": ");
        if (separador > 0) {
            String detalle = mensajes.getMessage(original.substring(separador + 2), null, null, idioma);
            if (detalle != null) return original.substring(0, separador + 2) + detalle;
        }
        ResourceBundle catalogo = ResourceBundle.getBundle("messages", idioma.getLanguage().equals("pt")
                ? InternacionalizacionConfiguration.PORTUGUES : InternacionalizacionConfiguration.INGLES);
        for (String prefijo : catalogo.keySet().stream()
                .filter(clave -> clave.endsWith(" ") && clave.length() > 8)
                .sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
            if (original.startsWith(prefijo)) return catalogo.getString(prefijo) + original.substring(prefijo.length());
        }
        return original;
    }

    /** Solo para diagnósticos internos que concatenan frases conocidas y datos numéricos. */
    public String traducirCompuesto(String original) {
        String exacto = traducir(original);
        if (original == null || !original.equals(exacto)
                || !List.of("en", "pt").contains(LocaleContextHolder.getLocale().getLanguage())) return exacto;
        // Algunos mensajes existentes concatenan dos frases de presentación.
        // Se sustituyen solamente entradas explícitas del catálogo de traducciones.
        String traducido = original;
        Locale locale = LocaleContextHolder.getLocale();
        ResourceBundle catalogo = ResourceBundle.getBundle("messages", locale.getLanguage().equals("pt")
                ? InternacionalizacionConfiguration.PORTUGUES : InternacionalizacionConfiguration.INGLES);
        for (String frase : catalogo.keySet().stream()
                .filter(clave -> !clave.startsWith("auth."))
                .sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
            if (traducido.contains(frase)) traducido = traducido.replace(frase, catalogo.getString(frase));
        }
        return traducido;
    }
}
