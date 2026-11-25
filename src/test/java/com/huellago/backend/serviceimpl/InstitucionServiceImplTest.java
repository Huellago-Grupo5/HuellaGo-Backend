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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstitucionServiceImplTest {
    @Mock InstitucionRepository instituciones;
    @Mock UsuarioInstitucionRepository afiliaciones;
    @Mock UsuarioRepository usuarios;
    @Mock GrupoInstitucionalRepository grupos;
    @InjectMocks InstitucionServiceImpl servicio;
    private Usuario representante;

    @BeforeEach
    void preparar() {
        representante = new Usuario();
        representante.setId(22L);
    }

    @Test void rankingIndividualInformaSiExistenGruposSinCambiarParticipantes() {
        when(instituciones.existsById(5L)).thenReturn(true);
        when(afiliaciones.existsByUsuario_IdAndInstitucion_IdAndRol(
                22L, 5L, RolInstitucional.ADMIN_INSTITUCIONAL)).thenReturn(true);
        GrupoInstitucional grupo = new GrupoInstitucional();
        grupo.setNombre("Equipo A");
        when(grupos.findByInstitucion_IdOrderByIdAsc(5L)).thenReturn(List.of(grupo));
        var ranking = servicio.consultarRanking(22L, 5L);
        assertTrue(ranking.gruposDisponibles());
        assertTrue(ranking.participantes().isEmpty());
    }

    @Test void rankingInstitucionalOrdenadoYLimitadoAMiembrosVisibles() {
        when(instituciones.existsById(5L)).thenReturn(true);
        when(afiliaciones.existsByUsuario_IdAndInstitucion_IdAndRol(
                22L, 5L, RolInstitucional.ADMIN_INSTITUCIONAL)).thenReturn(true);
        var primero = mock(UsuarioInstitucionRepository.FilaRankingInstitucional.class);
        when(primero.getUsuarioId()).thenReturn(22L);
        when(primero.getNombre()).thenReturn(" Administrador ");
        when(primero.getEcoPuntos()).thenReturn(90);
        when(primero.getNivel()).thenReturn(2);
        var segundo = mock(UsuarioInstitucionRepository.FilaRankingInstitucional.class);
        when(segundo.getUsuarioId()).thenReturn(70L);
        when(segundo.getNombre()).thenReturn("Miembro");
        when(segundo.getEcoPuntos()).thenReturn(50);
        when(segundo.getNivel()).thenReturn(1);
        when(afiliaciones.listarRanking(5L)).thenReturn(List.of(primero, segundo));

        var ranking = servicio.consultarRanking(22L, 5L);

        assertEquals(2, ranking.participantes().size());
        assertEquals(1, ranking.miPosicion());
        assertEquals("Administrador", ranking.participantes().getFirst().nombreVisible());
        assertEquals(90, ranking.participantes().getFirst().ecoPuntos());
        assertFalse(ranking.gruposDisponibles());
        verify(afiliaciones).listarRanking(5L);
    }

    @Test void rankingRechazaAdministradoresDeOtraInstitucion() {
        when(instituciones.existsById(5L)).thenReturn(true);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> servicio.consultarRanking(22L, 5L)).getStatusCode());
        verify(afiliaciones, never()).listarRanking(any());
    }

    @Test void rankingVacioInformaYConsultaSoloMiembrosVisibles() throws Exception {
        when(instituciones.existsById(5L)).thenReturn(true);
        when(afiliaciones.existsByUsuario_IdAndInstitucion_IdAndRol(
                22L, 5L, RolInstitucional.ADMIN_INSTITUCIONAL)).thenReturn(true);
        var ranking = servicio.consultarRanking(22L, 5L);
        assertTrue(ranking.participantes().isEmpty());
        assertNull(ranking.miPosicion());
        assertNotNull(ranking.mensaje());
        String consulta = UsuarioInstitucionRepository.class.getMethod("listarRanking", Long.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
        assertTrue(consulta.contains("ui.institucion.id = :institucionId"));
        assertTrue(consulta.contains("u.visibilidadComunidad = true"));
        assertTrue(consulta.contains("u.ecoPuntos desc, u.id asc"));
        assertFalse(consulta.contains("correo"));
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
