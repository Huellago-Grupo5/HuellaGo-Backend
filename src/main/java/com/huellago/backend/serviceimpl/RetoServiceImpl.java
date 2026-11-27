package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.RetoRespuestaDTO;
import com.huellago.backend.dtos.UsuarioRetoRespuestaDTO;
import com.huellago.backend.dtos.ActualizarProgresoDTO;
import com.huellago.backend.dtos.RegistrarAvanceDTO;
import com.huellago.backend.dtos.AvanceRetoRespuestaDTO;
import com.huellago.backend.entities.Reto;
import com.huellago.backend.entities.AvanceReto;
import com.huellago.backend.entities.TipoReto;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.entities.UsuarioReto;
import com.huellago.backend.repositories.RetoRepository;
import com.huellago.backend.repositories.UsuarioRetoRepository;
import com.huellago.backend.repositories.AvanceRetoRepository;
import com.huellago.backend.repositories.UsuarioRepository;
import com.huellago.backend.services.RetoService;
import com.huellago.backend.services.UsuarioService;
import com.huellago.backend.services.InsigniaService;
import com.huellago.backend.services.NotificacionService;
import com.huellago.backend.events.InformacionAmbientalActualizada;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Objects;

@Service
public class RetoServiceImpl implements RetoService {

    @Autowired
    ApplicationEventPublisher eventos;

    @Autowired
    RetoRepository retoRepository;

    @Autowired
    UsuarioRetoRepository usuarioRetoRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    UsuarioService usuarioService;

    @Autowired
    InsigniaService insigniaService;
    @Autowired
    NotificacionService notificacionService;

    @Autowired
    AvanceRetoRepository avanceRetoRepository;

