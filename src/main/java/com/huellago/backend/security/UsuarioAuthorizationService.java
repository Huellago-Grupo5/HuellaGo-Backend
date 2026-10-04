package com.huellago.backend.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UsuarioAuthorizationService {

    public void validarPropietario(Long usuarioSolicitado, UserSecurity usuarioAutenticado) {
        if (usuarioAutenticado == null
                || usuarioAutenticado.getUser() == null
                || !usuarioAutenticado.getUser().getId().equals(usuarioSolicitado)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No tiene autorización para acceder a este usuario");
        }
    }
}
