package com.huellago.backend.services;

import com.huellago.backend.dtos.AlimentacionResiduosDTO;
import com.huellago.backend.dtos.AlimentacionResiduosRespuestaDTO;
import com.huellago.backend.dtos.EnergiaDTO;
import com.huellago.backend.dtos.EnergiaRespuestaDTO;
import com.huellago.backend.dtos.HabitoRespuestaDTO;
import com.huellago.backend.dtos.TransporteDTO;
import com.huellago.backend.dtos.TransporteRespuestaDTO;
import com.huellago.backend.dtos.ActualizarHabitoDTO;
import com.huellago.backend.entities.Usuario;

public interface HabitoService {
    public TransporteRespuestaDTO registrarTransporte(TransporteDTO transporteDTO);
    public EnergiaRespuestaDTO registrarEnergia(EnergiaDTO energiaDTO);
    public AlimentacionResiduosRespuestaDTO registrarAlimentacionResiduos(AlimentacionResiduosDTO dto);
    public HabitoRespuestaDTO actualizar(Long id, Usuario usuario, ActualizarHabitoDTO dto);
}
