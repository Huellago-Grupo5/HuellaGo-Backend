package com.huellago.backend.services;

import com.huellago.backend.entities.Notificacion;
import com.huellago.backend.entities.Usuario;

import java.util.Optional;

public interface NotificacionService {
    Optional<Notificacion> crearOpcional(
            Usuario usuario, String titulo, String mensaje, String tipo);
}
