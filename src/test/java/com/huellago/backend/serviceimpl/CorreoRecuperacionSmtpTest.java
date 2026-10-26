package com.huellago.backend.serviceimpl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CorreoRecuperacionSmtpTest {
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
