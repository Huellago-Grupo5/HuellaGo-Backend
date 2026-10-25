package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.services.PublicacionComunitariaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PublicacionComunitariaServiceImpl implements PublicacionComunitariaService {
    private final PublicacionComunitariaRepository publicaciones;
    private final UsuarioRepository usuarios;
    private final UsuarioInsigniaRepository insigniasObtenidas;
    private final InsigniaRepository insignias;
    private final UsuarioRetoRepository participaciones;
    private final ResultadoRetoColectivoRepository resultados;

    public PublicacionComunitariaServiceImpl(PublicacionComunitariaRepository publicaciones,
            UsuarioRepository usuarios, UsuarioInsigniaRepository insigniasObtenidas,
            InsigniaRepository insignias, UsuarioRetoRepository participaciones,
            ResultadoRetoColectivoRepository resultados) {
        this.publicaciones = publicaciones;
        this.usuarios = usuarios;
        this.insigniasObtenidas = insigniasObtenidas;
        this.insignias = insignias;
        this.participaciones = participaciones;
        this.resultados = resultados;
    }

    @Override
    @Transactional
    public PublicacionCreadaDTO compartir(Usuario autenticado, CompartirLogroDTO solicitud) {
        Long autorId = identidad(autenticado);
        if (solicitud == null || solicitud.tipo() == null || solicitud.origenId() == null
                || solicitud.origenId() <= 0) {
            throw error(HttpStatus.BAD_REQUEST, "Tipo y origenId positivo son obligatorios");
        }
        // Mismo bloqueo de usuario que las recompensas/evaluaciones existentes; no modifica su saldo.
        usuarios.bloquearParaRecomendaciones(autorId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        Usuario autor = usuarios.findById(autorId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        PublicacionComunitaria publicacion = new PublicacionComunitaria();
        publicacion.setAutor(autor);
        if (solicitud.tipo() == CompartirLogroDTO.Tipo.INSIGNIA) {
            Insignia insignia = insignias.findById(solicitud.origenId())
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Insignia no encontrada"));
            UsuarioInsignia obtenida = insigniasObtenidas
                    .findFirstByUsuario_IdAndInsignia_IdOrderByIdAsc(autorId, insignia.getId())
                    .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "No has obtenido esta insignia"));
            if (obtenida.getFechaObtencion() == null) {
                throw error(HttpStatus.CONFLICT, "La insignia no tiene una fecha de obtención verificable");
            }
            if (publicaciones.existsByAutor_IdAndInsignia_Id(autorId, insignia.getId())) {
                throw error(HttpStatus.CONFLICT, "Ya compartiste esta insignia");
            }
            publicacion.setInsignia(insignia);
            publicacion.setTitulo(insignia.getNombre());
        } else {
            UsuarioReto participacion = participaciones.findByIdAndUsuario_Id(solicitud.origenId(), autorId)
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Participación no encontrada para el usuario"));
            validarFinalizacion(participacion);
            if (publicaciones.existsByParticipacion_Id(participacion.getId())) {
                throw error(HttpStatus.CONFLICT, "Ya compartiste este reto");
            }
            publicacion.setParticipacion(participacion);
            publicacion.setTitulo(participacion.getReto().getTitulo());
        }
        if (publicacion.getTitulo() == null || publicacion.getTitulo().isBlank()
                || publicacion.getTitulo().length() > 255) {
            throw error(HttpStatus.CONFLICT, "El logro no tiene un título válido para publicarse");
        }
        publicacion.setFechaPublicacion(LocalDateTime.now());
        PublicacionComunitaria guardada = publicaciones.saveAndFlush(publicacion);
        return new PublicacionCreadaDTO("Logro publicado en la comunidad", respuesta(guardada, autorId));
    }

    private void validarFinalizacion(UsuarioReto participacion) {
        Reto reto = participacion.getReto();
        if (reto == null || participacion.getProgreso() == null
                || participacion.getProgreso().compareTo(BigDecimal.valueOf(100)) != 0
                || "cancelado".equalsIgnoreCase(participacion.getEstado())) {
            throw error(HttpStatus.CONFLICT, "El reto no está completado");
        }
        if (reto.getTipo() == TipoReto.COLECTIVO) {
            if (!"activo".equalsIgnoreCase(participacion.getEstado())
                    && !"completado".equalsIgnoreCase(participacion.getEstado())) {
                throw error(HttpStatus.CONFLICT, "La participación no acredita la finalización del reto");
            }
            // HU29 no cambia el estado de las participaciones al cerrar; la evidencia es el resultado persistido.
            ResultadoRetoColectivo resultado = resultados.findByReto_Id(reto.getId())
                    .orElseThrow(() -> error(HttpStatus.CONFLICT, "El reto colectivo todavía no tiene un resultado final"));
            if (!"EXITOSO".equals(resultado.getEstado()) || resultado.getFechaCierre() == null
                    || resultado.getProgresoFinal() == null
                    || resultado.getProgresoFinal().compareTo(BigDecimal.valueOf(100)) != 0) {
                throw error(HttpStatus.CONFLICT, "El reto colectivo no finalizó exitosamente");
            }
        } else if (!"completado".equalsIgnoreCase(participacion.getEstado())
                || participacion.getFechaCompletado() == null) {
            throw error(HttpStatus.CONFLICT, "El reto no está completado");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicacionComunitariaDTO> listar(Usuario autenticado, int pagina, int tamano) {
        Long visorId = identidad(autenticado);
        if (pagina < 0 || tamano < 1 || tamano > 50) {
            throw error(HttpStatus.BAD_REQUEST, "pagina debe ser >= 0 y tamano debe estar entre 1 y 50");
        }
        return publicaciones.findAll(PageRequest.of(pagina, tamano,
                        Sort.by(Sort.Direction.DESC, "fechaPublicacion", "id")))
                .stream().map(p -> respuesta(p, visorId)).toList();
    }

    private PublicacionComunitariaDTO respuesta(PublicacionComunitaria publicacion, Long visorId) {
        Usuario autor = publicacion.getAutor();
        boolean visible = visorId.equals(autor.getId()) || Boolean.TRUE.equals(autor.getVisibilidadComunidad());
        String nombre = visible ? autor.getNombres() : "Usuario privado";
        if (nombre == null || nombre.isBlank()) nombre = "Usuario de HuellaGo";
        boolean insignia = publicacion.getInsignia() != null;
        Integer origenId = insignia ? publicacion.getInsignia().getId() : publicacion.getParticipacion().getId();
        return new PublicacionComunitariaDTO(publicacion.getId(), visible ? autor.getId() : null,
                nombre, insignia ? "INSIGNIA" : "RETO", visible ? origenId : null,
                publicacion.getTitulo(), publicacion.getFechaPublicacion());
    }

    private Long identidad(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            throw error(HttpStatus.UNAUTHORIZED, "Se requiere autenticación");
        }
        return usuario.getId();
    }

    private ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }
}
