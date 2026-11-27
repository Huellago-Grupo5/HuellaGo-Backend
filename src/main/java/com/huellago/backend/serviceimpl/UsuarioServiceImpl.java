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
import com.huellago.backend.dtos.PreferenciasVisualesDTO;
import com.huellago.backend.dtos.RankingComunitarioDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.entities.Sesion;
import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.repositories.UsuarioRepository;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    JwtUtilService jwtUtilService;

    @Autowired
    SesionRepository sesionRepository;

    @Autowired
    com.huellago.backend.services.RecuperacionContrasenaService recuperacionContrasenaService;

    @jakarta.persistence.PersistenceContext
    jakarta.persistence.EntityManager entityManager;

    @Autowired
    com.huellago.backend.repositories.RecuperacionContrasenaRepository recuperaciones;


    @Override
    @Transactional
    public UsuarioRespuestaDTO registrar(UsuarioRegistroDTO usuarioRegistroDTO) {
        if (usuarioRegistroDTO != null) {
            usuarioRegistroDTO.setNombres(usuarioRegistroDTO.getNombres());
            usuarioRegistroDTO.setApellidos(usuarioRegistroDTO.getApellidos());
            usuarioRegistroDTO.setCorreo(usuarioRegistroDTO.getCorreo());
        }
        com.huellago.backend.security.ValidacionCuenta.exigir(usuarioRegistroDTO);
        if (usuarioRepository.existeCorreoNormalizado(usuarioRegistroDTO.getCorreo())) {
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

        Usuario usuarioGuardado;
        try { usuarioGuardado = usuarioRepository.saveAndFlush(usuario); }
        catch (org.springframework.dao.DataIntegrityViolationException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        }
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
        return usuarioRepository.findByCorreo(com.huellago.backend.security.ValidacionCuenta.correo(correo));
    }

    @Override
    public com.huellago.backend.dtos.RecuperacionRespuestaDTO solicitarRecuperacion(RecuperarContrasenaDTO dto) {
        return recuperacionContrasenaService.solicitar(dto);
    }

    @Override
    public void restablecerContrasena(RestablecerContrasenaDTO dto) {
        recuperacionContrasenaService.restablecer(dto);
    }

    @Override
    @Transactional
    public UsuarioRespuestaDTO actualizarPerfil(String correoAutenticado, UsuarioPerfilDTO dto) {
        if (dto != null) {
            dto.setNombres(dto.getNombres()); dto.setApellidos(dto.getApellidos()); dto.setCorreo(dto.getCorreo());
        }
        com.huellago.backend.security.ValidacionCuenta.exigir(dto);
        Usuario usuario = buscarPorCorreo(correoAutenticado);
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        usuarioRepository.bloquearParaRecomendaciones(usuario.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        entityManager.refresh(usuario);
        if (!com.huellago.backend.security.ValidacionCuenta.correo(correoAutenticado)
                .equals(com.huellago.backend.security.ValidacionCuenta.correo(usuario.getCorreo()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión nuevamente");
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

        Usuario usuarioActualizado;
        try {
            usuarioActualizado = usuarioRepository.save(usuario);
            usuarioRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        }
        if (cambioCorreo) {
            recuperaciones.eliminarDelUsuario(usuario.getId());
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
    @Transactional(readOnly = true)
    public UsuarioRespuestaDTO obtenerPerfil(Usuario usuarioAutenticado) {
        Usuario usuario = obtenerUsuarioActual(usuarioAutenticado);
        return new UsuarioRespuestaDTO(usuario.getId(), usuario.getNombres(), usuario.getApellidos(),
                usuario.getCorreo(), usuario.getEcoPuntos(), usuario.getNivel(),
                usuario.getNotificacionesActivas(), usuario.getVisibilidadComunidad(),
                usuario.getFechaCreacion(), usuario.getFechaActualizacion());
    }

    @Override
    @Transactional
    public void cambiarContrasena(Usuario usuarioAutenticado, String contrasenaActual,
            String contrasenaNueva) {
        Usuario usuario = obtenerUsuarioActual(usuarioAutenticado);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (!encoder.matches(contrasenaActual, usuario.getContrasena())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña actual no es correcta");
        }
        if (encoder.matches(contrasenaNueva, usuario.getContrasena())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La nueva contraseña debe ser diferente a la actual");
        }
        usuario.setContrasena(encoder.encode(contrasenaNueva));
        usuario.setFechaActualizacion(LocalDateTime.now());
        usuarioRepository.save(usuario);
    }

    @Override
    public EcoPuntosRespuestaDTO otorgarEcoPuntos(
            Usuario usuario, EcoPuntosAccionDTO dto) {
        if (dto == null || dto.getAccion() == null || dto.getAccion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La acción es obligatoria");
        }
        // No existe evidencia persistida ni una regla de validación para acciones autodeclaradas.
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No se pueden otorgar eco-puntos por acciones sin verificación");
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

    @Override
    @Transactional(readOnly = true)
    public PreferenciasVisualesDTO obtenerPreferenciasVisuales(Usuario autenticado) {
        Usuario usuario = obtenerUsuarioActual(autenticado);
        return mapearPreferenciasVisuales(usuario);
    }

    @Override
    @Transactional
    public PreferenciasVisualesDTO actualizarPreferenciasVisuales(Usuario autenticado, PreferenciasVisualesDTO dto) {
        if (dto == null || dto.tema() == null || dto.unidades() == null
                || !java.util.Set.of("claro", "oscuro").contains(dto.tema())
                || !java.util.Set.of("metric", "imperial").contains(dto.unidades())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preferencias visuales inválidas");
        }
        Usuario usuario = obtenerUsuarioActual(autenticado);
        usuario.setTemaPreferido(dto.tema());
        usuario.setUnidadesPreferidas(dto.unidades());
        usuario.setFechaActualizacion(LocalDateTime.now());
        usuarioRepository.save(usuario);
        return mapearPreferenciasVisuales(usuario);
    }

    private PreferenciasVisualesDTO mapearPreferenciasVisuales(Usuario usuario) {
        return new PreferenciasVisualesDTO(usuario.getTemaPreferido() == null ? "claro" : usuario.getTemaPreferido(),
                usuario.getUnidadesPreferidas() == null ? "metric" : usuario.getUnidadesPreferidas());
    }

    @Override
    @Transactional(readOnly = true)
    public RankingComunitarioDTO obtenerRankingComunitario(Usuario usuarioAutenticado) {
        if (usuarioAutenticado == null || usuarioAutenticado.getId() == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere autenticación");

        var filas = usuarioRepository.listarRankingComunitario();
        var participantes = new java.util.ArrayList<RankingComunitarioDTO.Participante>(filas.size());
        Integer miPosicion = null;
        for (var fila : filas) {
            int posicion = participantes.size() + 1;
            String nombre = fila.getNombres();
            if (nombre == null || nombre.isBlank()) nombre = "Usuario de HuellaGo";
            participantes.add(new RankingComunitarioDTO.Participante(posicion, nombre.trim(),
                    fila.getEcoPuntos(), fila.getNivel()));
            if (usuarioAutenticado.getId().equals(fila.getId())) miPosicion = posicion;
        }
        String mensaje = participantes.isEmpty() ? "No hay participantes visibles en el ranking."
                : participantes.size() == 1 ? "Aún no hay otros participantes visibles para comparar."
                : null;
        return new RankingComunitarioDTO(participantes, miPosicion, mensaje);
    }

}
