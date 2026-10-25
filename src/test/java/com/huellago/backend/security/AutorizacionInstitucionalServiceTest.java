package com.huellago.backend.security;

import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.UsuarioInstitucionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutorizacionInstitucionalServiceTest {
    @Mock UsuarioInstitucionRepository afiliaciones;
    @InjectMocks AutorizacionInstitucionalService servicio;

    @Test
    void administradorSeValidaEnElVinculoBloqueadoDeSuInstitucion() {
        UsuarioInstitucion vinculo = new UsuarioInstitucion();
        vinculo.setRol(RolInstitucional.ADMIN_INSTITUCIONAL);
        when(afiliaciones.bloquearVinculo(77L, 5L)).thenReturn(Optional.of(vinculo));
        assertDoesNotThrow(() -> servicio.exigirAdministrador(77L, 5L));
        verify(afiliaciones).bloquearVinculo(77L, 5L);
    }

    @Test
    void miembroOrdinarioNoRecibePermisosAdministrativos() {
        UsuarioInstitucion vinculo = new UsuarioInstitucion();
        vinculo.setRol(RolInstitucional.MIEMBRO);
        when(afiliaciones.bloquearVinculo(77L, 5L)).thenReturn(Optional.of(vinculo));
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> servicio.exigirAdministrador(77L, 5L)).getStatusCode().value());
        assertDoesNotThrow(() -> servicio.exigirMiembroParaParticipar(77L, 5L));
    }

    @Test
    void administradorDeOtraInstitucionNoTieneAcceso() {
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> servicio.exigirAdministrador(77L, 6L)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> servicio.exigirMiembro(77L, 6L)).getStatusCode().value());
    }

    @Test
    void miembroSeIdentificaPorRelacionPersistida() {
        when(afiliaciones.existsByUsuario_IdAndInstitucion_Id(88L, 5L)).thenReturn(true);
        assertTrue(servicio.esMiembro(88L, 5L));
        assertFalse(servicio.esMiembro(null, 5L));
        assertDoesNotThrow(() -> servicio.exigirMiembro(88L, 5L));
    }

    @Test
    void afiliacionEliminadaNoPermiteNuevasParticipaciones() {
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> servicio.exigirMiembroParaParticipar(88L, 5L)).getStatusCode().value());
        verify(afiliaciones).bloquearVinculo(88L, 5L);
    }
}
