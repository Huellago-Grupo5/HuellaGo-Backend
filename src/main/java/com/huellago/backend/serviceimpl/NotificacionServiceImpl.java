package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Notificacion;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.NotificacionRepository;
import com.huellago.backend.services.NotificacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

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
}
