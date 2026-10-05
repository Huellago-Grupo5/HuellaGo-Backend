package com.huellago.backend.services;

import com.huellago.backend.dtos.AlimentacionResiduosDTO;
import com.huellago.backend.dtos.EnergiaDTO;
import com.huellago.backend.dtos.HabitoRespuestaDTO;
import com.huellago.backend.dtos.TransporteDTO;
import com.huellago.backend.dtos.ActualizarHabitoDTO;
import com.huellago.backend.entities.Usuario;

import java.util.List;

public interface HabitoService {
    public List<HabitoRespuestaDTO> registrarTransporte(TransporteDTO transporteDTO);
    public List<HabitoRespuestaDTO> registrarEnergia(EnergiaDTO energiaDTO);
    public List<HabitoRespuestaDTO> registrarAlimentacionResiduos(AlimentacionResiduosDTO dto);
    public HabitoRespuestaDTO actualizar(Long id, Usuario usuario, ActualizarHabitoDTO dto);
}
