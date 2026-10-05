package com.huellago.backend.services;

import com.huellago.backend.dtos.InsigniaRespuestaDTO;
import com.huellago.backend.entities.Usuario;

import java.util.List;

public interface InsigniaService {
    void evaluarYDesbloquearInsignias(Usuario usuario);

    List<InsigniaRespuestaDTO> obtenerInsignias(Usuario usuario);
}
