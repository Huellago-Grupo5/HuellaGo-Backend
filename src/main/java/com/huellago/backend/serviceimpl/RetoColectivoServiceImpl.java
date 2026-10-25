package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ActualizarProgresoDTO;
import com.huellago.backend.dtos.RetoColectivoDTO;
import com.huellago.backend.dtos.UsuarioRetoRespuestaDTO;
import com.huellago.backend.entities.ResultadoRetoColectivo;
import com.huellago.backend.entities.Reto;
import com.huellago.backend.entities.TipoReto;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.entities.UsuarioReto;
import com.huellago.backend.repositories.ResultadoRetoColectivoRepository;
import com.huellago.backend.repositories.RetoRepository;
import com.huellago.backend.repositories.UsuarioRetoRepository;
import com.huellago.backend.services.RetoColectivoService;
import com.huellago.backend.security.AutorizacionInstitucionalService;
import com.huellago.backend.events.InformacionAmbientalActualizada;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RetoColectivoServiceImpl implements RetoColectivoService {
    @Autowired ApplicationEventPublisher eventos;
    @Autowired RetoRepository retos;
    @Autowired UsuarioRetoRepository participaciones;
    @Autowired ResultadoRetoColectivoRepository resultados;
    @Autowired AutorizacionInstitucionalService autorizacionInstitucional;

    @Override
    @Transactional(readOnly = true)
    public List<RetoColectivoDTO> listarDisponibles() {
        return retos.findByTipo(TipoReto.COLECTIVO).stream()
                .filter(reto -> reto.getInstitucion() == null && disponible(reto, LocalDate.now())
                        && !resultados.existsByReto_Id(reto.getId()))
                .map(this::resumen)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RetoColectivoDTO obtener(Integer retoId) {
        Reto reto = buscarColectivo(retoId);
        if (reto.getInstitucion() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere identidad institucional");
        }
        return resumen(reto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RetoColectivoDTO> listarDisponibles(Long usuarioId) {
        validarIdentidad(usuarioId);
        return retos.findByTipo(TipoReto.COLECTIVO).stream()
                .filter(r -> r.getInstitucion() == null
                        || autorizacionInstitucional.esMiembro(usuarioId, r.getInstitucion().getId()))
                .filter(r -> disponible(r, LocalDate.now()) && !resultados.existsByReto_Id(r.getId()))
                .map(this::resumen).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RetoColectivoDTO obtener(Integer retoId, Long usuarioId) {
        validarIdentidad(usuarioId);
        Reto reto = buscarColectivo(retoId);
        autorizar(reto, usuarioId);
        return resumen(reto);
    }

    private void validarIdentidad(Long usuarioId) {
        if (usuarioId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere autenticación");
    }

    private void autorizar(Reto reto, Long usuarioId) {
        if (reto.getInstitucion() != null) {
            autorizacionInstitucional.exigirMiembro(usuarioId, reto.getInstitucion().getId());
        }
    }

    private void autorizarParticipacion(Reto reto, Long usuarioId) {
        if (reto.getInstitucion() != null) {
            autorizacionInstitucional.exigirMiembroParaParticipar(usuarioId, reto.getInstitucion().getId());
        }
    }

    @Override
    @Transactional
    public UsuarioRetoRespuestaDTO unirse(Integer retoId, Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere autenticación");
        }
        bloquearReto(retoId);
        Reto reto = buscarColectivo(retoId);
        autorizarParticipacion(reto, usuario.getId());
        if (!disponible(reto, LocalDate.now()) || resultados.existsByReto_Id(retoId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El reto colectivo no admite participantes");
        }
        if (participaciones.existsByUsuario_IdAndReto_Id(usuario.getId(), retoId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El usuario ya aceptó este reto");
        }
        UsuarioReto nueva = new UsuarioReto();
        nueva.setUsuario(usuario);
        nueva.setReto(reto);
        nueva.setEstado("activo");
        nueva.setProgreso(BigDecimal.ZERO);
        nueva.setFechaAceptacion(LocalDateTime.now());
        return respuesta(participaciones.saveAndFlush(nueva));
    }

    @Override
    @Transactional
    public UsuarioRetoRespuestaDTO actualizarProgreso(Integer participacionId, Long usuarioId,
                                                       ActualizarProgresoDTO dto) {
        if (dto == null || dto.getProgreso() == null
                || dto.getProgreso().compareTo(BigDecimal.ZERO) < 0
                || dto.getProgreso().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El progreso debe estar entre 0 y 100");
        }
        Integer retoId = participaciones.buscarRetoIdDeParticipacion(participacionId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Participación no encontrada"));
        bloquearReto(retoId);
        Reto reto = buscarColectivo(retoId);
        autorizarParticipacion(reto, usuarioId);
        if (!disponible(reto, LocalDate.now()) || resultados.existsByReto_Id(retoId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El periodo del reto colectivo cerró");
        }
        participaciones.bloquearParticipacion(participacionId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Participación no encontrada"));
        UsuarioReto participacion = participaciones.findByIdAndUsuario_Id(participacionId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Participación no encontrada"));
        if (!"activo".equalsIgnoreCase(participacion.getEstado())
                || participacion.getFechaCompletado() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La participación no está activa");
        }
        participacion.setProgreso(dto.getProgreso());
        return respuesta(participaciones.save(participacion));
    }

    @Override
    @Transactional
    public void cerrarVencido(Integer retoId) {
        bloquearReto(retoId);
        Reto reto = buscarColectivo(retoId);
        if (reto.getFechaFin() == null || !LocalDate.now().isAfter(reto.getFechaFin())
                || resultados.existsByReto_Id(retoId)) {
            return;
        }
        List<UsuarioReto> participantes = participaciones.findByReto_Id(retoId);
        BigDecimal progreso = promedio(participantes);
        ResultadoRetoColectivo resultado = new ResultadoRetoColectivo();
        resultado.setReto(reto);
        resultado.setParticipantes(participantes.size());
        resultado.setProgresoFinal(progreso);
        resultado.setEstado(progreso.compareTo(BigDecimal.valueOf(100)) == 0
                ? "EXITOSO" : "NO_ALCANZADO");
        resultado.setFechaCierre(LocalDateTime.now());
        resultados.saveAndFlush(resultado);
        if (!participantes.isEmpty()) {
            eventos.publishEvent(new InformacionAmbientalActualizada(participantes.stream()
                    .map(p -> p.getUsuario().getId()).toList()));
        }
    }

    private RetoColectivoDTO resumen(Reto reto) {
        var cerrado = resultados.findByReto_Id(reto.getId());
        if (cerrado.isPresent()) {
            ResultadoRetoColectivo resultado = cerrado.get();
            return new RetoColectivoDTO(reto.getId(), reto.getTitulo(), reto.getFechaInicio(),
                    reto.getFechaFin(), resultado.getParticipantes(), resultado.getProgresoFinal(),
                    resultado.getEstado(), resultado.getFechaCierre());
        }
        List<UsuarioReto> participantes = participaciones.findByReto_Id(reto.getId());
        String estado = reto.getFechaFin() != null && LocalDate.now().isAfter(reto.getFechaFin())
                ? "PENDIENTE_CIERRE"
                : disponible(reto, LocalDate.now()) ? "ACTIVO" : "NO_DISPONIBLE";
        return new RetoColectivoDTO(reto.getId(), reto.getTitulo(), reto.getFechaInicio(),
                reto.getFechaFin(), participantes.size(), promedio(participantes), estado, null);
    }

    private BigDecimal promedio(List<UsuarioReto> participantes) {
        if (participantes.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal suma = participantes.stream()
                .map(p -> p.getProgreso() == null ? BigDecimal.ZERO : p.getProgreso())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return suma.divide(BigDecimal.valueOf(participantes.size()), 2, RoundingMode.HALF_UP);
    }

    private boolean disponible(Reto reto, LocalDate hoy) {
        return Boolean.TRUE.equals(reto.getActivo()) && reto.getFechaFin() != null
                && (reto.getFechaInicio() == null || !hoy.isBefore(reto.getFechaInicio()))
                && !hoy.isAfter(reto.getFechaFin())
                && (reto.getFechaInicio() == null || !reto.getFechaInicio().isAfter(reto.getFechaFin()));
    }

    private Reto buscarColectivo(Integer retoId) {
        Reto reto = retos.findById(retoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reto no encontrado"));
        if (reto.getTipo() != TipoReto.COLECTIVO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El reto no es colectivo");
        }
        return reto;
    }

    private void bloquearReto(Integer retoId) {
        retos.bloquearParaAceptacion(retoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reto no encontrado"));
    }

    private UsuarioRetoRespuestaDTO respuesta(UsuarioReto participacion) {
        return new UsuarioRetoRespuestaDTO(participacion.getId(), participacion.getUsuario().getId(),
                participacion.getReto().getId(), participacion.getEstado(), participacion.getProgreso(),
                participacion.getFechaAceptacion(), participacion.getFechaCompletado());
    }
}
