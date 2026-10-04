package com.huellago.backend.services;

import com.huellago.backend.dtos.RetoRespuestaDTO;

import java.util.List;

public interface RetoService {
    List<RetoRespuestaDTO> listarRetosActivos();
}
