package com.huellago.backend.serviceimpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CorreoRecuperacionSmtpTest {
    @AfterEach void limpiarIdioma() { org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext(); }
    @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> proveedor = mock(ObjectProvider.class);
    MockEnvironment entorno = new MockEnvironment();
    CorreoRecuperacionSmtp servicio = new CorreoRecuperacionSmtp(proveedor, entorno);
    void configurar() {
        entorno.withProperty("huellago.recovery.enabled", "true").withProperty("huellago.recovery.from", "noreply@example.invalid")
                .withProperty("huellago.recovery.reset-url", "https://frontend.example.invalid/restablecer");
    }
    @Test void sinConfiguracionDevuelve503YNoEnvia() {
        assertEquals(503, assertThrows(ResponseStatusException.class, servicio::verificarConfiguracion).getStatusCode().value());
        verifyNoInteractions(proveedor);
    }
    @Test void sinProveedorDevuelve503() { configurar(); assertThrows(ResponseStatusException.class, servicio::verificarConfiguracion); }
    @Test void enviaSoloAlDestinatarioRegistrado() {
        configurar(); var sender = mock(JavaMailSender.class);
        when(proveedor.getIfAvailable()).thenReturn(sender); when(proveedor.getObject()).thenReturn(sender);
        servicio.enviar("usuario@example.invalid", "secreto-de-prueba");
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class); verify(sender).send(captor.capture());
        assertArrayEquals(new String[]{"usuario@example.invalid"}, captor.getValue().getTo());
        assertTrue(captor.getValue().getText().contains("https://frontend.example.invalid/restablecer?token=secreto-de-prueba"));
    }
    @Test void correoDeRecuperacionEnInglesConservaEnlaceYToken() {
        configurar(); var sender = mock(JavaMailSender.class);
        when(proveedor.getIfAvailable()).thenReturn(sender); when(proveedor.getObject()).thenReturn(sender);
        var mensajes = new org.springframework.context.support.ResourceBundleMessageSource();
        mensajes.setBasename("messages"); mensajes.setDefaultEncoding("UTF-8");
        org.springframework.test.util.ReflectionTestUtils.setField(servicio, "textos",
                new com.huellago.backend.config.TextoLocalizado(mensajes));
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.US);
        servicio.enviar("usuario@example.invalid", "token-ficticio");
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class); verify(sender).send(captor.capture());
        assertEquals("HuellaGo password recovery", captor.getValue().getSubject());
        assertTrue(captor.getValue().getText().startsWith("Use the following link"));
        assertTrue(captor.getValue().getText().contains("?token=token-ficticio"));
    }
    @Test void errorDelProveedorNoExponeDetalles() {
        configurar(); var sender = mock(JavaMailSender.class);
        when(proveedor.getIfAvailable()).thenReturn(sender); when(proveedor.getObject()).thenReturn(sender);
        doThrow(new MailSendException("contenido secreto SMTP")).when(sender).send(any(SimpleMailMessage.class));
        var error = assertThrows(ResponseStatusException.class, () -> servicio.enviar("usuario@example.invalid", "secreto"));
        assertEquals(503, error.getStatusCode().value()); assertFalse(error.toString().contains("contenido secreto")); assertNull(error.getCause());
    }
    @Test void enlaceInseguroNoEnvia() {
        configurar(); when(proveedor.getIfAvailable()).thenReturn(mock(JavaMailSender.class));
        entorno.withProperty("huellago.recovery.reset-url", "http://externo.example.invalid/reset");
        assertThrows(ResponseStatusException.class, servicio::verificarConfiguracion);
    }
}
