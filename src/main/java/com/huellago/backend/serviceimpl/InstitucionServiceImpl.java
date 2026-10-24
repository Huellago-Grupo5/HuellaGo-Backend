package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.InstitucionRegistroDTO;
import com.huellago.backend.dtos.InstitucionRespuestaDTO;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.InstitucionRepository;
import com.huellago.backend.repositories.UsuarioInstitucionRepository;
import com.huellago.backend.repositories.UsuarioRepository;
import com.huellago.backend.services.InstitucionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class InstitucionServiceImpl implements InstitucionService {
    private final InstitucionRepository instituciones;
    private final UsuarioInstitucionRepository afiliaciones;
    private final UsuarioRepository usuarios;

    public InstitucionServiceImpl(InstitucionRepository instituciones,
            UsuarioInstitucionRepository afiliaciones, UsuarioRepository usuarios) {
        this.instituciones = instituciones;
        this.afiliaciones = afiliaciones;
        this.usuarios = usuarios;
    }

    @Override
    @Transactional
    public InstitucionRespuestaDTO registrar(Long usuarioId, InstitucionRegistroDTO datos) {
        Usuario representante = usuarios.findById(usuarioId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no disponible"));
        String nombre = datos.nombre().trim();
        if (nombre.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El nombre debe tener al menos 3 caracteres sin contar espacios exteriores");
        }
        String pais = datos.pais().trim().toUpperCase(Locale.ROOT);
        String identificador = datos.identificadorFiscal().trim().toUpperCase(Locale.ROOT);
        if (afiliaciones.existsByUsuario_IdAndInstitucion_PaisAndInstitucion_IdentificadorFiscal(
                usuarioId, pais, identificador)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El usuario ya está afiliado a esta institución");
        }
        if (instituciones.existsByPaisAndIdentificadorFiscal(pais, identificador)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La institución ya está registrada");
        }

        Institucion institucion = new Institucion();
        institucion.setTipo(datos.tipo());
        institucion.setNombre(nombre);
        institucion.setPais(pais);
        institucion.setIdentificadorFiscal(identificador);
        institucion.setCorreoContacto(datos.correoContacto().trim().toLowerCase(Locale.ROOT));
        institucion.setFechaRegistro(LocalDateTime.now());
        try {
            institucion = instituciones.saveAndFlush(institucion);
            UsuarioInstitucion afiliacion = new UsuarioInstitucion();
            afiliacion.setInstitucion(institucion);
            afiliacion.setUsuario(representante);
            afiliacion.setRol(RolInstitucional.ADMIN_INSTITUCIONAL);
            afiliacion.setFechaAfiliacion(LocalDateTime.now());
            afiliaciones.saveAndFlush(afiliacion);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La institución o su afiliación ya está registrada");
        }
        return respuesta(institucion, usuarioId);
    }

    @Override
    @Transactional(readOnly = true)
    public InstitucionRespuestaDTO consultarAdministracion(Long usuarioId, Long institucionId) {
        Institucion institucion = instituciones.findById(institucionId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Institución no encontrada"));
        if (!afiliaciones.existsByUsuario_IdAndInstitucion_IdAndRol(
                usuarioId, institucionId, RolInstitucional.ADMIN_INSTITUCIONAL)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes acceso administrativo a esta institución");
        }
        return respuesta(institucion, usuarioId);
    }

    private InstitucionRespuestaDTO respuesta(Institucion institucion, Long representanteId) {
        return new InstitucionRespuestaDTO(institucion.getId(), institucion.getTipo(),
                institucion.getNombre(), institucion.getPais(), institucion.getIdentificadorFiscal(),
                institucion.getCorreoContacto(), institucion.getFechaRegistro(), representanteId,
                RolInstitucional.ADMIN_INSTITUCIONAL);
    }
}
