package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.RetoRespuestaDTO;
import com.huellago.backend.dtos.UsuarioRetoRespuestaDTO;
import com.huellago.backend.entities.Reto;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.entities.UsuarioReto;
import com.huellago.backend.repositories.RetoRepository;
import com.huellago.backend.repositories.UsuarioRetoRepository;
import com.huellago.backend.services.RetoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RetoServiceImpl implements RetoService {

    @Autowired
    RetoRepository retoRepository;

    @Autowired
    UsuarioRetoRepository usuarioRetoRepository;

    @Override
    public List<RetoRespuestaDTO> listarRetosActivos() {
        return retoRepository.findByActivoTrueOrderByFechaInicioAsc()
                .stream()
                .map(this::convertirADTO)
                .toList();
    }

    @Override
    public UsuarioRetoRespuestaDTO aceptarReto(Integer retoId, Usuario usuario) {
        Reto reto = retoRepository.findById(retoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reto no encontrado"));

        if (!Boolean.TRUE.equals(reto.getActivo())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "El reto no está activo");
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
