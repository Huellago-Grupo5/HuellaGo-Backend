package com.huellago.backend.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class LimiteRecuperacionException extends ResponseStatusException {
    private final long segundosEspera;
    public LimiteRecuperacionException(long segundosEspera) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes de recuperación. Intenta nuevamente más tarde");
        this.segundosEspera = Math.max(1, segundosEspera);
    }
    public long getSegundosEspera() { return segundosEspera; }
}
