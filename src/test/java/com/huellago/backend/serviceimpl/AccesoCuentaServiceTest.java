package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.LoginDTO;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.*;
import com.huellago.backend.services.SesionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class AccesoCuentaServiceTest {
    @Mock AuthenticationManager autenticacion; @Mock UsuarioRepository usuarios; @Mock HabitoRepository habitos;
    @Mock JwtUtilService jwt; @Mock SesionService sesiones; @Mock jakarta.persistence.EntityManager entityManager;
    @InjectMocks AccesoCuentaServiceImpl servicio;
    Usuario usuario;
    @BeforeEach void datos() { usuario=new Usuario(); usuario.setId(22L); usuario.setCorreo("cuenta@example.invalid"); }
    void autenticar() {
        when(usuarios.findByCorreoNormalizado(usuario.getCorreo())).thenReturn(usuario);
        when(usuarios.bloquearParaRecomendaciones(22L)).thenReturn(Optional.of(22L));
        var principal=new UserSecurity(usuario);
        when(autenticacion.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        when(jwt.generateToken(any())).thenReturn("jwt-de-prueba");
    }
    @Test void loginNormalizaYRegistraSesion() {
        autenticar(); var resultado=servicio.login(new LoginDTO(" CUENTA@EXAMPLE.INVALID ","ClaveAnterior"));
        assertEquals("jwt-de-prueba",resultado.getToken());
        assertEquals(22L,resultado.getId()); assertEquals(usuario.getCorreo(),resultado.getCorreo());
        verify(sesiones).registrarSesion(any(),eq("jwt-de-prueba"));
        var orden=inOrder(usuarios,entityManager,autenticacion,sesiones);
        orden.verify(usuarios).findByCorreoNormalizado(usuario.getCorreo()); orden.verify(usuarios).bloquearParaRecomendaciones(22L);
        orden.verify(entityManager).refresh(usuario); orden.verify(autenticacion).authenticate(any()); orden.verify(sesiones).registrarSesion(any(),any());
    }
    @Test void loginNoConsultaHabitos() {
        autenticar();
        servicio.login(new LoginDTO(usuario.getCorreo(),"ClaveAnterior"));
        verifyNoInteractions(habitos);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" "})
    void claveObligatoria(String clave) {
        assertEquals(400,assertThrows(ResponseStatusException.class,()->servicio.login(new LoginDTO(usuario.getCorreo(),clave))).getStatusCode().value());
        verifyNoInteractions(usuarios,autenticacion,sesiones);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","invalido"})
    void correoObligatorioYValido(String correo) {
        assertThrows(ResponseStatusException.class,()->servicio.login(new LoginDTO(correo,"clave")));verifyNoInteractions(usuarios,autenticacion,sesiones);
    }
    @Test void credencialesIncorrectasNoEmitenSesion() {
        when(usuarios.findByCorreoNormalizado(usuario.getCorreo())).thenReturn(usuario);
        when(usuarios.bloquearParaRecomendaciones(22L)).thenReturn(Optional.of(22L));
        when(autenticacion.authenticate(any())).thenThrow(new BadCredentialsException("incorrectas"));
        assertThrows(BadCredentialsException.class,()->servicio.login(new LoginDTO(usuario.getCorreo(),"incorrecta")));
        verifyNoInteractions(jwt,sesiones);
    }
}
