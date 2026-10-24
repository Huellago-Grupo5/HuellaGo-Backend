package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.InstitucionRegistroDTO;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstitucionServiceImplTest {
    @Mock InstitucionRepository instituciones;
    @Mock UsuarioInstitucionRepository afiliaciones;
    @Mock UsuarioRepository usuarios;
    @InjectMocks InstitucionServiceImpl servicio;
    private Usuario representante;

    @BeforeEach
    void preparar() {
        representante = new Usuario();
        representante.setId(22L);
    }

    @Test
    void registraUniversidadYAfiliacionAdministrativaEnUnaTransaccion() throws Exception {
        when(usuarios.findById(22L)).thenReturn(Optional.of(representante));
        when(instituciones.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var respuesta = servicio.registrar(22L, datos(TipoInstitucion.UNIVERSIDAD));
        ArgumentCaptor<Institucion> institucion = ArgumentCaptor.forClass(Institucion.class);
        ArgumentCaptor<UsuarioInstitucion> afiliacion = ArgumentCaptor.forClass(UsuarioInstitucion.class);
        verify(instituciones).saveAndFlush(institucion.capture());
        verify(afiliaciones).saveAndFlush(afiliacion.capture());
        assertEquals("PE", institucion.getValue().getPais());
        assertEquals("20123456789", institucion.getValue().getIdentificadorFiscal());
        assertSame(institucion.getValue(), afiliacion.getValue().getInstitucion());
        assertSame(representante, afiliacion.getValue().getUsuario());
        assertEquals(RolInstitucional.ADMIN_INSTITUCIONAL, afiliacion.getValue().getRol());
        assertEquals(22L, respuesta.representanteId());
        assertNotNull(InstitucionServiceImpl.class.getMethod("registrar", Long.class,
                InstitucionRegistroDTO.class).getAnnotation(org.springframework.transaction.annotation.Transactional.class));
    }

    @Test
    void registraEmpresa() {
        when(usuarios.findById(22L)).thenReturn(Optional.of(representante));
        when(instituciones.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(TipoInstitucion.EMPRESA, servicio.registrar(22L, datos(TipoInstitucion.EMPRESA)).tipo());
    }

    @Test
    void rechazaInstitucionDuplicada() {
        when(usuarios.findById(22L)).thenReturn(Optional.of(representante));
        when(instituciones.existsByPaisAndIdentificadorFiscal("PE", "20123456789")).thenReturn(true);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> servicio.registrar(22L, datos(TipoInstitucion.EMPRESA)));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(instituciones, never()).saveAndFlush(any());
    }

    @Test
    void rechazaAfiliacionPreexistente() {
        when(usuarios.findById(22L)).thenReturn(Optional.of(representante));
        when(afiliaciones.existsByUsuario_IdAndInstitucion_PaisAndInstitucion_IdentificadorFiscal(
                22L, "PE", "20123456789")).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> servicio.registrar(22L, datos(TipoInstitucion.UNIVERSIDAD))).getStatusCode());
        verify(instituciones, never()).saveAndFlush(any());
    }

    @Test
    void convierteConflictoConcurrenteEn409() {
        when(usuarios.findById(22L)).thenReturn(Optional.of(representante));
        when(instituciones.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> servicio.registrar(22L, datos(TipoInstitucion.UNIVERSIDAD))).getStatusCode());
        verify(afiliaciones, never()).saveAndFlush(any());
    }

    @Test
    void soloAdministradorDeLaInstitucionPuedeConsultarla() {
        Institucion institucion = new Institucion();
        institucion.setNombre("Universidad Verde");
        when(instituciones.findById(7L)).thenReturn(Optional.of(institucion));
        when(afiliaciones.existsByUsuario_IdAndInstitucion_IdAndRol(
                22L, 7L, RolInstitucional.ADMIN_INSTITUCIONAL)).thenReturn(true);
        assertEquals("Universidad Verde", servicio.consultarAdministracion(22L, 7L).nombre());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> servicio.consultarAdministracion(99L, 7L)).getStatusCode());
    }

    private InstitucionRegistroDTO datos(TipoInstitucion tipo) {
        return new InstitucionRegistroDTO(tipo, "Universidad Verde", "pe", "20123456789", "contacto@ejemplo.pe");
    }
}
