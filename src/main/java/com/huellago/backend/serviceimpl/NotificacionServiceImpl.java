package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Notificacion;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.dtos.NotificacionRespuestaDTO;
import com.huellago.backend.repositories.NotificacionRepository;
import com.huellago.backend.services.NotificacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Service
public class NotificacionServiceImpl implements NotificacionService {

    @Autowired
    NotificacionRepository notificacionRepository;

    @Override
    public Optional<Notificacion> crearOpcional(
            Usuario usuario, String titulo, String mensaje, String tipo) {
        if (!Boolean.TRUE.equals(usuario.getNotificacionesActivas())) {
            return Optional.empty();
        }

        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(usuario);
        notificacion.setTitulo(titulo);
        notificacion.setMensaje(mensaje);
        notificacion.setTipo(tipo);
        notificacion.setLeida(false);
        notificacion.setFechaCreacion(LocalDateTime.now());
        return Optional.of(notificacionRepository.save(notificacion));
    }

    @Override
    @Transactional
    public Optional<Notificacion> crearPorEvento(Usuario usuario, String claveEvento,
            String titulo, String mensaje, String tipo) {
        if (usuario == null || usuario.getId() == null || claveEvento == null
                || !claveEvento.matches("[A-Z_]+:[0-9]+") || claveEvento.length() > 100)
            throw new IllegalArgumentException("Evento de notificación inválido");
        if (!Boolean.TRUE.equals(usuario.getNotificacionesActivas())
                || notificacionRepository.existsByUsuario_IdAndClaveEvento(usuario.getId(), claveEvento))
            return Optional.empty();
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(usuario); notificacion.setClaveEvento(claveEvento);
        notificacion.setTitulo(titulo); notificacion.setMensaje(mensaje); notificacion.setTipo(tipo);
        notificacion.setLeida(false); notificacion.setFechaCreacion(LocalDateTime.now());
        return Optional.of(notificacionRepository.save(notificacion));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificacionRespuestaDTO> listar(Long usuarioId) {
        validarUsuario(usuarioId);
        return notificacionRepository.findByUsuario_IdOrderByFechaCreacionDescIdDesc(usuarioId)
                .stream().map(this::respuesta).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long contarNoLeidas(Long usuarioId) {
        validarUsuario(usuarioId);
        return notificacionRepository.contarNoLeidas(usuarioId);
    }

    @Override
    @Transactional
    public NotificacionRespuestaDTO marcarLeida(Long usuarioId, Integer notificacionId) {
        validarUsuario(usuarioId);
        if (notificacionId == null || notificacionId <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de notificación inválido");
        Notificacion n = notificacionRepository.findByIdAndUsuario_Id(notificacionId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada"));
        if (!Boolean.TRUE.equals(n.getLeida())) {
            n.setLeida(true);
            n = notificacionRepository.save(n);
        }
        return respuesta(n);
    }

    @Override
    @Transactional
    public int marcarTodasLeidas(Long usuarioId) {
        validarUsuario(usuarioId);
        int actualizadas = 0;
        for (Notificacion n : notificacionRepository.findByUsuario_IdOrderByFechaCreacionDescIdDesc(usuarioId)) {
            if (!Boolean.TRUE.equals(n.getLeida())) {
                n.setLeida(true); notificacionRepository.save(n); actualizadas++;
            }
        }
        return actualizadas;
    }

    private void validarUsuario(Long usuarioId) {
        if (usuarioId == null || usuarioId <= 0)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere autenticación");
    }

    private NotificacionRespuestaDTO respuesta(Notificacion n) {
        return new NotificacionRespuestaDTO(n.getId(), n.getTipo(), n.getTitulo(), n.getMensaje(),
                Boolean.TRUE.equals(n.getLeida()), n.getFechaCreacion());
    }
}
