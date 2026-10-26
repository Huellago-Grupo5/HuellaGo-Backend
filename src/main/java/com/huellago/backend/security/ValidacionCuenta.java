package com.huellago.backend.security;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;
import java.util.stream.Collectors;

/** La misma Bean Validation se aplica incluso al invocar directamente los servicios. */
public final class ValidacionCuenta {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private ValidacionCuenta() { }
    public static String correo(String valor) {
        return valor == null ? null : valor.strip().toLowerCase(Locale.ROOT);
    }
    public static String nombre(String valor) { return valor == null ? null : valor.strip(); }
    public static void exigir(Object dto) {
        if (dto == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La solicitud es obligatoria");
        var errores = VALIDATOR.validate(dto);
        if (!errores.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                errores.stream().map(e -> e.getPropertyPath() + ": " + e.getMessage()).sorted().collect(Collectors.joining("; ")));
    }
}
