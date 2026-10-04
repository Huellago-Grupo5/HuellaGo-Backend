package com.huellago.backend.services;

import com.huellago.backend.security.UserSecurity;

public interface SesionService {
    void registrarSesion(UserSecurity userSecurity, String token);

    void cerrarSesion(String token);
}
