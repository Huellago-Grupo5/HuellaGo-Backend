package com.huellago.backend.serviceimpl;

import com.huellago.backend.security.*;
import com.huellago.backend.services.LimiteRecuperacionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/** Ventana móvil exacta y reserva conjunta correo/IP, atómica por instancia y sin E/S bajo el monitor. */
@Service
public class LimiteRecuperacionServiceImpl implements LimiteRecuperacionService {
    private final Clock reloj;
    private final OrigenRecuperacion origen;
    private final Duration ventana;
    private final Duration intervalo;
    private final int maxCorreo;
    private final int maxIp;
    private final int capacidad;
    private final Map<String, Deque<Instant>> intentos = new HashMap<>();
    private Instant proximaLimpieza = Instant.MIN;

    public LimiteRecuperacionServiceImpl(Environment entorno, @Qualifier("recoveryRateClock") Clock reloj,
                                         OrigenRecuperacion origen) {
        this.reloj = reloj; this.origen = origen;
        long segundos = entorno.getProperty("huellago.recovery.rate.window-seconds", Long.class, 900L);
        long pausa = entorno.getProperty("huellago.recovery.rate.cooldown-seconds", Long.class, 60L);
        maxCorreo = entorno.getProperty("huellago.recovery.rate.account-limit", Integer.class, 3);
        maxIp = entorno.getProperty("huellago.recovery.rate.ip-limit", Integer.class, 20);
        capacidad = entorno.getProperty("huellago.recovery.rate.max-entries", Integer.class, 10000);
        if (segundos < 1 || pausa < 1 || pausa > segundos || maxCorreo < 1 || maxIp < 1 || capacidad < 2)
            throw new IllegalArgumentException("Configuración inválida del límite de recuperación");
        ventana = Duration.ofSeconds(segundos); intervalo = Duration.ofSeconds(pausa);
    }

    @Override public void reservar(String correo) {
        String cuenta = ValidacionCuenta.correo(correo);
        if (cuenta == null || cuenta.isBlank()) throw new IllegalArgumentException("Correo requerido para reservar intento");
        String correoKey = "CUENTA:" + huella(cuenta);
        String ipKey = origen.ipActual().map(ip -> "IP:" + huella(ip)).orElse(null);
        synchronized (this) {
            Instant ahora = reloj.instant();
            if (!ahora.isBefore(proximaLimpieza)) limpiar(ahora);
            Deque<Instant> cuentaIntentos = vigentes(correoKey, ahora);
            Deque<Instant> ipIntentos = ipKey == null ? null : vigentes(ipKey, ahora);
            Instant permitirEn = ahora;
            if (cuentaIntentos != null && !cuentaIntentos.isEmpty()) {
                permitirEn = posterior(permitirEn, cuentaIntentos.peekLast().plus(intervalo));
                if (cuentaIntentos.size() >= maxCorreo)
                    permitirEn = posterior(permitirEn, cuentaIntentos.peekFirst().plus(ventana));
            }
            if (ipIntentos != null && ipIntentos.size() >= maxIp)
                permitirEn = posterior(permitirEn, ipIntentos.peekFirst().plus(ventana));
            if (permitirEn.isAfter(ahora)) throw excedido(ahora, permitirEn);
            int nuevas = (cuentaIntentos == null ? 1 : 0) + (ipKey != null && ipIntentos == null ? 1 : 0);
            if (intentos.size() + nuevas > capacidad) {
                limpiar(ahora);
                if (intentos.size() + nuevas > capacidad) throw new LimiteRecuperacionException(60);
            }
            // Se modifican AMBAS cuotas solo después de comprobar todo: un rechazo no extiende bloqueos.
            intentos.computeIfAbsent(correoKey, k -> new ArrayDeque<>()).addLast(ahora);
            if (ipKey != null) intentos.computeIfAbsent(ipKey, k -> new ArrayDeque<>()).addLast(ahora);
        }
    }

    private Deque<Instant> vigentes(String key, Instant ahora) {
        Deque<Instant> cola = intentos.get(key);
        if (cola != null) {
            podar(cola, ahora);
            if (cola.isEmpty()) { intentos.remove(key); return null; }
        }
        return cola;
    }
    private void podar(Deque<Instant> cola, Instant ahora) {
        Instant corte = ahora.minus(ventana);
        while (!cola.isEmpty() && !cola.peekFirst().isAfter(corte)) cola.removeFirst();
    }
    private void limpiar(Instant ahora) {
        intentos.values().forEach(c -> podar(c, ahora));
        intentos.entrySet().removeIf(e -> e.getValue().isEmpty());
        proximaLimpieza = ahora.plusSeconds(60);
    }
    @Scheduled(fixedDelay = 60000)
    public synchronized void limpiarExpirados() { limpiar(reloj.instant()); }

    private Instant posterior(Instant a, Instant b) { return a.isAfter(b) ? a : b; }
    private String huella(String valor) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 no disponible"); }
    }
    private LimiteRecuperacionException excedido(Instant ahora, Instant permitirEn) {
        Duration espera = Duration.between(ahora, permitirEn);
        return new LimiteRecuperacionException(espera.getSeconds() + (espera.getNano() > 0 ? 1 : 0));
    }
}
