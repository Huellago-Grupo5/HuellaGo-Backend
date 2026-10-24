package com.huellago.backend.services;

import com.huellago.backend.dtos.GeneracionRecomendacionesDTO;
import com.huellago.backend.dtos.ConversionRecomendacionDTO;
import com.huellago.backend.entities.Usuario;

public interface RecomendacionService {
    GeneracionRecomendacionesDTO generar(Usuario usuarioAutenticado);
    ConversionRecomendacionDTO convertirEnReto(Integer usuarioRecomendacionId, Usuario usuarioAutenticado);
}
