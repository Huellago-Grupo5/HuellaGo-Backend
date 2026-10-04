package com.huellago.backend.services;

import com.huellago.backend.dtos.UsuarioRegistroDTO;
import com.huellago.backend.dtos.UsuarioRespuestaDTO;
import com.huellago.backend.dtos.RecuperarContrasenaDTO;
import com.huellago.backend.dtos.RestablecerContrasenaDTO;
import com.huellago.backend.dtos.TokenDTO;
import com.huellago.backend.dtos.UsuarioPerfilDTO;
import com.huellago.backend.entities.Usuario;

public interface UsuarioService {
    public UsuarioRespuestaDTO registrar(UsuarioRegistroDTO usuarioRegistroDTO);
    public Usuario buscarPorCorreo(String correo);
    public TokenDTO solicitarRecuperacion(RecuperarContrasenaDTO recuperarContrasenaDTO);
    public void restablecerContrasena(RestablecerContrasenaDTO restablecerContrasenaDTO);
    public UsuarioRespuestaDTO actualizarPerfil(String correoAutenticado, UsuarioPerfilDTO usuarioPerfilDTO);
}
