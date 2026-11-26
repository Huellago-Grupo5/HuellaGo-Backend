package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Sesion;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.UserSecurity;
import io.jsonwebtoken.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.Clock;
import java.util.*;

/** Clave aleatoria por proceso, nunca lee huellago.jwt.secret ni acepta tokens productivos. */
final class SeguridadHttpIA123 extends OncePerRequestFilter {
    static final String EMISOR="huellago-ia123-local", AUDIENCIA="postman-ia123";
    private final SecretKey clave=Jwts.SIG.HS256.key().build();
    private final UsuarioRepository usuarios;
    private final SesionRepository sesiones;
    private final BCryptPasswordEncoder bcrypt=new BCryptPasswordEncoder();
    private final Clock reloj;
    SeguridadHttpIA123(UsuarioRepository usuarios,SesionRepository sesiones) { this(usuarios,sesiones,Clock.systemUTC()); }
    SeguridadHttpIA123(UsuarioRepository usuarios,SesionRepository sesiones,Clock reloj) {
        this.usuarios=usuarios; this.sesiones=sesiones; this.reloj=reloj;
    }
    private UserSecurity sintetico() {
        var u=usuarios.findByCorreoNormalizado(EntornoHttpIA123.CORREO);
        if(u==null || u.getId()==null || !EntornoHttpIA123.CORREO.equals(u.getCorreo())) throw new IllegalArgumentException("Usuario sintetico no disponible");
        return new UserSecurity(u);
    }
    private UserSecurity autenticar(String ruta,String cabecera) {
        if(cabecera==null || cabecera.length()>4096) throw new IllegalArgumentException("Autenticacion requerida");
        if(EntornoHttpIA123.TOKEN.equals(ruta) && cabecera.startsWith("Basic ")) {
            String texto=new String(Base64.getDecoder().decode(cabecera.substring(6)),StandardCharsets.UTF_8);
            int separador=texto.indexOf(':');
            if(separador<0 || !EntornoHttpIA123.CORREO.equals(texto.substring(0,separador))) throw new IllegalArgumentException("Credenciales invalidas");
            var p=sintetico();
            if(p.getPassword()==null || !bcrypt.matches(texto.substring(separador+1),p.getPassword())) throw new IllegalArgumentException("Credenciales invalidas");
            return p;
        }
        if(!EntornoHttpIA123.RUTA.equals(ruta) || !cabecera.startsWith("Bearer ")) throw new IllegalArgumentException("JWT requerido");
        String token=cabecera.substring(7);
        var claims=Jwts.parser().verifyWith(clave).requireIssuer(EMISOR).requireAudience(AUDIENCIA)
                .clock(()->Date.from(reloj.instant())).build().parseSignedClaims(token).getPayload();
        if(claims.getExpiration()==null || !claims.getExpiration().toInstant().isAfter(reloj.instant())) throw new IllegalArgumentException("JWT expirado");
        var p=sintetico();
        if(!p.getUser().getId().toString().equals(claims.getSubject())) throw new IllegalArgumentException("Propietario invalido");
        var s=sesiones.findByToken(token).orElseThrow(()->new IllegalArgumentException("Sesion no disponible"));
        if(!Boolean.TRUE.equals(s.getActiva()) || s.getUsuario()==null || !p.getUser().getId().equals(s.getUsuario().getId())
                || s.getFechaExpiracion()==null || !s.getFechaExpiracion().isAfter(LocalDateTime.ofInstant(reloj.instant(),ZoneOffset.UTC)))
            throw new IllegalArgumentException("Sesion invalida");
        return p;
    }
    String emitir(UserSecurity usuario,TransactionTemplate tx) {
        Instant ahora=reloj.instant(),vence=ahora.plusSeconds(900);
        String token=Jwts.builder().issuer(EMISOR).audience().add(AUDIENCIA).and()
                .subject(usuario.getUser().getId().toString()).id(UUID.randomUUID().toString())
                .issuedAt(Date.from(ahora)).expiration(Date.from(vence)).signWith(clave,Jwts.SIG.HS256).compact();
        tx.executeWithoutResult(estado->{
            var s=new Sesion(); s.setUsuario(usuario.getUser()); s.setToken(token); s.setActiva(true);
            s.setFechaInicio(LocalDateTime.ofInstant(ahora,ZoneOffset.UTC)); s.setFechaExpiracion(LocalDateTime.ofInstant(vence,ZoneOffset.UTC));
            sesiones.saveAndFlush(s);
        });
        return token;
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain cadena) throws java.io.IOException,ServletException {
        try {
            var p=autenticar(req.getServletPath().isEmpty()?req.getRequestURI():req.getServletPath(),req.getHeader("Authorization"));
            var contexto=SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(new UsernamePasswordAuthenticationToken(p,null,List.of()));
            SecurityContextHolder.setContext(contexto);
        } catch(DataAccessException e) { res.sendError(503,"LECTURA_AUTENTICACION_NO_DISPONIBLE"); return; }
        catch(JwtException|IllegalArgumentException e) { res.sendError(401,"AUTENTICACION_INVALIDA"); return; }
        try { cadena.doFilter(req,res); } finally { SecurityContextHolder.clearContext(); }
    }
}
