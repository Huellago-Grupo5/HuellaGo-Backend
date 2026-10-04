package com.huellago.backend.serviceimpl;

import com.huellago.backend.dto.NotificacionesDTO;
import com.huellago.backend.dto.UsuarioPerfilDTO;
import com.huellago.backend.dto.UsuarioRegistroDTO;
import com.huellago.backend.dto.VisibilidadDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.UsuarioRepository;
import com.huellago.backend.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public Usuario registrar(UsuarioRegistroDTO dto) {
        if (usuarioRepository.existsByCorreo(dto.getCorreo())) {
            return null;
        }
        Usuario usuario = new Usuario();
        usuario.setNombres(dto.getNombres());
        usuario.setApellidos(dto.getApellidos());
        usuario.setCorreo(dto.getCorreo());
        usuario.setContrasena(dto.getContrasena());
        usuario.setEcoPuntos(0);
        usuario.setNivel(1);
        usuario.setNotificacionesActivas(true);
        usuario.setVisibilidadComunidad(true);
        usuario.setFechaCreacion(LocalDateTime.now());
        usuario.setFechaActualizacion(LocalDateTime.now());

        return usuarioRepository.save(usuario);
    }

    @Override
    public Usuario actualizarPerfil(UsuarioPerfilDTO dto) {
        Usuario usuario = usuarioRepository.findById(dto.getId()).orElse(null);
        if (usuario == null) {
            return null;
        }
        usuario.setNombres(dto.getNombres());
        usuario.setApellidos(dto.getApellidos());
        usuario.setFechaActualizacion(LocalDateTime.now());

        return usuarioRepository.save(usuario);
    }

    @Override
    public Usuario cambiarPreferenciaNotificaciones(NotificacionesDTO dto) {
        Usuario usuario = usuarioRepository.findById(dto.getId()).orElse(null);
        if (usuario == null) {
            return null;
        }
        usuario.setNotificacionesActivas(dto.getNotificacionesActivas());
        usuario.setFechaActualizacion(LocalDateTime.now());

        return usuarioRepository.save(usuario);
    }

    @Override
    public Usuario cambiarVisibilidadComunitaria(VisibilidadDTO dto) {
        Usuario usuario = usuarioRepository.findById(dto.getId()).orElse(null);
        if (usuario == null) {
            return null;
        }
        usuarioRepository.actualizarVisibilidadNativa(dto.getId(), dto.getVisibilidadComunidad());
        return usuarioRepository.findById(dto.getId()).orElse(null);
    }
}