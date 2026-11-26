package com.huellago.backend.exceptions;

/** Error tipado del cliente Ollama; la entrada HTTP lo traduce a una respuesta controlada. */
public class OllamaClientException extends RuntimeException {
    public enum Tipo {
        CONTEXTO_INVALIDO, NO_DISPONIBLE, TIEMPO_AGOTADO, ERROR_HTTP, RESPUESTA_INVALIDA
    }

    private final Tipo tipo;
    private final Integer estadoHttp;

    public OllamaClientException(Tipo tipo, String mensaje) {
        this(tipo, mensaje, null);
    }

    public OllamaClientException(Tipo tipo, String mensaje, Integer estadoHttp) {
        super(mensaje);
        this.tipo = tipo;
        this.estadoHttp = estadoHttp;
    }

    public Tipo getTipo() { return tipo; }
    public Integer getEstadoHttp() { return estadoHttp; }
}
