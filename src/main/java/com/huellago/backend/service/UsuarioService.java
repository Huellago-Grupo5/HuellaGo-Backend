package com.huellago.backend.service;

import com.huellago.backend.dto.NotificacionesDTO;
import com.huellago.backend.dto.UsuarioPerfilDTO;
import com.huellago.backend.dto.UsuarioRegistroDTO;
import com.huellago.backend.dto.VisibilidadDTO;
import com.huellago.backend.entities.Usuario;

public interface UsuarioService {
    Usuario registrar(UsuarioRegistroDTO dto);
    Usuario actualizarPerfil(UsuarioPerfilDTO dto);
    Usuario cambiarPreferenciaNotificaciones(NotificacionesDTO dto);
    Usuario cambiarVisibilidadComunitaria(VisibilidadDTO dto);
}