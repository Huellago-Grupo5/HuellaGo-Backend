package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.GrupoInstitucionalDTO;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.AutorizacionInstitucionalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GrupoInstitucionalServiceImplTest {
    InstitucionRepository instituciones = mock(InstitucionRepository.class);
    GrupoInstitucionalRepository grupos = mock(GrupoInstitucionalRepository.class);
    InvitacionInstitucionalRepository invitaciones = mock(InvitacionInstitucionalRepository.class);
    UsuarioInstitucionRepository afiliaciones = mock(UsuarioInstitucionRepository.class);
    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    GrupoInstitucionalServiceImpl servicio = new GrupoInstitucionalServiceImpl(instituciones, grupos,
            invitaciones, afiliaciones, usuarios, new AutorizacionInstitucionalService(afiliaciones));
    Institucion institucion = new Institucion();
    GrupoInstitucional equipo = new GrupoInstitucional();

    @BeforeEach void preparar() {
        ReflectionTestUtils.setField(institucion, "id", 5L);
        ReflectionTestUtils.setField(equipo, "id", 8L);
        equipo.setInstitucion(institucion); equipo.setNombre("Equipo Azul"); equipo.setTipo(TipoGrupoInstitucional.EQUIPO);
        when(instituciones.findById(5L)).thenReturn(Optional.of(institucion));
        when(grupos.findByIdAndInstitucion_Id(8L, 5L)).thenReturn(Optional.of(equipo));
        when(afiliaciones.bloquearVinculo(1L, 5L)).thenReturn(Optional.of(admin()));
        when(afiliaciones.existsByUsuario_IdAndInstitucion_IdAndRol(1L, 5L, RolInstitucional.ADMIN_INSTITUCIONAL)).thenReturn(true);
    }
    UsuarioInstitucion admin() {
        UsuarioInstitucion ui = new UsuarioInstitucion(); ui.setRol(RolInstitucional.ADMIN_INSTITUCIONAL); return ui;
    }
    void estado(int codigo, Runnable accion) {
        assertEquals(codigo, assertThrows(ResponseStatusException.class, accion::run).getStatusCode().value());
    }
    @Test void soloAdministradorCreaGrupos() {
        estado(403, () -> servicio.crear(99L, 5L, new GrupoInstitucionalDTO.Datos(TipoGrupoInstitucional.EQUIPO, "Equipo Azul")));
        verifyNoInteractions(grupos);
    }
    @Test void creaYListaGruposSinDuplicarNombre() {
        when(grupos.saveAndFlush(any())).thenAnswer(inv -> {
            GrupoInstitucional g = inv.getArgument(0); ReflectionTestUtils.setField(g, "id", 8L); return g;
        });
        var creado = servicio.crear(1L, 5L, new GrupoInstitucionalDTO.Datos(TipoGrupoInstitucional.EQUIPO, " Equipo Azul "));
        assertEquals("Equipo Azul", creado.nombre()); assertEquals(TipoGrupoInstitucional.EQUIPO, creado.tipo());
        when(grupos.existsByInstitucion_IdAndNombreIgnoreCase(5L, "Equipo Azul")).thenReturn(true);
        estado(409, () -> servicio.crear(1L, 5L, new GrupoInstitucionalDTO.Datos(TipoGrupoInstitucional.EQUIPO, "Equipo Azul")));
        when(grupos.findByInstitucion_IdOrderByIdAsc(5L)).thenReturn(List.of(equipo));
        assertEquals(1, servicio.listar(1L, 5L).size());
    }
    @Test void invitacionEsDeUnSoloUsoYNoGuardaElCodigoEnClaro() {
        when(invitaciones.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        String codigo = servicio.invitar(1L, 5L).codigo();
        var captor = org.mockito.ArgumentCaptor.forClass(InvitacionInstitucional.class);
        verify(invitaciones).saveAndFlush(captor.capture());
        InvitacionInstitucional inv = captor.getValue();
        assertEquals(43, codigo.length()); assertNotEquals(codigo, inv.getCodigoHash());
        assertEquals(64, inv.getCodigoHash().length()); assertNull(inv.getFechaUso());
        when(usuarios.existsById(2L)).thenReturn(true);
        when(invitaciones.findByCodigoHash(inv.getCodigoHash())).thenReturn(Optional.of(inv));
        when(usuarios.getReferenceById(2L)).thenReturn(new Usuario());
        when(afiliaciones.saveAndFlush(any())).thenAnswer(a -> a.getArgument(0));
        assertEquals(5L, servicio.afiliar(2L, codigo).institucionId());
        assertNotNull(inv.getFechaUso());
        estado(409, () -> servicio.afiliar(2L, codigo));
    }
    @Test void invitacionVencidaYAfiliacionDuplicadaSeRechazan() {
        when(usuarios.existsById(2L)).thenReturn(true);
        InvitacionInstitucional inv = new InvitacionInstitucional(); inv.setInstitucion(institucion);
        inv.setFechaExpiracion(LocalDateTime.now().minusDays(1));
        when(invitaciones.findByCodigoHash(anyString())).thenReturn(Optional.of(inv));
        String codigo = "a".repeat(43);
        estado(409, () -> servicio.afiliar(2L, codigo));
        inv.setFechaExpiracion(LocalDateTime.now().plusDays(1));
        when(afiliaciones.existsByUsuario_IdAndInstitucion_Id(2L, 5L)).thenReturn(true);
        estado(409, () -> servicio.afiliar(2L, codigo));
    }
    @Test void noAsignaUsuarioDeOtraInstitucion() {
        estado(404, () -> servicio.asignar(1L, 5L, 8L, 22L));
        verify(afiliaciones).bloquearParaGrupo(22L, 5L);
        verify(afiliaciones, never()).saveAndFlush(any());
    }
    @Test void asignaYDesasignaMiembroAfiliado() {
        UsuarioInstitucion miembro = new UsuarioInstitucion();
        when(afiliaciones.bloquearParaGrupo(22L, 5L)).thenReturn(Optional.of(miembro));
        servicio.asignar(1L, 5L, 8L, 22L); assertSame(equipo, miembro.getGrupo());
        servicio.desasignar(1L, 5L, 8L, 22L); assertNull(miembro.getGrupo());
        verify(afiliaciones, times(2)).saveAndFlush(miembro);
    }
    record Fila(Long getGrupoId, Integer getEcoPuntos) implements UsuarioInstitucionRepository.FilaGrupoVisible { }
    @Test void rankingSumaSoloVisiblesYDesempataPorId() {
        GrupoInstitucional segundo = new GrupoInstitucional(); ReflectionTestUtils.setField(segundo, "id", 9L);
        segundo.setInstitucion(institucion); segundo.setTipo(TipoGrupoInstitucional.FACULTAD); segundo.setNombre("Facultad");
        when(grupos.findByInstitucion_IdOrderByIdAsc(5L)).thenReturn(List.of(equipo, segundo));
        when(afiliaciones.listarMiembrosVisiblesParaRankingGrupos(5L))
                .thenReturn(List.of(new Fila(8L, 20), new Fila(8L, 10), new Fila(9L, 30)));
        var ranking = servicio.ranking(1L, 5L);
        assertEquals(2, ranking.grupos().size()); assertEquals(8L, ranking.grupos().getFirst().grupoId());
        assertEquals(30, ranking.grupos().getFirst().ecoPuntos());
        assertEquals(2, ranking.grupos().getFirst().participantesVisibles());
        assertEquals(1, ranking.grupos().getLast().participantesVisibles());
    }
    @Test void consultaDeRankingExcluyePrivadosYGruposDeOtrasInstituciones() throws Exception {
        String consulta = UsuarioInstitucionRepository.class
                .getMethod("listarMiembrosVisiblesParaRankingGrupos", Long.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
        assertTrue(consulta.contains("ui.institucion.id = :institucionId"));
        assertTrue(consulta.contains("ui.grupo.institucion.id = :institucionId"));
        assertTrue(consulta.contains("u.visibilidadComunidad = true"));
    }
}
