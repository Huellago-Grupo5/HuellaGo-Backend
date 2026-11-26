package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Sesion;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.time.*;
import java.util.*;

/** Solo autentica la emision local. La ruta habitual usa JwtRequestFilter real sin alteraciones. */
class AutenticacionAcademicaIA extends OncePerRequestFilter {
    static final String TOKEN="/__test/demo/ia/token";
    private final UsuarioRepository usuarios;
    AutenticacionAcademicaIA(UsuarioRepository usuarios) { this.usuarios=usuarios; }
    static UserSecurity usuario(UsuarioRepository usuarios) {
        var u=usuarios.findByCorreoNormalizado(ProteccionAcademicaIA.CORREO);
        if(u==null || !Long.valueOf(ProteccionAcademicaIA.USUARIO).equals(u.getId()) || !ProteccionAcademicaIA.CORREO.equals(u.getCorreo()))
            throw new IllegalArgumentException("Usuario sintetico no autorizado");
        return new UserSecurity(u);
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest req) { return !TOKEN.equals(req.getRequestURI()); }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain cadena) throws java.io.IOException,ServletException {
        try {
            String h=req.getHeader("Authorization");
            if(h==null || !h.startsWith("Basic ") || h.length()>4096) throw new IllegalArgumentException();
            String texto=new String(Base64.getDecoder().decode(h.substring(6)),java.nio.charset.StandardCharsets.UTF_8); int pos=texto.indexOf(':');
            if(pos<0 || !ProteccionAcademicaIA.CORREO.equals(texto.substring(0,pos))) throw new IllegalArgumentException();
            var p=usuario(usuarios);
            if(p.getPassword()==null || !new BCryptPasswordEncoder().matches(texto.substring(pos+1),p.getPassword())) throw new IllegalArgumentException();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,List.of()));
        } catch(org.springframework.dao.DataAccessException e) { res.sendError(503); return; }
        catch(IllegalArgumentException e) { res.sendError(401); return; }
        try { cadena.doFilter(req,res); } finally { SecurityContextHolder.clearContext(); }
    }

    @org.springframework.context.annotation.Profile("academico-local-ia")
    @RestController
    static class Emisor {
        private final JwtUtilService jwt;
        private final SesionRepository sesiones;
        private final TransactionTemplate tx;
        Emisor(JwtUtilService jwt,SesionRepository sesiones,org.springframework.transaction.PlatformTransactionManager tm) {
            this.jwt=jwt; this.sesiones=sesiones; tx=new TransactionTemplate(tm);
        }
        @PostMapping(TOKEN) Map<String,Object> emitir(Authentication a,HttpServletRequest req) throws java.io.IOException {
            if(req.getQueryString()!=null || req.getInputStream().read()!=-1) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
            ProteccionAcademicaIA.identidad(a); var p=(UserSecurity)a.getPrincipal(); String token=jwt.generateToken(p);
            tx.executeWithoutResult(s->{ var sesion=new Sesion(); sesion.setUsuario(p.getUser()); sesion.setActiva(true); sesion.setToken(token);
                sesion.setFechaInicio(LocalDateTime.now()); sesion.setFechaExpiracion(LocalDateTime.ofInstant(jwt.extractExpiration(token).toInstant(),ZoneId.systemDefault()));
                sesiones.saveAndFlush(sesion); });
            return Map.of("access_token",token,"token_type","Bearer","finalidad",ProteccionAcademicaIA.FINALIDAD,"publicable",false);
        }
    }
}
