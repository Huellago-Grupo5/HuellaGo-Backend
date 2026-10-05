package com.huellago.backend.services;

import com.huellago.backend.dtos.LogrosRespuestaDTO;
import com.huellago.backend.entities.Usuario;

public interface LogrosService {
    LogrosRespuestaDTO obtenerLogros(Usuario usuario);
}
