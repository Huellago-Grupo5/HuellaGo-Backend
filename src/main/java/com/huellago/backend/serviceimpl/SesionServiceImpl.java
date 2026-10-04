package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Sesion;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.SesionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class SesionServiceImpl implements SesionService {

    @Autowired
    SesionRepository sesionRepository;

    @Autowired
    JwtUtilService jwtUtilService;

    @Override
    public void registrarSesion(UserSecurity userSecurity, String token) {
        Sesion sesion = new Sesion();
        sesion.setUsuario(userSecurity.getUser());
        sesion.setToken(token);
        sesion.setFechaInicio(LocalDateTime.now());
        sesion.setFechaExpiracion(jwtUtilService.extractExpiration(token)
                .toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        sesion.setActiva(true);
        sesionRepository.save(sesion);
    }

    @Override
    public void cerrarSesion(String token) {
        Sesion sesion = sesionRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Sesión no encontrada"));
        sesion.setActiva(false);
        sesionRepository.save(sesion);
    }
}
