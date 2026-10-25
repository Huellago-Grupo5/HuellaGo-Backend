package com.huellago.backend.security;

import com.huellago.backend.entities.RolInstitucional;
import com.huellago.backend.repositories.UsuarioInstitucionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AutorizacionInstitucionalService {
    private final UsuarioInstitucionRepository afiliaciones;

    public AutorizacionInstitucionalService(UsuarioInstitucionRepository afiliaciones) {
        this.afiliaciones = afiliaciones;
    }

    public boolean esMiembro(Long usuarioId, Long institucionId) {
        return usuarioId != null && afiliaciones.existsByUsuario_IdAndInstitucion_Id(usuarioId, institucionId);
    }

    public void exigirMiembro(Long usuarioId, Long institucionId) {
        if (!esMiembro(usuarioId, institucionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a esta institución");
        }
    }

    @Transactional(readOnly = true)
    public void exigirAdministradorConsulta(Long usuarioId, Long institucionId) {
        if (usuarioId == null || !afiliaciones.existsByUsuario_IdAndInstitucion_IdAndRol(
                usuarioId, institucionId, RolInstitucional.ADMIN_INSTITUCIONAL)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso administrativo a esta institución");
        }
    }

    @Transactional
    public void exigirAdministrador(Long usuarioId, Long institucionId) {
        var vinculo = afiliaciones.bloquearVinculo(usuarioId, institucionId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso administrativo a esta institución"));
        if (vinculo.getRol() != RolInstitucional.ADMIN_INSTITUCIONAL) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso administrativo a esta institución");
        }
    }

    @Transactional
    public void exigirMiembroParaParticipar(Long usuarioId, Long institucionId) {
        afiliaciones.bloquearVinculo(usuarioId, institucionId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a esta institución"));
    }
}
