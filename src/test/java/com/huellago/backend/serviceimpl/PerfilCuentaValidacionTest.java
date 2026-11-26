package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.UsuarioPerfilDTO;
import com.huellago.backend.repositories.UsuarioRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerfilCuentaValidacionTest {
    @Mock UsuarioRepository usuarioRepository;
    @InjectMocks UsuarioServiceImpl servicio;
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","A","Nombre1"})
    void nombresInvalidosNoAccedenAPersistencia(String nombre){rechazar(new UsuarioPerfilDTO(nombre,"Quispe","cuenta@example.invalid"));}
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","A","Apellido1"})
    void apellidosInvalidosNoAccedenAPersistencia(String apellido){rechazar(new UsuarioPerfilDTO("Carlos",apellido,"cuenta@example.invalid"));}
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","invalido","a@"})
    void correoInvalidoNoAccedeAPersistencia(String correo){rechazar(new UsuarioPerfilDTO("Carlos","Quispe",correo));}
    void rechazar(UsuarioPerfilDTO dto){
        assertEquals(400,assertThrows(ResponseStatusException.class,()->servicio.actualizarPerfil("actual@example.invalid",dto)).getStatusCode().value());
        verifyNoInteractions(usuarioRepository);
    }
}
