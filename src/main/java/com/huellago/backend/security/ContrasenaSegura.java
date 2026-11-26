package com.huellago.backend.security;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ContrasenaSeguraValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ContrasenaSegura {
    String message() default "La contraseña requiere 12 a 72 caracteres, máximo 72 bytes UTF-8, mayúscula, minúscula, número y símbolo, sin espacios";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