    @Override
    public List<RetoRespuestaDTO> listarRetosActivos() {
        LocalDate hoy = LocalDate.now();
        return retoRepository.findByActivoTrueOrderByFechaInicioAsc()
                .stream()
                .filter(reto -> reto.getTipo() != TipoReto.COLECTIVO
                        && reto.getRecomendacionOrigen() == null && disponible(reto, hoy))
                .map(this::convertirADTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RetoRespuestaDTO> listarRetosDisponibles(Long usuarioId, String clasificacion) {
        if (usuarioId == null || usuarioId <= 0)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere autenticación");
        String filtro = clasificacion == null ? null : clasificacion.trim().toUpperCase(Locale.ROOT);
        if (filtro != null && !Set.of("DIARIO", "SEMANAL", "PERSONALIZADO", "SIN_CLASIFICACION").contains(filtro))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clasificación de reto no válida");
        if ("PERSONALIZADO".equals(filtro)) {
            LocalDate hoy = LocalDate.now();
            return usuarioRetoRepository.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaAceptacionDesc(usuarioId,"activo")
                    .stream().filter(p -> p.getUsuario()!=null && Objects.equals(usuarioId,p.getUsuario().getId()))
                    .map(UsuarioReto::getReto).filter(Objects::nonNull)
                    .filter(r -> r.getRecomendacionOrigen()!=null && r.getRecomendacionOrigen().getUsuario()!=null
                            && Objects.equals(usuarioId,r.getRecomendacionOrigen().getUsuario().getId()) && disponible(r,hoy))
                    .map(this::convertirADTO).distinct().toList();
        }
        Set<Integer> yaAceptados = usuarioRetoRepository.findByUsuario_Id(usuarioId).stream()
                .map(p -> p.getReto() == null ? null : p.getReto().getId())
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        return listarRetosActivos().stream()
                .filter(r -> !yaAceptados.contains(r.getId()))
                .filter(r -> filtro == null || filtro.equals(r.getClasificacion())).toList();
    }

    private String clasificacion(Reto reto) {
        if (reto.getTipo()==TipoReto.COLECTIVO) return "COLECTIVO";
        if (reto.getRecomendacionOrigen()!=null) return "PERSONALIZADO";
        if (reto.getFechaInicio()!=null && reto.getFechaFin()!=null) {
            if (reto.getFechaInicio().equals(reto.getFechaFin())) return "DIARIO";
            if (reto.getFechaInicio().plusDays(6).equals(reto.getFechaFin())) return "SEMANAL";
        }
        return "SIN_CLASIFICACION";
    }

    @Override
    @Transactional
    public UsuarioRetoRespuestaDTO aceptarReto(Integer retoId, Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere autenticación");
        }
        retoRepository.bloquearParaAceptacion(retoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reto no encontrado"));
        Reto reto = retoRepository.findById(retoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reto no encontrado"));

        if (reto.getTipo() == TipoReto.COLECTIVO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Use la ruta de participación colectiva");
        }

        if (reto.getRecomendacionOrigen() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reto no encontrado");
        }

        if (!disponible(reto, LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "El reto no está disponible en la fecha actual");
        }

        if (usuarioRetoRepository.existsByUsuario_IdAndReto_Id(usuario.getId(), retoId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "El usuario ya aceptó este reto");
        }

        UsuarioReto usuarioReto = new UsuarioReto();
        usuarioReto.setUsuario(usuario);
        usuarioReto.setReto(reto);
        usuarioReto.setEstado("activo");
        usuarioReto.setProgreso(BigDecimal.ZERO);
        usuarioReto.setFechaAceptacion(LocalDateTime.now());
        usuarioReto.setFechaCompletado(null);

        return convertirARespuesta(usuarioRetoRepository.saveAndFlush(usuarioReto));
    }

    private boolean disponible(Reto reto, LocalDate hoy) {
        LocalDate inicio = reto.getFechaInicio();
        LocalDate fin = reto.getFechaFin();
        return Boolean.TRUE.equals(reto.getActivo())
                && (inicio == null || !hoy.isBefore(inicio))
                && (fin == null || !hoy.isAfter(fin))
                && (inicio == null || fin == null || !inicio.isAfter(fin));
    }

    @Override
    public List<UsuarioRetoRespuestaDTO> listarRetosActivos(Long usuarioId) {
        return usuarioRetoRepository
                .findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaAceptacionDesc(usuarioId, "activo")
                .stream()
                .map(this::convertirARespuesta)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioRetoRespuestaDTO> listarRetosCompletados(Long usuarioId) {
        return usuarioRetoRepository.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDesc(
                usuarioId, "completado").stream().map(this::convertirARespuesta).toList();
    }

    @Override
    @Transactional
    public UsuarioRetoRespuestaDTO registrarAvance(Integer usuarioRetoId, Long usuarioId, RegistrarAvanceDTO dto) {
        if (dto == null || dto.cantidad() == null || dto.claveSolicitud() == null
                || dto.cantidad().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cantidad positiva y la clave de solicitud son obligatorias");
        }
        final BigDecimal cantidad;
        try {
            cantidad = dto.cantidad().setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad admite hasta tres decimales");
        }
        usuarioRetoRepository.bloquearParticipacion(usuarioRetoId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participación no encontrada"));
        UsuarioReto participacion = usuarioRetoRepository.findByIdAndUsuario_Id(usuarioRetoId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participación no encontrada"));
        if (avanceRetoRepository.findByParticipacion_IdAndClaveSolicitud(usuarioRetoId, dto.claveSolicitud()).isPresent()) {
            return convertirARespuesta(participacion);
        }
        if (!"activo".equalsIgnoreCase(participacion.getEstado()) || participacion.getFechaCompletado() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La participación no está activa");
        }
        Reto reto = participacion.getReto();
        if (reto == null || reto.getMetaCantidad() == null || reto.getMetaCantidad().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El reto no tiene una meta cuantitativa válida");
        }
        BigDecimal acumulado = participacion.getCantidadAcumulada() == null
                ? BigDecimal.ZERO : participacion.getCantidadAcumulada();
        BigDecimal total = acumulado.add(cantidad).setScale(3, RoundingMode.UNNECESSARY);
        if (total.compareTo(reto.getMetaCantidad()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cantidad supera lo que falta para alcanzar la meta");
        }
        AvanceReto avance = new AvanceReto();
        avance.setParticipacion(participacion);
        avance.setCantidad(cantidad);
        avance.setRegistradoEn(LocalDateTime.now());
        avance.setClaveSolicitud(dto.claveSolicitud());
        avanceRetoRepository.save(avance);
        participacion.setCantidadAcumulada(total);
        participacion.setProgreso(total.multiply(BigDecimal.valueOf(100))
                .divide(reto.getMetaCantidad(), 2, RoundingMode.HALF_UP));
        return convertirARespuesta(usuarioRetoRepository.save(participacion));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvanceRetoRespuestaDTO> listarAvances(Integer usuarioRetoId, Long usuarioId) {
        usuarioRetoRepository.findByIdAndUsuario_Id(usuarioRetoId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participación no encontrada"));
        return avanceRetoRepository
                .findByParticipacion_IdAndParticipacion_Usuario_IdOrderByRegistradoEnDescIdDesc(usuarioRetoId, usuarioId)
                .stream().map(a -> new AvanceRetoRespuestaDTO(a.getId(), a.getCantidad(), a.getRegistradoEn()))
                .toList();
    }

    @Override
    @Transactional
    public UsuarioRetoRespuestaDTO actualizarProgreso(
            Integer usuarioRetoId, Long usuarioId, ActualizarProgresoDTO dto) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "El progreso se registra como cantidades mediante POST /retos-activos/{id}/avances");
    }

    @Override
    @Transactional
    public UsuarioRetoRespuestaDTO completarReto(Integer usuarioRetoId, Long usuarioId) {
        usuarioRetoRepository.bloquearParticipacion(usuarioRetoId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Participación no encontrada"));
        // También serializa las recompensas de retos distintos del mismo usuario.
        usuarioRepository.bloquearParaRecomendaciones(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        UsuarioReto usuarioReto = usuarioRetoRepository
                .findByIdAndUsuario_Id(usuarioRetoId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Participación no encontrada"));

        if (usuarioReto.getReto() != null && usuarioReto.getReto().getTipo() == TipoReto.COLECTIVO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Los retos colectivos se cierran automáticamente sin recompensas individuales");
        }

        if (!"activo".equalsIgnoreCase(usuarioReto.getEstado())
                || usuarioReto.getFechaCompletado() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La participación ya está completada o no está activa");
        }

        if (usuarioReto.getProgreso() == null
                || usuarioReto.getProgreso().compareTo(BigDecimal.valueOf(100)) != 0
                || usuarioReto.getReto() == null || usuarioReto.getReto().getMetaCantidad() == null
                || usuarioReto.getCantidadAcumulada() == null
                || usuarioReto.getCantidadAcumulada().compareTo(usuarioReto.getReto().getMetaCantidad()) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El progreso debe ser 100 para completar el reto");
        }

        if (usuarioReto.getReto() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La participación no tiene un reto asociado");
        }

        usuarioReto.setEstado("completado");
        usuarioReto.setProgreso(BigDecimal.valueOf(100));
        usuarioReto.setFechaCompletado(LocalDateTime.now());

        int puntosActuales = usuarioReto.getUsuario().getEcoPuntos() == null
                ? 0 : usuarioReto.getUsuario().getEcoPuntos();
        int puntosRecompensa = usuarioReto.getReto().getPuntosRecompensa() == null
                ? 0 : usuarioReto.getReto().getPuntosRecompensa();
        usuarioReto.getUsuario().setEcoPuntos(puntosActuales + puntosRecompensa);
        Integer nivelAnterior = usuarioReto.getUsuario().getNivel();
        Usuario usuarioActualizado = usuarioService.actualizarNivel(usuarioReto.getUsuario());
        var insigniasDesbloqueadas = new java.util.ArrayList<>(insigniaService.evaluarYDesbloquearInsignias(usuarioActualizado));
        insigniasDesbloqueadas.addAll(insigniaService.evaluarYDesbloquearPorReto(usuarioActualizado,usuarioReto.getReto()));

        UsuarioRetoRespuestaDTO respuesta = convertirARespuesta(usuarioRetoRepository.save(usuarioReto));
        respuesta.setInsigniasDesbloqueadas(insigniasDesbloqueadas);
        notificacionService.crearPorEvento(usuarioActualizado, "RETO_COMPLETADO:"+usuarioReto.getId(),
                "Reto completado", "Completaste el reto: "+usuarioReto.getReto().getTitulo()
                        + (puntosRecompensa > 0 ? ". Ganaste " + puntosRecompensa + " eco puntos." : "."),
                "RETO_COMPLETADO");
        for (var insignia : insigniasDesbloqueadas)
            notificacionService.crearPorEvento(usuarioActualizado, "INSIGNIA:"+insignia.getId(),
                    "Nueva insignia", "Obtuviste la insignia: "+insignia.getNombre(), "INSIGNIA");
        if (usuarioActualizado.getNivel()!=null && nivelAnterior!=null
                && usuarioActualizado.getNivel()>nivelAnterior)
            notificacionService.crearPorEvento(usuarioActualizado, "NIVEL_ALCANZADO:"+usuarioActualizado.getNivel(),
                    "Nuevo nivel", "Alcanzaste el nivel "+usuarioActualizado.getNivel(), "NIVEL");
        eventos.publishEvent(new InformacionAmbientalActualizada(usuarioId));
        return respuesta;
    }

    private UsuarioRetoRespuestaDTO convertirARespuesta(UsuarioReto usuarioReto) {
        UsuarioRetoRespuestaDTO respuesta = new UsuarioRetoRespuestaDTO(
                usuarioReto.getId(),
                usuarioReto.getUsuario().getId(),
                usuarioReto.getReto().getId(),
                usuarioReto.getEstado(),
                usuarioReto.getProgreso(),
                usuarioReto.getFechaAceptacion(),
                usuarioReto.getFechaCompletado()
        );
        respuesta.setCantidadAcumulada(usuarioReto.getCantidadAcumulada() == null
                ? BigDecimal.ZERO : usuarioReto.getCantidadAcumulada());
        respuesta.setReto(convertirADTO(usuarioReto.getReto()));
        return respuesta;
    }

    private RetoRespuestaDTO convertirADTO(Reto reto) {
        var dto = new RetoRespuestaDTO(
                reto.getId(),
                reto.getTitulo(),
                reto.getDescripcion(),
                reto.getPuntosRecompensa(),
                reto.getDificultad(),
                reto.getFechaInicio(),
                reto.getFechaFin(),
                reto.getActivo(),
                reto.getCategoria() != null ? reto.getCategoria().getId() : null,
                reto.getCategoria() != null ? reto.getCategoria().getNombre() : null
        );
        dto.setClasificacion(clasificacion(reto));
        dto.setMetaCantidad(reto.getMetaCantidad());
        dto.setUnidadMedida(reto.getUnidadMedida());
        return dto;
    }
}
