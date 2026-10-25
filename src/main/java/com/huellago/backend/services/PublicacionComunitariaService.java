package com.huellago.backend.services;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.Usuario;
import java.util.List;

public interface PublicacionComunitariaService {
    PublicacionCreadaDTO compartir(Usuario autenticado, CompartirLogroDTO solicitud);
    List<PublicacionComunitariaDTO> listar(Usuario autenticado, int pagina, int tamano);
}
