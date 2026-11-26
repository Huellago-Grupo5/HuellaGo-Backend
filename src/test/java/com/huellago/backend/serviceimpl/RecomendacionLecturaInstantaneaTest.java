package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Solo repositorios Mockito y objetos sintéticos; sin contexto Spring ni DataSource. */
@ExtendWith(MockitoExtension.class)
class RecomendacionLecturaInstantaneaTest {
    @Mock UsuarioRecomendacionIARepository asociaciones;
    @Mock RetoRepository retos;
    @Mock UsuarioRetoRepository participaciones;
    @Mock UsuarioRepository usuarios;
    @InjectMocks RecomendacionServiceImpl servicio;
    Usuario usuario;
    RecomendacionIA compartida;
    UsuarioRecomendacionIA a;
    LocalDateTime fecha = LocalDateTime.of(2026, 11, 1, 10, 0);

    @BeforeEach void preparar() {
        usuario = new Usuario(); usuario.setId(101L); usuario.setEcoPuntos(30); usuario.setNivel(1);
        compartida = new RecomendacionIA(); compartida.setId(8);
        compartida.setTitulo("Título compartido"); compartida.setDescripcion("Descripción compartida");
        a = new UsuarioRecomendacionIA(3, "Pendiente", fecha, null, usuario, compartida);
    }
    void instantanea() {
        compartida.setAccionId("TR_PLANIFICAR_DESPLAZAMIENTOS"); compartida.setVersionAccion("1");
        a.setTituloInstantanea("  Título personal exacto  ");
        a.setDescripcionInstantanea("Paso personal.\nSegunda línea.");
        a.setMotivoInstantanea("Motivo original del usuario.");
    }
    void historial(List<UsuarioRecomendacionIA> registros) {
        when(asociaciones.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(101L, "completado"))
                .thenReturn(registros);
    }
    void completarPropia() {
        when(usuarios.bloquearParaRecomendaciones(101L)).thenReturn(Optional.of(101L));
        when(asociaciones.bloquearPropia(3, 101L)).thenReturn(Optional.of(3));
        when(asociaciones.findByIdAndUsuario_Id(3, 101L)).thenReturn(Optional.of(a));
    }
    void convertirPropia() {
        when(asociaciones.bloquearPropia(3, 101L)).thenReturn(Optional.of(3));
        when(asociaciones.findById(3)).thenReturn(Optional.of(a));
    }
    @Test void historialHeredadoConservaContenidoYFechasSinGuardar() {
        a.setEstado("completado"); a.setFechaCompletado(fecha.plusDays(1)); historial(List.of(a));
        var dto = servicio.historialCompletadas(usuario).getFirst();
        assertEquals(compartida.getTitulo(), dto.titulo());
        assertEquals(compartida.getDescripcion(), dto.descripcion());
        assertEquals(fecha, dto.fechaGeneracion()); assertEquals(a.getFechaCompletado(), dto.fechaCompletado());
        verify(asociaciones, never()).saveAndFlush(any());
    }
    @Test void deterministaConPrioridadYBaseSinInstantaneaSeConsultaEnAmbosIdiomas() {
        var categoria = new CategoriaHabito(); categoria.setNombre("Transporte");
        compartida.setCategoria(categoria);
        a.setPrioridadInstantanea("ALTA"); a.setBasePriorizacion("HUELLA_REGISTRADA");
        a.setHuellaOrigenId(12L);
        servicio.traducciones = new PresentacionesLocalizadasIA();
        when(asociaciones.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(101L, "pendiente"))
                .thenReturn(List.of(a));
        for (var idioma : List.of(java.util.Locale.forLanguageTag("es-419"), java.util.Locale.US)) {
            org.springframework.context.i18n.LocaleContextHolder.setLocale(idioma);
            var dto = servicio.pendientes(usuario, null).getFirst();
            assertEquals(3, dto.getUsuarioRecomendacionId());
            assertEquals(8, dto.getRecomendacionId());
            assertEquals("Título compartido", dto.getTitulo());
            assertEquals("Descripción compartida", dto.getDescripcion());
            assertEquals("ALTA", dto.getPrioridad());
        }
        org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
        a.setEstado("completado"); a.setFechaCompletado(fecha.plusDays(1)); historial(List.of(a));
        assertEquals("Título compartido", servicio.historialCompletadas(usuario).getFirst().titulo());
        verify(asociaciones, never()).save(any()); verify(asociaciones, never()).saveAndFlush(any());
    }
    @Test void instantaneaValidaSinVersionEditorialConservaTextosOriginalesEnIngles() {
        instantanea(); compartida.setVersionAccion(null); a.setVersionPresentacion(null);
        servicio.traducciones = new PresentacionesLocalizadasIA();
        a.setEstado("completado"); historial(List.of(a));
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.US);
        try {
            assertEquals("  Título personal exacto  ", servicio.historialCompletadas(usuario).getFirst().titulo());
        } finally { org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext(); }
    }
    @Test void historialConservaOrdenDevueltoPorRepositorioYTextosExactos() {
        instantanea(); a.setEstado("completado"); a.setFechaCompletado(fecha.plusDays(2));
        var anterior = new UsuarioRecomendacionIA(2, "completado", fecha, fecha.plusDays(1), usuario, compartida);
        anterior.setTituloInstantanea("Título anterior"); anterior.setDescripcionInstantanea("Paso anterior");
        anterior.setMotivoInstantanea("Motivo anterior");
        historial(List.of(a, anterior));
        var resultado = servicio.historialCompletadas(usuario);
        assertEquals(List.of(3, 2), resultado.stream().map(x -> x.usuarioRecomendacionId()).toList());
        assertEquals(a.getTituloInstantanea(), resultado.getFirst().titulo());
        assertEquals(a.getDescripcionInstantanea(), resultado.getFirst().descripcion());
        assertEquals(fecha.plusDays(2), resultado.getFirst().fechaCompletado());
        assertEquals("Título anterior", resultado.getLast().titulo());
        assertEquals("Título compartido", compartida.getTitulo());
        assertEquals("Motivo original del usuario.", a.getMotivoInstantanea());
        verify(asociaciones, never()).saveAndFlush(any());
    }
    @Test void dosUsuariosObtienenSusInstantaneasDeLaMismaRecomendacion() {
        instantanea(); historial(List.of(a));
        Usuario otro = new Usuario(); otro.setId(102L);
        var b = new UsuarioRecomendacionIA(4, "completado", fecha, fecha.plusDays(1), otro, compartida);
        b.setTituloInstantanea("Título del otro usuario"); b.setDescripcionInstantanea("Su paso");
        b.setMotivoInstantanea("Su motivo");
        when(asociaciones.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(102L, "completado"))
                .thenReturn(List.of(b));
        assertEquals(a.getTituloInstantanea(), servicio.historialCompletadas(usuario).getFirst().titulo());
        assertEquals(b.getTituloInstantanea(), servicio.historialCompletadas(otro).getFirst().titulo());
    }
    @Test void completarUsaInstantaneaSinRecompensasNiModificarContenido() {
        instantanea(); completarPropia(); when(asociaciones.saveAndFlush(a)).thenReturn(a);
        var dto = servicio.completar(3, usuario);
        assertEquals(a.getTituloInstantanea(), dto.titulo()); assertEquals(a.getDescripcionInstantanea(), dto.descripcion());
        assertEquals("completado", dto.estado()); assertEquals(fecha, dto.fechaGeneracion());
        assertNotNull(dto.fechaCompletado()); assertEquals(30, usuario.getEcoPuntos()); assertEquals(1, usuario.getNivel());
        assertEquals("Motivo original del usuario.", a.getMotivoInstantanea());
        verifyNoInteractions(retos, participaciones);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> servicio.completar(3, usuario)).getStatusCode());
        verify(asociaciones, times(1)).saveAndFlush(any());
    }
    @Test void convertirCopiaInstantaneaExactaInclusoSiYaEstaCompletada() {
        instantanea(); a.setEstado("completado"); a.setFechaCompletado(fecha.plusDays(1)); convertirPropia();
        when(retos.saveAndFlush(any())).thenAnswer(i -> { Reto r = i.getArgument(0); r.setId(40); return r; });
        when(participaciones.saveAndFlush(any())).thenAnswer(i -> { UsuarioReto p = i.getArgument(0); p.setId(50); return p; });
        var dto = servicio.convertirEnReto(3, usuario);
        assertEquals(40, dto.retoId()); assertEquals("activo", dto.estado());
        verify(retos).saveAndFlush(argThat(r -> a.getTituloInstantanea().equals(r.getTitulo())
                && a.getDescripcionInstantanea().equals(r.getDescripcion()) && r.getPuntosRecompensa() == 0
                && r.getTipo() == TipoReto.INDIVIDUAL && r.getRecomendacionOrigen() == a));
        assertEquals("completado", a.getEstado()); assertEquals(fecha.plusDays(1), a.getFechaCompletado());
        assertEquals("Título compartido", compartida.getTitulo());
        verify(asociaciones, never()).saveAndFlush(any());
    }
    @ParameterizedTest @CsvSource({"titulo,NULL", "titulo,BLANCO", "descripcion,NULL", "descripcion,BLANCO", "motivo,NULL", "motivo,BLANCO"})
    void instantaneaParcialFallaEnHistorialCompletarYConvertirSinEscrituras(String campo, String caso) {
        instantanea(); String valor = caso.equals("NULL") ? null : " \t ";
        switch (campo) { case "titulo" -> a.setTituloInstantanea(valor);
            case "descripcion" -> a.setDescripcionInstantanea(valor); default -> a.setMotivoInstantanea(valor); }
        historial(List.of(a)); completarPropia(); convertirPropia();
        for (Runnable operacion : List.<Runnable>of(() -> servicio.historialCompletadas(usuario),
                () -> servicio.completar(3, usuario), () -> servicio.convertirEnReto(3, usuario))) {
            var error = assertThrows(ResponseStatusException.class, operacion::run);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getStatusCode());
            assertEquals("No se pudo recuperar el contenido personalizado de la recomendación", error.getReason());
        }
        assertEquals("Pendiente", a.getEstado()); assertNull(a.getFechaCompletado());
        verify(asociaciones, never()).saveAndFlush(any()); verify(retos, never()).saveAndFlush(any());
        verifyNoInteractions(participaciones);
    }
    @ParameterizedTest @CsvSource({"accion", "version", "metodo", "modelo", "contexto", "textoVacio"})
    void marcasAisladasNoPermitenFallbackCompartido(String marca) {
        switch (marca) { case "accion" -> compartida.setAccionId("TR_PLANIFICAR_DESPLAZAMIENTOS");
            case "version" -> compartida.setVersionAccion("1");
            case "metodo" -> a.setMetodoGeneracion("OLLAMA_SELECCION_PRESENTACION_CONTROLADA");
            case "modelo" -> a.setModeloGeneracion("qwen3:4b-instruct");
            case "contexto" -> a.setReferenciaContexto("digest-sintetico");
            default -> a.setTituloInstantanea(""); }
        historial(List.of(a));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, assertThrows(ResponseStatusException.class,
                () -> servicio.historialCompletadas(usuario)).getStatusCode());
    }
    @Test void instantaneaCompletaSeUsaAunqueNoHayaIdentidadCatalogadaTodavia() {
        instantanea(); compartida.setAccionId(null); compartida.setVersionAccion(null); historial(List.of(a));
        assertEquals(a.getDescripcionInstantanea(), servicio.historialCompletadas(usuario).getFirst().descripcion());
    }
}
