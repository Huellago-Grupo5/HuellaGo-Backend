package com.huellago.backend.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class InternacionalizacionConfigurationTest {
    @AfterEach void limpiarIdioma() { LocaleContextHolder.resetLocaleContext(); }

    @Test void reconocePortuguesBrasileñoEnAcceptLanguage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8");
        assertEquals(InternacionalizacionConfiguration.PORTUGUES,
                new InternacionalizacionConfiguration().localeResolver().resolveLocale(request));
    }

    @Test void localizaMensajesDeDominioAlPortuguesSinMutarLaFraseFuente() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        var textos = new TextoLocalizado(source);
        LocaleContextHolder.setLocale(InternacionalizacionConfiguration.PORTUGUES);
        String original = "Usuario no encontrado";
        String response = textos.traducir(original);
        assertEquals("Usuário não encontrado", response);
        assertNotEquals(original, response);
    }
}
