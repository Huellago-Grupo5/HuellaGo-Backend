package com.huellago.backend.services;

public interface CorreoRecuperacionService {
    void verificarConfiguracion();
    void enviar(String destinatario, String token);
}
