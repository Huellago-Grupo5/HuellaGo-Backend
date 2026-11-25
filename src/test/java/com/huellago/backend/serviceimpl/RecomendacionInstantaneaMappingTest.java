package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.RecomendacionIARepository;
import com.huellago.backend.repositories.UsuarioRecomendacionIARepository;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.repository.query.parser.PartTree;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Metadatos y objetos solamente: no arranca Hibernate, Spring ni un DataSource.
 * No acredita la existencia de columnas o restricciones en PostgreSQL. */
class RecomendacionInstantaneaMappingTest {
    private record Campo(Class<?> entidad, String propiedad, String columna, Class<?> tipo, int longitud) { }

    static Stream<Campo> camposNuevos() {
        return Stream.of(
                new Campo(RecomendacionIA.class, "accionId", "accion_id", String.class, 100),
                new Campo(RecomendacionIA.class, "versionAccion", "version_accion", String.class, 30),
                new Campo(UsuarioRecomendacionIA.class, "tituloInstantanea", "titulo_instantanea", String.class, 160),
                new Campo(UsuarioRecomendacionIA.class, "descripcionInstantanea", "descripcion_instantanea", String.class, 1000),
                new Campo(UsuarioRecomendacionIA.class, "motivoInstantanea", "motivo_instantanea", String.class, 1000),
                new Campo(UsuarioRecomendacionIA.class, "enfoque", "enfoque", String.class, 100),
                new Campo(UsuarioRecomendacionIA.class, "versionPresentacion", "version_presentacion", String.class, 30),
                new Campo(UsuarioRecomendacionIA.class, "metodoGeneracion", "metodo_generacion", String.class, 100),
                new Campo(UsuarioRecomendacionIA.class, "referenciaContexto", "referencia_contexto", String.class, 128),
                new Campo(UsuarioRecomendacionIA.class, "prioridadInstantanea", "prioridad_instantanea", String.class, 20),
                new Campo(UsuarioRecomendacionIA.class, "basePriorizacion", "base_priorizacion", String.class, 40),
                new Campo(UsuarioRecomendacionIA.class, "huellaOrigenId", "huella_origen_id", Long.class, 255),
                new Campo(UsuarioRecomendacionIA.class, "modeloGeneracion", "modelo_generacion", String.class, 128));
    }

    @ParameterizedTest
    @MethodSource("camposNuevos")
    void columnasOpcionalesInsertablesSinActualizacionJPA(Campo esperado) throws Exception {
        Field campo = esperado.entidad().getDeclaredField(esperado.propiedad());
        Column columna = campo.getAnnotation(Column.class);
        assertNotNull(columna);
        assertEquals(esperado.tipo(), campo.getType());
        assertEquals(esperado.columna(), columna.name());
        assertEquals(esperado.longitud(), columna.length());
        assertTrue(columna.nullable());
        assertTrue(columna.insertable());
        assertFalse(columna.updatable());
        assertFalse(columna.unique());
        assertNull(campo.getAnnotation(ManyToOne.class));
        Object heredado = esperado.entidad().getConstructor().newInstance();
        campo.setAccessible(true);
        assertNull(campo.get(heredado));
    }

    @Test
    void constructorAnteriorDeCatalogoConservaTodosLosCampos() {
        CategoriaHabito categoria = new CategoriaHabito();
        List<UsuarioRecomendacionIA> asociaciones = new ArrayList<>();
        var r = new RecomendacionIA(7, "Anterior", "Descripción", "MEDIA", 0, categoria, asociaciones);
        assertEquals(7, r.getId());
        assertEquals("Anterior", r.getTitulo());
        assertEquals("Descripción", r.getDescripcion());
        assertEquals("MEDIA", r.getPrioridad());
        assertEquals(0, r.getPuntosRecompensa());
        assertSame(categoria, r.getCategoria());
        assertSame(asociaciones, r.getUsuariosRecomendacionesIA());
        assertNull(r.getAccionId());
        assertNull(r.getVersionAccion());
    }

