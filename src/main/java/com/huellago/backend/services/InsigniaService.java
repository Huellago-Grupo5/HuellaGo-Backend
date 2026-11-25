package com.huellago.backend.services;

import com.huellago.backend.dtos.InsigniaRespuestaDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.entities.Reto;

import java.util.List;

public interface InsigniaService {
    List<InsigniaRespuestaDTO> evaluarYDesbloquearInsignias(Usuario usuario);
    List<InsigniaRespuestaDTO> evaluarYDesbloquearPorReto(Usuario usuario, Reto reto);

    List<InsigniaRespuestaDTO> obtenerInsignias(Usuario usuario);
}
