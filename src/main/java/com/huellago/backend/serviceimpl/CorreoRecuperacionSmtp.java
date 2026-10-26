package com.huellago.backend.serviceimpl;

import com.huellago.backend.services.CorreoRecuperacionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;

@Service
public class CorreoRecuperacionSmtp implements CorreoRecuperacionService {
    private final ObjectProvider<JavaMailSender> proveedores;
    private final Environment entorno;
    public CorreoRecuperacionSmtp(ObjectProvider<JavaMailSender> proveedores, Environment entorno) {
        this.proveedores = proveedores; this.entorno = entorno;
    }
    @Override public void verificarConfiguracion() {
        try {
            if (!entorno.getProperty("huellago.recovery.enabled", Boolean.class, false)
                    || proveedores.getIfAvailable() == null) throw indisponible();
            String from = entorno.getRequiredProperty("huellago.recovery.from");
            if (!from.matches("[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+")) throw indisponible();
            URI url = URI.create(entorno.getRequiredProperty("huellago.recovery.reset-url"));
            boolean local = "localhost".equals(url.getHost()) || "127.0.0.1".equals(url.getHost());
            if (url.getHost() == null || url.getUserInfo() != null || url.getRawQuery() != null
                    || url.getFragment() != null || !("https".equals(url.getScheme()) || local && "http".equals(url.getScheme())))
                throw indisponible();
            if (proveedores.getIfAvailable() instanceof JavaMailSenderImpl smtp) {
                var p = smtp.getJavaMailProperties();
                boolean localSmtp = "localhost".equals(smtp.getHost()) || "127.0.0.1".equals(smtp.getHost());
                if (!localSmtp && !Boolean.parseBoolean(p.getProperty("mail.smtp.ssl.enable"))
                        && !(Boolean.parseBoolean(p.getProperty("mail.smtp.starttls.enable"))
                        && Boolean.parseBoolean(p.getProperty("mail.smtp.starttls.required")))) throw indisponible();
                p.putIfAbsent("mail.smtp.connectiontimeout", "5000");
                p.putIfAbsent("mail.smtp.timeout", "5000");
                p.putIfAbsent("mail.smtp.writetimeout", "5000");
                p.setProperty("mail.debug", "false");
                p.setProperty("mail.smtp.ssl.checkserveridentity", "true");
                smtp.getSession().setDebug(false);
            }
        } catch (RuntimeException error) { throw indisponible(); }
    }
    @Override public void enviar(String destinatario, String token) {
        verificarConfiguracion();
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(entorno.getRequiredProperty("huellago.recovery.from"));
        mensaje.setTo(destinatario);
        mensaje.setSubject("Recuperación de contraseña HuellaGo");
        mensaje.setText("Usa el siguiente enlace en los próximos 15 minutos. Solo puede utilizarse una vez.\n"
                + entorno.getRequiredProperty("huellago.recovery.reset-url") + "?token=" + token
                + "\nSi no solicitaste este cambio, ignora este mensaje.");
        try { proveedores.getObject().send(mensaje); }
        catch (RuntimeException error) { throw indisponible(); }
    }
    private ResponseStatusException indisponible() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "La recuperación por correo no está disponible; no se confirmó ningún envío");
    }
}
