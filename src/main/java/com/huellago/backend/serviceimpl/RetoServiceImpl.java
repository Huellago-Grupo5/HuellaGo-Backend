package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.RetoRespuestaDTO;
import com.huellago.backend.dtos.UsuarioRetoRespuestaDTO;
import com.huellago.backend.dtos.ActualizarProgresoDTO;
import com.huellago.backend.entities.Reto;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.entities.UsuarioReto;
import com.huellago.backend.repositories.RetoRepository;
import com.huellago.backend.repositories.UsuarioRetoRepository;
import com.huellago.backend.repositories.UsuarioRepository;
import com.huellago.backend.services.RetoService;
import com.huellago.backend.services.UsuarioService;
import com.huellago.backend.services.InsigniaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Service
public class RetoServiceImpl implements RetoService {

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

    @Override
    public List<RetoRespuestaDTO> listarRetosActivos() {
        LocalDate hoy = LocalDate.now();
        return retoRepository.findByActivoTrueOrderByFechaInicioAsc()
                .stream()
                .filter(reto -> disponible(reto, hoy))
                .map(this::convertirADTO)
                .toList();
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
    @Transactional
    public UsuarioRetoRespuestaDTO actualizarProgreso(
            Integer usuarioRetoId, Long usuarioId, ActualizarProgresoDTO dto) {
        if (dto == null || dto.getProgreso() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El progreso es obligatorio");
        }
        if (dto.getProgreso().compareTo(BigDecimal.ZERO) < 0
                || dto.getProgreso().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El progreso debe estar entre 0 y 100");
        }

        usuarioRetoRepository.bloquearParticipacion(usuarioRetoId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Participación no encontrada"));
        UsuarioReto usuarioReto = usuarioRetoRepository
                .findByIdAndUsuario_Id(usuarioRetoId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Participación no encontrada"));

        if (!"activo".equalsIgnoreCase(usuarioReto.getEstado())
                || usuarioReto.getFechaCompletado() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La participación no está activa");
        }

        usuarioReto.setProgreso(dto.getProgreso());
        return convertirARespuesta(usuarioRetoRepository.save(usuarioReto));
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

        if (!"activo".equalsIgnoreCase(usuarioReto.getEstado())
                || usuarioReto.getFechaCompletado() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La participación ya está completada o no está activa");
        }

        if (usuarioReto.getProgreso() == null
                || usuarioReto.getProgreso().compareTo(BigDecimal.valueOf(100)) != 0) {
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
        Usuario usuarioActualizado = usuarioService.actualizarNivel(usuarioReto.getUsuario());
        insigniaService.evaluarYDesbloquearInsignias(usuarioActualizado);

        return convertirARespuesta(usuarioRetoRepository.save(usuarioReto));
    }

    private UsuarioRetoRespuestaDTO convertirARespuesta(UsuarioReto usuarioReto) {
        return new UsuarioRetoRespuestaDTO(
                usuarioReto.getId(),
                usuarioReto.getUsuario().getId(),
                usuarioReto.getReto().getId(),
                usuarioReto.getEstado(),
                usuarioReto.getProgreso(),
                usuarioReto.getFechaAceptacion(),
                usuarioReto.getFechaCompletado()
        );
    }

    private RetoRespuestaDTO convertirADTO(Reto reto) {
        return new RetoRespuestaDTO(
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
    }
}
