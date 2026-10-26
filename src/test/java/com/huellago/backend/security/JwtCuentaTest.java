package com.huellago.backend.security;

import com.huellago.backend.entities.Usuario;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtCuentaTest {
    @Test void emisionesSimultaneasSonDistintasYValidasSoloConLaClaveCorrespondiente() {
        var jwt=new JwtUtilService();var usuario=new Usuario();usuario.setId(22L);usuario.setCorreo("cuenta@example.invalid");var principal=new UserSecurity(usuario);
        String uno=jwt.generateToken(principal), dos=jwt.generateToken(principal);
        assertNotEquals(uno,dos);assertTrue(jwt.validateToken(uno,principal));assertTrue(jwt.validateToken(dos,principal));
        assertThrows(RuntimeException.class,()->new JwtUtilService().validateToken(uno,principal));
    }
    @Test void normalizaSubjectParaCuentasLegadasSinInvalidarCambioSoloDeMayusculas() {
        var jwt=new JwtUtilService();var usuario=new Usuario();usuario.setId(22L);usuario.setCorreo("CUENTA@EXAMPLE.INVALID");var principal=new UserSecurity(usuario);
        String token=jwt.generateToken(principal);assertEquals("cuenta@example.invalid",jwt.extractUsername(token));
        usuario.setCorreo("cuenta@example.invalid");assertTrue(jwt.validateToken(token,principal));
    }
    @Test void secretoExternoValidoPermiteOtraInstanciaYRechazaManipulacionYExpiracion() {
        var key=io.jsonwebtoken.Jwts.SIG.HS256.key().build();String secreto=java.util.Base64.getEncoder().encodeToString(key.getEncoded());
        var uno=new JwtUtilService();var dos=new JwtUtilService();
        org.springframework.test.util.ReflectionTestUtils.setField(uno,"secretoExterno",secreto);
        org.springframework.test.util.ReflectionTestUtils.setField(dos,"secretoExterno",secreto);
        var usuario=new Usuario();usuario.setId(22L);usuario.setCorreo("cuenta@example.invalid");var principal=new UserSecurity(usuario);
        String token=uno.generateToken(principal);assertTrue(dos.validateToken(token,principal));
        int punto=token.lastIndexOf('.')+1;String modificado=token.substring(0,punto)+(token.charAt(punto)=='A'?'B':'A')+token.substring(punto+1);
        assertThrows(RuntimeException.class,()->dos.validateToken(modificado,principal));
        String expirado=io.jsonwebtoken.Jwts.builder().subject(usuario.getCorreo()).expiration(new java.util.Date(0)).signWith(key).compact();
        assertThrows(io.jsonwebtoken.ExpiredJwtException.class,()->dos.validateToken(expirado,principal));
    }
    @Test void secretoExternoDebilFallaDeFormaCerrada() {
        var jwt=new JwtUtilService();org.springframework.test.util.ReflectionTestUtils.setField(jwt,"secretoExterno","YQ==");
        assertThrows(io.jsonwebtoken.security.WeakKeyException.class,jwt::verificarConfiguracion);
    }
}