    @Test
    void constructorAnteriorDeAsociacionConservaFechasEstadoYRelaciones() {
        Usuario usuario = new Usuario();
        RecomendacionIA recomendacion = new RecomendacionIA();
        LocalDateTime generacion = LocalDateTime.of(2026, 11, 1, 10, 0);
        LocalDateTime completado = generacion.plusDays(1);
        var a = new UsuarioRecomendacionIA(5, "completado", generacion, completado, usuario, recomendacion);
        assertEquals(5, a.getId());
        assertEquals("completado", a.getEstado());
        assertEquals(generacion, a.getFechaGeneracion());
        assertEquals(completado, a.getFechaCompletado());
        assertSame(usuario, a.getUsuario());
        assertSame(recomendacion, a.getRecomendacionIA());
        assertNull(a.getTituloInstantanea());
        assertNull(a.getMetodoGeneracion());
    }

    @Test
    void identidadNoDependeDelTituloYNuncaSeAsignaPorDefecto() {
        var r = new RecomendacionIA();
        r.setTitulo("Texto compartido");
        assertNull(r.getAccionId());
        r.setAccionId("TR_PLANIFICAR_DESPLAZAMIENTOS");
        r.setVersionAccion("1");
        r.setTitulo("Otro título");
        assertEquals("TR_PLANIFICAR_DESPLAZAMIENTOS", r.getAccionId());
        assertEquals("1", r.getVersionAccion());
    }

    @Test
    void dosUsuariosCompartenAccionSinCompartirInstantanea() {
        RecomendacionIA accion = new RecomendacionIA();
        accion.setAccionId("TR_PLANIFICAR_DESPLAZAMIENTOS");
        accion.setVersionAccion("1");
        accion.setTitulo("Catálogo");
        Usuario u1 = new Usuario(); u1.setId(101L);
        Usuario u2 = new Usuario(); u2.setId(102L);
        var a = new UsuarioRecomendacionIA(1, "Pendiente", LocalDateTime.MIN, null, u1, accion);
        var b = new UsuarioRecomendacionIA(2, "Pendiente", LocalDateTime.MIN, null, u2, accion);
        a.setTituloInstantanea("Organiza tus próximas gestiones");
        a.setDescripcionInstantanea("Anota las necesidades de cada salida.");
        a.setMotivoInstantanea("Registraste 100 km semanales en Auto.");
        b.setTituloInstantanea("Organiza tus próximas gestiones");
        b.setDescripcionInstantanea("Comprueba qué gestiones puedes organizar.");
        b.setMotivoInstantanea("Registraste 40 km semanales en Auto.");
        a.setEnfoque("NECESIDADES");
        assertSame(a.getRecomendacionIA(), b.getRecomendacionIA());
        assertNotEquals(a.getUsuario().getId(), b.getUsuario().getId());
        assertNotEquals(a.getDescripcionInstantanea(), b.getDescripcionInstantanea());
        assertNotEquals(a.getMotivoInstantanea(), b.getMotivoInstantanea());
        assertNull(b.getEnfoque());
        assertEquals("Catálogo", accion.getTitulo());
        accion.setTitulo("Catálogo posterior");
        assertEquals("Organiza tus próximas gestiones", a.getTituloInstantanea());
        assertEquals("Registraste 100 km semanales en Auto.", a.getMotivoInstantanea());
    }

    @Test
    void procedenciaYContextoNoModificanEstadoDeRealizacion() {
        var a = new UsuarioRecomendacionIA();
        a.setEstado("completado");
        a.setFechaGeneracion(LocalDateTime.MIN);
        a.setFechaCompletado(LocalDateTime.MIN.plusDays(1));
        a.setVersionPresentacion("1");
        a.setMetodoGeneracion("OLLAMA_SELECCION_PRESENTACION_CONTROLADA");
        a.setReferenciaContexto("digest-sintetico-no-personal");
        a.setPrioridadInstantanea("ALTA");
        a.setBasePriorizacion("HUELLA_REGISTRADA");
        a.setHuellaOrigenId(8L);
        a.setModeloGeneracion("qwen3:4b-instruct");
        assertEquals("completado", a.getEstado());
        assertEquals(LocalDateTime.MIN, a.getFechaGeneracion());
        assertEquals(LocalDateTime.MIN.plusDays(1), a.getFechaCompletado());
        assertEquals("1", a.getVersionPresentacion());
        assertEquals("OLLAMA_SELECCION_PRESENTACION_CONTROLADA", a.getMetodoGeneracion());
        assertEquals("digest-sintetico-no-personal", a.getReferenciaContexto());
        assertEquals("ALTA", a.getPrioridadInstantanea());
        assertEquals("HUELLA_REGISTRADA", a.getBasePriorizacion());
        assertEquals(8L, a.getHuellaOrigenId());
        assertEquals("qwen3:4b-instruct", a.getModeloGeneracion());
    }

