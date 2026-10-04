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
    private static final String JWT_SIGNATURE_KEY =
            "VVBDX0lOR0VOSUVSSUFfU0lTVEVNQVNfREVfSU5GT1JNQUNJT05fVFJBRElOR19CQUNLRU5EX0pXVA==";
    private static final Long JWT_TOKEN_VALIDITY = 1000 * 60 * 60 * 3L;
    private static final Long PASSWORD_RESET_TOKEN_VALIDITY = 1000 * 60 * 15L;

    private SecretKey getSigningKey() {
        byte[] decodedKey = Base64.getDecoder().decode(JWT_SIGNATURE_KEY);
        return Keys.hmacShaKeyFor(decodedKey);
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
                && extractUsername(token).equals(user.getUsername());
    }

    public String extractPurpose(String token) {
        return extractClaim(token, claims -> claims.get("purpose", String.class));
    }

    private String createToken(String subject, Map<String, Object> claims) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + JWT_TOKEN_VALIDITY))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String generateToken(UserSecurity securityUser) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", securityUser.getUser().getId());
        return createToken(securityUser.getUsername(), claims);
    }

    public String generatePasswordResetToken(UserSecurity securityUser) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("purpose", "PASSWORD_RESET");
        claims.put("user_id", securityUser.getUser().getId());
        return Jwts.builder()
                .claims(claims)
                .subject(securityUser.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + PASSWORD_RESET_TOKEN_VALIDITY))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public boolean isPasswordResetToken(String token) {
        try {
            return "PASSWORD_RESET".equals(extractPurpose(token)) && !isTokenExpired(token);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
