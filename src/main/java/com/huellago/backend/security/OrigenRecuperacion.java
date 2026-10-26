package com.huellago.backend.security;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Optional;
import java.util.Locale;

@Component
public class OrigenRecuperacion {
    private final Environment entorno;
    public OrigenRecuperacion(Environment entorno) { this.entorno = entorno; }

    public Optional<String> ipActual() {
        // Si Spring/container procesa forwarding, no afirmamos conocer la IP de conexión original.
        // Nunca se leen Forwarded, X-Forwarded-For o X-Real-IP del cliente.
        if (!"none".equalsIgnoreCase(entorno.getProperty("server.forward-headers-strategy", "none")))
            return Optional.empty();
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos))
            return Optional.empty();
        jakarta.servlet.ServletRequest conexion = atributos.getRequest();
        // Descarta wrappers de forwarding manuales; conserva la dirección del request original.
        while (conexion instanceof jakarta.servlet.ServletRequestWrapper envoltura)
            conexion = envoltura.getRequest();
        String ip = conexion.getRemoteAddr();
        if (ip == null || ip.length() > 64 || ip.isBlank()) return Optional.empty();
        // Solo literales proporcionados por el contenedor; no resolución DNS ni cabeceras.
        if (!ip.matches("[0-9a-fA-F:.%]+") || !(ip.contains(":") || ip.contains("."))) return Optional.empty();
        return Optional.of(ip.toLowerCase(Locale.ROOT));
    }
}
