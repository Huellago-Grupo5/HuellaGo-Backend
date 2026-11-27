package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ActualizarProgresoDTO;
import com.huellago.backend.dtos.RetoColectivoDTO;
import com.huellago.backend.dtos.UsuarioRetoRespuestaDTO;
import com.huellago.backend.dtos.RegistrarAvanceDTO;
import com.huellago.backend.dtos.AvanceRetoRespuestaDTO;
import com.huellago.backend.dtos.RetoRespuestaDTO;
import com.huellago.backend.entities.AvanceReto;
import com.huellago.backend.entities.ResultadoRetoColectivo;
import com.huellago.backend.entities.Reto;
import com.huellago.backend.entities.TipoReto;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.entities.UsuarioReto;
import com.huellago.backend.repositories.ResultadoRetoColectivoRepository;
import com.huellago.backend.repositories.AvanceRetoRepository;
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
    @Autowired AvanceRetoRepository avances;
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
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "El progreso se registra como cantidades mediante /retos/colectivos/participaciones/{id}/avances");
    }

    @Override
    @Transactional
    public UsuarioRetoRespuestaDTO registrarAvance(Integer participacionId, Long usuarioId, RegistrarAvanceDTO dto) {
        if (dto == null || dto.cantidad() == null || dto.claveSolicitud() == null || dto.cantidad().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cantidad positiva y la clave de solicitud son obligatorias");
        }
        BigDecimal cantidad;
        try { cantidad = dto.cantidad().setScale(3, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad admite hasta tres decimales");
        }
        Integer retoId = participaciones.buscarRetoIdDeParticipacion(participacionId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participación no encontrada"));
        bloquearReto(retoId);
        Reto reto = buscarColectivo(retoId);
        autorizarParticipacion(reto, usuarioId);
        participaciones.bloquearParticipacion(participacionId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participación no encontrada"));
        UsuarioReto participacion = participaciones.findByIdAndUsuario_Id(participacionId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participación no encontrada"));
        if (avances.findByParticipacion_IdAndClaveSolicitud(participacionId, dto.claveSolicitud()).isPresent()) {
            return respuesta(participacion);
        }
        if (!disponible(reto, LocalDate.now()) || resultados.existsByReto_Id(retoId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El periodo del reto colectivo cerró");
        }
        if (!"activo".equalsIgnoreCase(participacion.getEstado()) || participacion.getFechaCompletado() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La participación no está activa");
        }
        if (reto.getMetaCantidad() == null || reto.getMetaCantidad().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El reto no tiene una meta cuantitativa válida");
        }
        BigDecimal acumulado = participacion.getCantidadAcumulada() == null
                ? BigDecimal.ZERO : participacion.getCantidadAcumulada();
        BigDecimal total = acumulado.add(cantidad).setScale(3, RoundingMode.UNNECESSARY);
        if (total.compareTo(reto.getMetaCantidad()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad supera lo que falta para alcanzar la meta");
        }
        AvanceReto avance = new AvanceReto();
        avance.setParticipacion(participacion);
        avance.setCantidad(cantidad);
        avance.setRegistradoEn(LocalDateTime.now());
        avance.setClaveSolicitud(dto.claveSolicitud());
        avances.save(avance);
        participacion.setCantidadAcumulada(total);
        participacion.setProgreso(total.multiply(BigDecimal.valueOf(100))
                .divide(reto.getMetaCantidad(), 2, RoundingMode.HALF_UP));
        return respuesta(participaciones.save(participacion));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvanceRetoRespuestaDTO> listarAvances(Integer participacionId, Long usuarioId) {
        Integer retoId = participaciones.buscarRetoIdDeParticipacion(participacionId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participación no encontrada"));
        autorizar(buscarColectivo(retoId), usuarioId);
        return avances.findByParticipacion_IdAndParticipacion_Usuario_IdOrderByRegistradoEnDescIdDesc(
                participacionId, usuarioId).stream()
                .map(a -> new AvanceRetoRespuestaDTO(a.getId(), a.getCantidad(), a.getRegistradoEn())).toList();
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
            RetoColectivoDTO dto = CalculoSeguimientoColectivo.resumen(datos(reto), 0, BigDecimal.ZERO,
                    new CalculoSeguimientoColectivo.Cierre(resultado.getParticipantes(), resultado.getProgresoFinal(),
                            resultado.getEstado(), resultado.getFechaCierre()), LocalDate.now());
            return conMeta(dto, reto);
        }
        List<UsuarioReto> participantes = participaciones.findByReto_Id(reto.getId());
        return conMeta(CalculoSeguimientoColectivo.resumen(datos(reto), participantes.size(), suma(participantes),
                null, LocalDate.now()), reto);
    }

    private RetoColectivoDTO conMeta(RetoColectivoDTO dto, Reto reto) {
        dto.setMetaCantidad(reto.getMetaCantidad());
        dto.setUnidadMedida(reto.getUnidadMedida());
        return dto;
    }

    private BigDecimal promedio(List<UsuarioReto> participantes) {
        return CalculoSeguimientoColectivo.promedio(suma(participantes), participantes.size());
    }

    private BigDecimal suma(List<UsuarioReto> participantes) {
        return participantes.stream()
                .map(p -> p.getProgreso() == null ? BigDecimal.ZERO : p.getProgreso())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean disponible(Reto reto, LocalDate hoy) {
        return CalculoSeguimientoColectivo.disponible(datos(reto), hoy);
    }

    private CalculoSeguimientoColectivo.Datos datos(Reto reto) {
        return new CalculoSeguimientoColectivo.Datos(reto.getId(), reto.getTitulo(),
                reto.getFechaInicio(), reto.getFechaFin(), reto.getActivo());
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
        UsuarioRetoRespuestaDTO dto = new UsuarioRetoRespuestaDTO(participacion.getId(), participacion.getUsuario().getId(),
                participacion.getReto().getId(), participacion.getEstado(), participacion.getProgreso(),
                participacion.getFechaAceptacion(), participacion.getFechaCompletado());
        dto.setCantidadAcumulada(participacion.getCantidadAcumulada() == null
                ? BigDecimal.ZERO : participacion.getCantidadAcumulada());
        Reto reto = participacion.getReto();
        RetoRespuestaDTO detalle = new RetoRespuestaDTO(reto.getId(), reto.getTitulo(), reto.getDescripcion(),
                reto.getPuntosRecompensa(), reto.getDificultad(), reto.getFechaInicio(), reto.getFechaFin(),
                reto.getActivo(), reto.getCategoria() == null ? null : reto.getCategoria().getId(),
                reto.getCategoria() == null ? null : reto.getCategoria().getNombre());
        detalle.setMetaCantidad(reto.getMetaCantidad());
        detalle.setUnidadMedida(reto.getUnidadMedida());
        dto.setReto(detalle);
        return dto;
    }
}