    @Test
    void tablasIdentidadYCamposAnterioresNoSeRedefinen() throws Exception {
        assertEquals("recomendaciones_ia", RecomendacionIA.class.getAnnotation(Table.class).name());
        assertEquals("usuarios_recomendaciones_ia", UsuarioRecomendacionIA.class.getAnnotation(Table.class).name());
        for (Class<?> tipo : List.of(RecomendacionIA.class, UsuarioRecomendacionIA.class)) {
            assertEquals(0, tipo.getAnnotation(Table.class).uniqueConstraints().length);
            Field id = tipo.getDeclaredField("id");
            assertNotNull(id.getAnnotation(Id.class));
            assertEquals(GenerationType.IDENTITY, id.getAnnotation(GeneratedValue.class).strategy());
        }
        for (String nombre : List.of("titulo", "descripcion", "prioridad", "puntosRecompensa"))
            assertNull(RecomendacionIA.class.getDeclaredField(nombre).getAnnotation(Column.class));
        for (String nombre : List.of("estado", "fechaGeneracion", "fechaCompletado"))
            assertNull(UsuarioRecomendacionIA.class.getDeclaredField(nombre).getAnnotation(Column.class));
        assertEquals(FetchType.EAGER, RecomendacionIA.class.getDeclaredField("usuariosRecomendacionesIA")
                .getAnnotation(OneToMany.class).fetch());
        assertEquals("recomendacionIA", RecomendacionIA.class.getDeclaredField("usuariosRecomendacionesIA")
                .getAnnotation(OneToMany.class).mappedBy());
    }

    @Test
    void relacionesYUnicidadDelRetoSeConservan() throws Exception {
        relacion(UsuarioRecomendacionIA.class, "usuario", "usuario_id", false);
        relacion(UsuarioRecomendacionIA.class, "recomendacionIA", "recomendacion_ia_id", false);
        relacion(RecomendacionIA.class, "categoria", "categoria_id", false);
        relacion(Reto.class, "recomendacionOrigen", "usuario_recomendacion_ia_id", true);
        var asociacion = new UsuarioRecomendacionIA();
        var reto = new Reto();
        reto.setRecomendacionOrigen(asociacion);
        asociacion.setTituloInstantanea("Instantánea sintética");
        assertSame(asociacion, reto.getRecomendacionOrigen());
    }

    private void relacion(Class<?> entidad, String nombre, String columna, boolean unica) throws Exception {
        Field campo = entidad.getDeclaredField(nombre);
        ManyToOne relacion = campo.getAnnotation(ManyToOne.class);
        assertNotNull(relacion);
        assertTrue(relacion.optional());
        assertEquals(0, relacion.cascade().length);
        assertEquals(FetchType.EAGER, relacion.fetch());
        assertEquals(columna, campo.getAnnotation(JoinColumn.class).name());
        assertEquals(unica, campo.getAnnotation(JoinColumn.class).unique());
    }

    @Test
    void consultaPorAccionUsaPropiedadesRealesSinSuponerUnicidad() throws Exception {
        var metodo = RecomendacionIARepository.class.getMethod("findByAccionIdAndVersionAccionOrderByIdAsc", String.class, String.class);
        assertEquals(List.class, metodo.getReturnType());
        PartTree arbol = new PartTree(metodo.getName(), RecomendacionIA.class);
        assertEquals(List.of("accionId", "versionAccion"), arbol.getParts().stream()
                .map(p -> p.getProperty().toDotPath()).toList());
        assertEquals("id: ASC", arbol.getSort().toString());
    }

    @Test
    void consultasAnterioresConservanPropiedadesYFirmas() throws Exception {
        for (var metodo : List.of(
                RecomendacionIARepository.class.getMethod("findFirstByCategoria_IdAndTituloIgnoreCaseOrderByIdAsc", Long.class, String.class),
                UsuarioRecomendacionIARepository.class.getMethod("findFirstByUsuario_IdAndRecomendacionIA_IdOrderByIdAsc", Long.class, Integer.class),
                UsuarioRecomendacionIARepository.class.getMethod("findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc", Long.class, String.class))) {
            Class<?> entidad = metodo.getDeclaringClass() == RecomendacionIARepository.class
                    ? RecomendacionIA.class : UsuarioRecomendacionIA.class;
            assertDoesNotThrow(() -> new PartTree(metodo.getName(), entidad));
        }
    }
}
