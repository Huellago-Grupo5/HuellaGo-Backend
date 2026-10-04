package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.RetoRespuestaDTO;
import com.huellago.backend.entities.Reto;
import com.huellago.backend.repositories.RetoRepository;
import com.huellago.backend.services.RetoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetoServiceImpl implements RetoService {

    @Autowired
    RetoRepository retoRepository;

    @Override
    public List<RetoRespuestaDTO> listarRetosActivos() {
        return retoRepository.findByActivoTrueOrderByFechaInicioAsc()
                .stream()
                .map(this::convertirADTO)
                .toList();
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
