package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.UsuarioRegistroDTO;
import com.huellago.backend.dtos.UsuarioRespuestaDTO;
import com.huellago.backend.dtos.RecuperarContrasenaDTO;
import com.huellago.backend.dtos.RestablecerContrasenaDTO;
import com.huellago.backend.dtos.TokenDTO;
import com.huellago.backend.dtos.UsuarioPerfilDTO;
import com.huellago.backend.dtos.EcoPuntosAccionDTO;
import com.huellago.backend.dtos.EcoPuntosRespuestaDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.UsuarioRepository;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private static final Map<String, Integer> PUNTOS_ACCIONES = Map.of(
            "RECICLAR", 10,
            "USAR_BICICLETA", 15,
            "AHORRAR_ENERGIA", 10
    );

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    JwtUtilService jwtUtilService;


    @Override
    public UsuarioRespuestaDTO registrar(UsuarioRegistroDTO usuarioRegistroDTO) {
        if (usuarioRepository.existsByCorreo(usuarioRegistroDTO.getCorreo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        }

        LocalDateTime ahora = LocalDateTime.now();
        Usuario usuario = new Usuario();
        usuario.setNombres(usuarioRegistroDTO.getNombres());
        usuario.setApellidos(usuarioRegistroDTO.getApellidos());
        usuario.setCorreo(usuarioRegistroDTO.getCorreo());
        usuario.setContrasena(new BCryptPasswordEncoder().encode(usuarioRegistroDTO.getContrasena()));
        usuario.setEcoPuntos(0);
        usuario.setNivel(1);
        usuario.setNotificacionesActivas(true);
        usuario.setVisibilidadComunidad(true);
        usuario.setFechaCreacion(ahora);
        usuario.setFechaActualizacion(ahora);

        Usuario usuarioGuardado = usuarioRepository.save(usuario);
        return new UsuarioRespuestaDTO(
                usuarioGuardado.getId(),
                usuarioGuardado.getNombres(),
                usuarioGuardado.getApellidos(),
                usuarioGuardado.getCorreo(),
                usuarioGuardado.getEcoPuntos(),
                usuarioGuardado.getNivel(),
                usuarioGuardado.getNotificacionesActivas(),
                usuarioGuardado.getVisibilidadComunidad(),
                usuarioGuardado.getFechaCreacion(),
                usuarioGuardado.getFechaActualizacion()
        );
    }

    @Override
    public Usuario buscarPorCorreo(String correo) {
        return usuarioRepository.findByCorreo(correo);
    }

    @Override
    public TokenDTO solicitarRecuperacion(RecuperarContrasenaDTO dto) {
        Usuario usuario = buscarPorCorreo(dto.getCorreo());
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        UserSecurity userSecurity = new UserSecurity(usuario);
        return new TokenDTO(
                jwtUtilService.generatePasswordResetToken(userSecurity),
                usuario.getId(),
                usuario.getCorreo()
        );
    }

    @Override
    public void restablecerContrasena(RestablecerContrasenaDTO dto) {
        if (!jwtUtilService.isPasswordResetToken(dto.getToken())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de recuperación inválido");
        }

        String correo = jwtUtilService.extractUsername(dto.getToken());
        Usuario usuario = buscarPorCorreo(correo);
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        usuario.setContrasena(new BCryptPasswordEncoder().encode(dto.getNuevaContrasena()));
        usuario.setFechaActualizacion(LocalDateTime.now());
        usuarioRepository.save(usuario);
    }

    @Override
    public UsuarioRespuestaDTO actualizarPerfil(String correoAutenticado, UsuarioPerfilDTO dto) {
        Usuario usuario = buscarPorCorreo(correoAutenticado);
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        if (dto.getCorreo() != null && !dto.getCorreo().equalsIgnoreCase(usuario.getCorreo())
                && usuarioRepository.existsByCorreoAndIdNot(dto.getCorreo(), usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        }

        if (dto.getNombres() != null) {
            usuario.setNombres(dto.getNombres());
        }
        if (dto.getApellidos() != null) {
            usuario.setApellidos(dto.getApellidos());
        }
        if (dto.getCorreo() != null) {
            usuario.setCorreo(dto.getCorreo());
        }
        usuario.setFechaActualizacion(LocalDateTime.now());

        Usuario usuarioActualizado = usuarioRepository.save(usuario);
        return new UsuarioRespuestaDTO(
                usuarioActualizado.getId(),
                usuarioActualizado.getNombres(),
                usuarioActualizado.getApellidos(),
                usuarioActualizado.getCorreo(),
                usuarioActualizado.getEcoPuntos(),
                usuarioActualizado.getNivel(),
                usuarioActualizado.getNotificacionesActivas(),
                usuarioActualizado.getVisibilidadComunidad(),
                usuarioActualizado.getFechaCreacion(),
                usuarioActualizado.getFechaActualizacion()
        );
    }

    @Override
    public EcoPuntosRespuestaDTO otorgarEcoPuntos(
            Usuario usuario, EcoPuntosAccionDTO dto) {
        if (dto == null || dto.getAccion() == null || dto.getAccion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La acción es obligatoria");
        }

        String accion = dto.getAccion().trim().toUpperCase(Locale.ROOT);
        Integer puntos = PUNTOS_ACCIONES.get(accion);
        if (puntos == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Acción sostenible no válida");
        }

        int ecoPuntosActuales = usuario.getEcoPuntos() == null ? 0 : usuario.getEcoPuntos();
        usuario.setEcoPuntos(ecoPuntosActuales + puntos);
        Usuario usuarioActualizado = usuarioRepository.save(usuario);

        return new EcoPuntosRespuestaDTO(
                usuarioActualizado.getId(),
                usuarioActualizado.getEcoPuntos(),
                puntos,
                accion
        );
    }

}
