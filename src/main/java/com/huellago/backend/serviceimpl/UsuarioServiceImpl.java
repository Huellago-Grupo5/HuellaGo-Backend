package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.UsuarioRegistroDTO;
import com.huellago.backend.dtos.UsuarioRespuestaDTO;
import com.huellago.backend.dtos.RecuperarContrasenaDTO;
import com.huellago.backend.dtos.RestablecerContrasenaDTO;
import com.huellago.backend.dtos.TokenDTO;
import com.huellago.backend.dtos.UsuarioPerfilDTO;
import com.huellago.backend.dtos.EcoPuntosAccionDTO;
import com.huellago.backend.dtos.EcoPuntosRespuestaDTO;
import com.huellago.backend.dtos.NivelRespuestaDTO;
import com.huellago.backend.dtos.PreferenciaNotificacionActualizarDTO;
import com.huellago.backend.dtos.PreferenciaNotificacionDTO;
import com.huellago.backend.dtos.PrivacidadActualizarDTO;
import com.huellago.backend.dtos.PrivacidadDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.entities.Sesion;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.repositories.UsuarioRepository;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.InsigniaService;
import com.huellago.backend.services.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.List;

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

    @Autowired
    InsigniaService insigniaService;

    @Autowired
    SesionRepository sesionRepository;


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
    @Transactional
    public UsuarioRespuestaDTO actualizarPerfil(String correoAutenticado, UsuarioPerfilDTO dto) {
        Usuario usuario = buscarPorCorreo(correoAutenticado);
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        String nombres = dto.getNombres().trim();
        String apellidos = dto.getApellidos().trim();
        String correo = dto.getCorreo().trim().toLowerCase(Locale.ROOT);
        boolean cambioCorreo = !correo.equalsIgnoreCase(usuario.getCorreo());

        if (cambioCorreo
                && usuarioRepository.existsByCorreoIgnoreCaseAndIdNot(correo, usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        }

        usuario.setNombres(nombres);
        usuario.setApellidos(apellidos);
        usuario.setCorreo(correo);
        usuario.setFechaActualizacion(LocalDateTime.now());

        Usuario usuarioActualizado = usuarioRepository.save(usuario);
        if (cambioCorreo) {
            List<Sesion> sesionesActivas = sesionRepository
                    .findByUsuario_IdAndActivaTrue(usuario.getId());
            sesionesActivas.forEach(sesion -> sesion.setActiva(false));
            sesionRepository.saveAll(sesionesActivas);
        }
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
        Usuario usuarioActualizado = actualizarNivel(usuario);
        insigniaService.evaluarYDesbloquearInsignias(usuarioActualizado);

        return new EcoPuntosRespuestaDTO(
                usuarioActualizado.getId(),
                usuarioActualizado.getEcoPuntos(),
                puntos,
                accion
        );
    }

    @Override
    public Usuario actualizarNivel(Usuario usuario) {
        int ecoPuntos = usuario.getEcoPuntos() == null ? 0 : usuario.getEcoPuntos();
        usuario.setNivel((ecoPuntos / 100) + 1);
        return usuarioRepository.save(usuario);
    }

    @Override
    public NivelRespuestaDTO obtenerNivel(Usuario usuario) {
        int ecoPuntos = usuario.getEcoPuntos() == null ? 0 : usuario.getEcoPuntos();
        int puntosEnNivel = ecoPuntos % 100;
        return new NivelRespuestaDTO(
                usuario.getId(),
                ecoPuntos,
                (ecoPuntos / 100) + 1,
                puntosEnNivel,
                100 - puntosEnNivel,
                puntosEnNivel
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PreferenciaNotificacionDTO obtenerPreferenciaNotificaciones(
            Usuario usuarioAutenticado) {
        Usuario usuario = obtenerUsuarioActual(usuarioAutenticado);
        return mapearPreferencia(usuario);
    }

    @Override
    @Transactional
    public PreferenciaNotificacionDTO actualizarPreferenciaNotificaciones(
            Usuario usuarioAutenticado, PreferenciaNotificacionActualizarDTO dto) {
        Usuario usuario = obtenerUsuarioActual(usuarioAutenticado);
        usuario.setNotificacionesActivas(dto.getNotificacionesActivas());
        usuario.setFechaActualizacion(LocalDateTime.now());
        return mapearPreferencia(usuarioRepository.save(usuario));
    }

    private Usuario obtenerUsuarioActual(Usuario usuarioAutenticado) {
        return usuarioRepository.findById(usuarioAutenticado.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private PreferenciaNotificacionDTO mapearPreferencia(Usuario usuario) {
        return new PreferenciaNotificacionDTO(
                usuario.getId(),
                usuario.getNotificacionesActivas(),
                usuario.getFechaActualizacion()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PrivacidadDTO obtenerPrivacidad(Usuario usuarioAutenticado) {
        Usuario usuario = obtenerUsuarioActual(usuarioAutenticado);
        return mapearPrivacidad(usuario);
    }

    @Override
    @Transactional
    public PrivacidadDTO actualizarPrivacidad(
            Usuario usuarioAutenticado, PrivacidadActualizarDTO dto) {
        Usuario usuario = obtenerUsuarioActual(usuarioAutenticado);
        usuario.setVisibilidadComunidad(dto.getVisibilidadComunidad());
        usuario.setFechaActualizacion(LocalDateTime.now());
        return mapearPrivacidad(usuarioRepository.save(usuario));
    }

    private PrivacidadDTO mapearPrivacidad(Usuario usuario) {
        return new PrivacidadDTO(
                usuario.getId(),
                usuario.getVisibilidadComunidad(),
                usuario.getFechaActualizacion()
        );
    }

}
