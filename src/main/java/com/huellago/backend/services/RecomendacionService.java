package com.huellago.backend.services;

import com.huellago.backend.dtos.GeneracionRecomendacionesDTO;
import com.huellago.backend.entities.Usuario;

public interface RecomendacionService {
    GeneracionRecomendacionesDTO generar(Usuario usuarioAutenticado);
}
