package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.*;
import com.huellago.backend.services.SesionService;
import org.springframework.security.authentication.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccesoCuentaServiceImpl implements com.huellago.backend.services.AccesoCuentaService {
    private final AuthenticationManager autenticacion;
    private final UsuarioRepository usuarios;
    private final JwtUtilService jwt;
    private final SesionService sesiones;
    private final jakarta.persistence.EntityManager entityManager;
    public AccesoCuentaServiceImpl(AuthenticationManager autenticacion, UsuarioRepository usuarios,
            JwtUtilService jwt, SesionService sesiones, jakarta.persistence.EntityManager entityManager) {
        this.autenticacion = autenticacion; this.usuarios = usuarios; this.jwt = jwt; this.sesiones = sesiones;
        this.entityManager = entityManager;
    }
    @Transactional
    public TokenDTO login(LoginDTO dto) {
        if (dto != null) dto.setCorreo(dto.getCorreo());
        ValidacionCuenta.exigir(dto);
        var usuario = usuarios.findByCorreoNormalizado(dto.getCorreo());
        if (usuario == null) {
            // Mantiene la comprobación BCrypt del proveedor para cuentas inexistentes.
            autenticacion.authenticate(new UsernamePasswordAuthenticationToken(dto.getCorreo(), dto.getContrasena()));
            throw new BadCredentialsException("Credenciales inválidas");
        }
        // Serializa login, cambio de correo y reset: un login con clave antigua no sobrevive a un reset.
        usuarios.bloquearParaRecomendaciones(usuario.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));
        entityManager.refresh(usuario);
        var resultado = autenticacion.authenticate(new UsernamePasswordAuthenticationToken(dto.getCorreo(), dto.getContrasena()));
        UserSecurity principal = (UserSecurity) resultado.getPrincipal();
        String token = jwt.generateToken(principal);
        sesiones.registrarSesion(principal, token);
        return new TokenDTO(token, principal.getUser().getId(), principal.getUsername());
    }
}
