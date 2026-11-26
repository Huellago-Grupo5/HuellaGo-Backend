package com.huellago.backend.services;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.Usuario;
import java.util.List;

public interface CampanaInstitucionalService {
    CampanaRespuestaDTO crear(Long institucionId, Usuario usuario, CampanaCrearDTO datos);
    List<CampanaRespuestaDTO> listar(Long institucionId, Usuario usuario, int pagina, int tamano);
    CampanaRespuestaDTO detalle(Long institucionId, Integer campanaId, Usuario usuario);
    List<CampanaRespuestaDTO> misCampanas(Usuario usuario, int pagina, int tamano);
}
