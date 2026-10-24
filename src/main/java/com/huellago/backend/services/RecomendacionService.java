package com.huellago.backend.services;

import com.huellago.backend.dtos.GeneracionRecomendacionesDTO;
import com.huellago.backend.dtos.ConversionRecomendacionDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.dtos.RecomendacionCompletadaDTO;
import java.util.List;

public interface RecomendacionService {
    GeneracionRecomendacionesDTO generar(Usuario usuarioAutenticado);
    ConversionRecomendacionDTO convertirEnReto(Integer usuarioRecomendacionId, Usuario usuarioAutenticado);
    RecomendacionCompletadaDTO completar(Integer usuarioRecomendacionId, Usuario usuarioAutenticado);
    List<RecomendacionCompletadaDTO> historialCompletadas(Usuario usuarioAutenticado);
}
