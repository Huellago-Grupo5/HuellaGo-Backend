package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.RecuperacionContrasena;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.ValidacionCuenta;
import com.huellago.backend.services.CorreoRecuperacionService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class RecuperacionContrasenaServiceImpl implements com.huellago.backend.services.RecuperacionContrasenaService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final RecuperacionContrasenaRepository recuperaciones;
    private final UsuarioRepository usuarios;
    private final SesionRepository sesiones;
    private final CorreoRecuperacionService correo;
    private final jakarta.persistence.EntityManager entityManager;
    private final com.huellago.backend.services.LimiteRecuperacionService limite;
    public RecuperacionContrasenaServiceImpl(RecuperacionContrasenaRepository recuperaciones,
            UsuarioRepository usuarios, SesionRepository sesiones, CorreoRecuperacionService correo,
            jakarta.persistence.EntityManager entityManager,
            com.huellago.backend.services.LimiteRecuperacionService limite) {
        this.recuperaciones = recuperaciones; this.usuarios = usuarios; this.sesiones = sesiones; this.correo = correo;
        this.entityManager = entityManager;
        this.limite = limite;
    }
    @Transactional
    public RecuperacionRespuestaDTO solicitar(RecuperarContrasenaDTO dto) {
        ValidacionCuenta.exigir(dto);
        limite.reservar(dto.getCorreo());
        // No revelar existencia ni producir tokens si no hay un canal de entrega configurado.
        correo.verificarConfiguracion();
        var usuario = usuarios.findByCorreoNormalizado(ValidacionCuenta.correo(dto.getCorreo()));
        if (usuario == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        usuarios.bloquearParaRecomendaciones(usuario.getId()).orElseThrow(this::invalido);
        entityManager.refresh(usuario);
        recuperaciones.eliminarDelUsuario(usuario.getId());
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String secreto = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RecuperacionContrasena fila = new RecuperacionContrasena();
        fila.setUsuario(usuario); fila.setTokenHash(hash(secreto)); fila.setCorreoDestino(usuario.getCorreo());
        fila.setCreadoEn(Instant.now()); fila.setExpiraEn(fila.getCreadoEn().plus(Duration.ofMinutes(15)));
        recuperaciones.saveAndFlush(fila);
        correo.enviar(usuario.getCorreo(), secreto);
        return new RecuperacionRespuestaDTO("El enlace fue aceptado por el servicio de correo para su envío");
    }
    @Transactional
    public void restablecer(RestablecerContrasenaDTO dto) {
        ValidacionCuenta.exigir(dto);
        // Tokens opacos de 256 bits: ningún JWT legado puede entrar en este mecanismo.
        if (!dto.getToken().matches("[A-Za-z0-9_-]{43}")) throw invalido();
        String digest = hash(dto.getToken());
        Long id = recuperaciones.buscarUsuario(digest).orElseThrow(this::invalido);
        usuarios.bloquearParaRecomendaciones(id).orElseThrow(this::invalido);
        var fila = recuperaciones.bloquear(digest).orElseThrow(this::invalido);
        var usuario = usuarios.findById(id).orElseThrow(this::invalido);
        entityManager.refresh(usuario);
        if (fila.getConsumidoEn() != null || !fila.getExpiraEn().isAfter(Instant.now())
                || !Objects.equals(fila.getCorreoDestino(), usuario.getCorreo())) throw invalido();
        usuario.setContrasena(new BCryptPasswordEncoder().encode(dto.getNuevaContrasena()));
        usuario.setFechaActualizacion(LocalDateTime.now());
        usuarios.saveAndFlush(usuario);
        var activas = sesiones.findByUsuario_IdAndActivaTrue(id);
        activas.forEach(s -> s.setActiva(false)); sesiones.saveAll(activas);
        fila.setConsumidoEn(Instant.now()); recuperaciones.saveAndFlush(fila);
    }
    public static String hash(String secreto) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secreto.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 no disponible"); }
    }
    private ResponseStatusException invalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token de recuperación inválido, expirado o utilizado");
    }
}
