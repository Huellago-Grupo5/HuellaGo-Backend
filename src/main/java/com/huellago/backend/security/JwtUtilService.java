package com.huellago.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtUtilService {
    @org.springframework.beans.factory.annotation.Value("${huellago.jwt.secret:}")
    private String secretoExterno = "";
    private final SecretKey claveEfimera = Jwts.SIG.HS256.key().build();
    private static final Long JWT_TOKEN_VALIDITY = 1000 * 60 * 60 * 3L;

    @jakarta.annotation.PostConstruct
    void verificarConfiguracion() {
        getSigningKey();
    }

    private SecretKey getSigningKey() {
        // Sin secreto externo se usa una clave aleatoria efímera: reiniciar invalida tokens.
        if (secretoExterno.isBlank()) return claveEfimera;
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretoExterno));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsFunction) {
        return claimsFunction.apply(extractAllClaims(token));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean validateToken(String token, UserSecurity user) {
        return extractPurpose(token) == null
                && !isTokenExpired(token)
                && ValidacionCuenta.correo(extractUsername(token)).equals(ValidacionCuenta.correo(user.getUsername()));
    }

    public String extractPurpose(String token) {
        return extractClaim(token, claims -> claims.get("purpose", String.class));
    }

    private String createToken(String subject, Map<String, Object> claims) {
        return Jwts.builder()
                .claims(claims)
                .id(java.util.UUID.randomUUID().toString())
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + JWT_TOKEN_VALIDITY))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String generateToken(UserSecurity securityUser) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", securityUser.getUser().getId());
        return createToken(ValidacionCuenta.correo(securityUser.getUsername()), claims);
    }

}
