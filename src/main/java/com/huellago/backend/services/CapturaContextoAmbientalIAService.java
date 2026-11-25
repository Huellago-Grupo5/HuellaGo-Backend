package com.huellago.backend.services;

import com.huellago.backend.dtos.CapturaContextoAmbientalIADTO;
import org.springframework.security.core.Authentication;

/** Authentication debe proceder del contexto de seguridad confiable, nunca del cuerpo HTTP.
 * No comprueba un JWT por sí mismo ni autoriza inferencias/persistencia. */
public interface CapturaContextoAmbientalIAService {
    CapturaContextoAmbientalIADTO capturar(Authentication autenticacion);
    Revalidacion revalidar(Authentication autenticacion, CapturaContextoAmbientalIADTO original);
    /** El escritor confiable debe bloquear Usuario antes de invocar por proxy esta operación. */
    Revalidacion comprobarVigenciaEnTransaccion(Authentication autenticacion, CapturaContextoAmbientalIADTO original);
    enum Estado { VIGENTE, CAMBIO_DETECTADO }
    record Revalidacion(Estado estado, String huellaActual) { }
}
