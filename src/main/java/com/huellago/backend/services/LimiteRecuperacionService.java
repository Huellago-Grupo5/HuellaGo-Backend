package com.huellago.backend.services;

public interface LimiteRecuperacionService {
    /** Reserva un intento antes de consultar la cuenta, modificar tokens o contactar SMTP. */
    void reservar(String correo);
}
