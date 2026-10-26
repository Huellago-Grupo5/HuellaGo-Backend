package com.huellago.backend.services;

import com.huellago.backend.dtos.*;

public interface RecuperacionContrasenaService {
    RecuperacionRespuestaDTO solicitar(RecuperarContrasenaDTO datos);
    void restablecer(RestablecerContrasenaDTO datos);
}
