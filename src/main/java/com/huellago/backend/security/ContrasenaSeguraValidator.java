package com.huellago.backend.security;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class ContrasenaSeguraValidator implements ConstraintValidator<ContrasenaSegura, String> {
    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {
        return valor != null && valor.length() >= 12 && valor.length() <= 72
                && valor.getBytes(StandardCharsets.UTF_8).length <= 72
                && valor.codePoints().noneMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c))
                && valor.codePoints().anyMatch(Character::isUpperCase)
                && valor.codePoints().anyMatch(Character::isLowerCase)
                && valor.codePoints().anyMatch(Character::isDigit)
                && valor.codePoints().anyMatch(c -> !Character.isLetterOrDigit(c));
    }
}
