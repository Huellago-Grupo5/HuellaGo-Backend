package com.huellago.backend.services;

import com.huellago.backend.entities.Notificacion;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.dtos.NotificacionRespuestaDTO;

import java.util.Optional;

public interface NotificacionService {
    Optional<Notificacion> crearOpcional(
            Usuario usuario, String titulo, String mensaje, String tipo);
    Optional<Notificacion> crearPorEvento(Usuario usuario, String claveEvento,
            String titulo, String mensaje, String tipo);
    java.util.List<NotificacionRespuestaDTO> listar(Long usuarioId);
    long contarNoLeidas(Long usuarioId);
    NotificacionRespuestaDTO marcarLeida(Long usuarioId, Integer notificacionId);
    int marcarTodasLeidas(Long usuarioId);
}
