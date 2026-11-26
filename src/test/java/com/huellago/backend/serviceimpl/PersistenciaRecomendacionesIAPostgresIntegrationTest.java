package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;

/** PREPARADA, no ejecutada con PostgreSQL en PASO119. Datos y selección SINTÉTICOS.
 * Sin Boot, DataJpaTest, data.sql, Ollama, mocks HTTP ni Mockito.
 * Activación futura exige autorización nueva y base/rol dedicados con esquema revisado. */
@EnabledIfEnvironmentVariable(named="HUELLAGO_IA_PG_REAL_TEST",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class PersistenciaRecomendacionesIAPostgresIntegrationTest {
    private static final String ACCION="TR_PLANIFICAR_DESPLAZAMIENTOS";
    private AnnotationConfigApplicationContext contexto;
    private PostgresIA119Soporte.FuenteProtegida ds;
    private final PostgresIA119Soporte.Control control=new PostgresIA119Soporte.Control();
    private Connection exclusividad;
    private JdbcTemplate jdbc;
    private long transporte,energia,u1,u2,testigo;
    private final List<Long> propios=new ArrayList<>(),categoriasPropias=new ArrayList<>();
    private final Set<Integer> recomendacionesPropias=ConcurrentHashMap.newKeySet();
    private final Set<Integer> asociacionesPropias=ConcurrentHashMap.newKeySet();
    private final Set<Long> habitosPropios=new HashSet<>();
    private String testigoAntes;
    private ExecutorService trabajadores;
    private final JsonMapper mapper=JsonMapper.builder().build();
    record Solicitud(Authentication auth,CapturaContextoAmbientalIADTO captura,
                     OrquestadorRecomendacionesIAService.Resultado seleccion,String receta) { }

    @BeforeAll void prepararInfraestructuraExclusiva() throws Exception {
        if(!PostgresIA119Soporte.BASE.equals(System.getenv("HUELLAGO_IA_PG_CONFIRMAR_BASE")))
            throw new IllegalStateException("Falta confirmación nominal de la base exclusiva IA119");
        ds=new PostgresIA119Soporte.FuenteProtegida(PostgresIA119Soporte.variable("HUELLAGO_IA_PG_URL"),
                PostgresIA119Soporte.variable("HUELLAGO_IA_PG_USUARIO"),PostgresIA119Soporte.variable("HUELLAGO_IA_PG_CLAVE"));
        jdbc=new JdbcTemplate(ds); exclusividad=ds.getConnection();
        try {
            try(var s=exclusividad.createStatement();var r=s.executeQuery("SELECT pg_try_advisory_lock(119117119)")) {
                assertTrue(r.next() && r.getBoolean(1),"Otra ronda IA119 está activa");
            }
            exigirVacia(); verificarIndices();
            contexto=PostgresIA119Soporte.abrir(ds,control);
        } catch(Throwable e) { exclusividad.close(); exclusividad=null; throw e; }
    }
    @AfterAll void cerrar() throws Exception {
        if(contexto!=null) contexto.close();
        if(exclusividad!=null) exclusividad.close(); // Libera advisory lock de sesión.
    }
    private <T>T bean(Class<T> tipo) { return contexto.getBean(tipo); }
    private long cantidad(String tabla) {
        if(!tabla.matches("[a-z_]+")) throw new IllegalArgumentException("Identificador inesperado");
        return jdbc.queryForObject("SELECT count(*) FROM public."+tabla,Long.class);
    }
    private void exigirVacia() {
        for(String tabla:jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public'",String.class))
            assertEquals(0,cantidad(tabla),"Base no vacía: detener sin limpiar registros ajenos ("+tabla+")");
    }
    @BeforeEach void datosSinteticos() {
        exigirVacia(); verificarIndices(); propios.clear(); categoriasPropias.clear(); recomendacionesPropias.clear(); asociacionesPropias.clear(); habitosPropios.clear(); control.pausarHilo=null;
        control.canonicaInsertadaAntesDelFallo.set(false);
        transporte=categoria("Transporte"); energia=categoria("Energía");
        u1=usuario(30); u2=usuario(40); testigo=usuario(77);
        for(long id:propios) {
            habito(id,"medio","0","Auto"); habito(id,"kmSemana","100","km/semana"); habito(id,"diasSemana","5","dias/semana");
        }
        testigoAntes=estadoTestigo();
    }
    private long categoria(String nombre) {
        long id=jdbc.queryForObject("INSERT INTO public.categorias_habito(nombre,descripcion) VALUES (?,?) RETURNING id",Long.class,nombre,"SINTETICO_IA119");
        categoriasPropias.add(id); return id;
    }
    private long usuario(int puntos) {
        long id=jdbc.queryForObject("INSERT INTO public.usuarios(nombres,apellidos,correo,contrasena,eco_puntos,nivel,fecha_creacion) VALUES (?,?,?,?,?,1,CURRENT_TIMESTAMP) RETURNING id",
                Long.class,"SINTETICO_IA119","PRUEBA",UUID.randomUUID()+"@example.invalid","NO_ES_CREDENCIAL_REAL",puntos);
        propios.add(id); return id;
    }
    private void habito(long usuario,String nombre,String valor,String unidad) {
        long id=jdbc.queryForObject("INSERT INTO public.habitos(usuario_id,categoria_id,nombre,valor,unidad,fecha_registro) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP) RETURNING id",
                Long.class,usuario,transporte,nombre,new BigDecimal(valor),unidad);
        habitosPropios.add(id);
    }
    private String estadoTestigo() {
        return jdbc.queryForObject("SELECT json_build_object('puntos',eco_puntos,'nivel',nivel,'nombre',nombres)::text FROM public.usuarios WHERE id=?",String.class,testigo)
                +jdbc.queryForList("SELECT nombre,valor,unidad,fecha_registro,fecha_actualizacion FROM public.habitos WHERE usuario_id=? ORDER BY id",testigo).toString();
    }
    @AfterEach void limpiarSoloFixturesPropios() throws Exception {
        control.liberar.countDown();
        if(trabajadores!=null) {
            trabajadores.shutdown();
            assertTrue(trabajadores.awaitTermination(30,TimeUnit.SECONDS),"Hilos activos: no se limpiará mientras puedan escribir");
            trabajadores=null;
        }
        if(propios.isEmpty()) return;
        try {
            if(testigoAntes!=null) assertEquals(testigoAntes,estadoTestigo(),"La operación alteró el usuario testigo");
        } finally {
            // IDs devueltos por INSERT de esta prueba; nunca TRUNCATE ni DELETE general.
            String ids=propios.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            String cats=categoriasPropias.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            String asocs=asociacionesPropias.isEmpty()?"NULL":asociacionesPropias.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            new TransactionTemplate(bean(PlatformTransactionManager.class)).executeWithoutResult(s->{
                jdbc.update("DELETE FROM public.usuarios_retos WHERE usuario_id IN ("+ids+") AND reto_id IN (SELECT id FROM public.retos WHERE usuario_recomendacion_ia_id IN ("+asocs+"))");
                jdbc.update("DELETE FROM public.retos WHERE usuario_recomendacion_ia_id IN ("+asocs+")");
                jdbc.update("DELETE FROM public.usuarios_recomendaciones_ia WHERE usuario_id IN ("+ids+") AND id IN ("+asocs+")");
                if(!recomendacionesPropias.isEmpty()) {
                    String recs=recomendacionesPropias.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                    jdbc.update("DELETE FROM public.recomendaciones_ia WHERE id IN ("+recs+")");
                }
                if(!habitosPropios.isEmpty()) {
                    String hs=habitosPropios.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                    jdbc.update("DELETE FROM public.habitos WHERE usuario_id IN ("+ids+") AND id IN ("+hs+")");
                }
                jdbc.update("DELETE FROM public.usuarios WHERE id IN ("+ids+")");
                jdbc.update("DELETE FROM public.categorias_habito WHERE id IN ("+cats+")");
            });
            propios.clear(); categoriasPropias.clear(); testigoAntes=null;
            exigirVacia();
        }
    }
    private Authentication auth(long id) {
        var u=new Usuario(); u.setId(id); return new UsernamePasswordAuthenticationToken(new UserSecurity(u),null,List.of());
    }
    private Solicitud solicitud(long id) {
        var a=auth(id); var captura=bean(CapturaContextoAmbientalIAService.class).capturar(a);
        var elegibles=bean(PoliticaAutorizacionEditorialIAService.class).evaluar(captura.contexto().contexto(),captura.realizadas()).elegibles();
        assertEquals(List.of(ACCION),elegibles.stream().map(c->c.definicion().id()).toList());
        var v=new ValidadorRecomendacionFundamentadaIA();
        var alcance=v.construirAlcanceExperimental(captura.contexto().contexto(),elegibles,captura.realizadas());
        var t=captura.contexto().contexto().transporte();
        var p=new Propuesta("Transporte",Decision.PROPONER,new Accion(ACCION,"1",Map.of()),List.of(
                new Evidencia("/transporte/medio",new Texto(t.medio()),Ambito.NO_APLICA),
                new Evidencia("/transporte/kmSemana",new Numero(t.kmSemana()),Ambito.NO_APLICA),
                new Evidencia("/transporte/diasSemana",new Numero(BigDecimal.valueOf(t.diasSemana())),Ambito.NO_APLICA)),null,null,null,List.of(),null);
        var dto=new RecomendacionFundamentadaIADTO("2",List.of(p)); var json=mapper.writeValueAsString(dto);
        var seleccion=new ResultadoGeneracionFundamentadaIADTO(json,dto,v.validarConAlcanceExperimental(json,captura.contexto().contexto(),alcance,captura.realizadas()));
        var presentacion=new PresentacionRecomendacionesIAServiceImpl().presentarConAlcanceParaPruebasSinteticas(captura.contexto().contexto(),seleccion,alcance,captura.realizadas());
        assertEquals(ResultadoPresentacionRecomendacionesIADTO.Estado.SELECCION_ACEPTADA,presentacion.estado());
        return new Solicitud(a,captura,new OrquestadorRecomendacionesIAService.Resultado(seleccion,presentacion,alcance,Set.of("Transporte")),
                bean(PersistenciaRecomendacionesIAService.class).referenciaEditorial(a,captura));
    }
    private ResultadoPersistenciaRecomendacionesIADTO guardar(Solicitud s) {
        var r=bean(PersistenciaRecomendacionesIAService.class).persistir(s.auth(),s.captura(),s.seleccion(),s.receta(),PersistenciaRecomendacionesIAService.Destino.PRUEBA_INTERNA);
        r.resultados().forEach(g->{recomendacionesPropias.add(g.recomendacionId()); asociacionesPropias.add(g.asociacionId());});
        return r;
    }
    private void cambiarKm(long id) {
        long h=jdbc.queryForObject("SELECT id FROM public.habitos WHERE usuario_id=? AND nombre='kmSemana'",Long.class,id);
        var u=new Usuario(); u.setId(id);
        bean(HabitoService.class).actualizar(h,u,new ActualizarHabitoDTO("kmSemana",new BigDecimal("200"),"km/semana"));
    }
    @Test void A_insertaInstantaneaCompletaYMetadatos() {
        var s=solicitud(u1); var r=guardar(s); var p=s.seleccion().presentacion().resultados().getFirst().presentacion();
        var fila=jdbc.queryForMap("SELECT * FROM public.usuarios_recomendaciones_ia WHERE id=?",r.resultados().getFirst().asociacionId());
        assertEquals(p.titulo(),fila.get("titulo_instantanea")); assertEquals(p.descripcion(),fila.get("descripcion_instantanea")); assertEquals(p.motivo(),fila.get("motivo_instantanea"));
        assertNull(fila.get("enfoque")); assertEquals("1",fila.get("version_presentacion")); assertEquals("qwen3:4b-instruct",fila.get("modelo_generacion"));
        assertEquals(74,((String)fila.get("referencia_contexto")).length()); assertFalse(r.publicable());
    }
    @Test void B_reintentoDevuelveMismaAsociacionYFecha() {
        var s=solicitud(u1); var a=guardar(s).resultados().getFirst();
        // guardar() ya ha confirmado: esta lectura conserva la precision realmente persistida.
        var antes=asociacionPersistida(a.asociacionId());
        var b=guardar(s).resultados().getFirst();
        var despues=asociacionPersistida(b.asociacionId());
        assertEquals(a.asociacionId(),b.asociacionId());
        assertEquals(a.recomendacionId(),b.recomendacionId());
        assertEquals(u1,((Number)antes.get("usuario_id")).longValue());
        assertEquals(ACCION,antes.get("accion_id")); assertEquals("1",antes.get("version_accion"));
        assertNotNull(antes.get("referencia_contexto"));
        comprobarReintentoPersistido(antes,despues,b.fechaGeneracion());
        assertTrue(b.reutilizada()); assertEquals(1,cantidad("usuarios_recomendaciones_ia"));
    }
    private Map<String,Object> asociacionPersistida(Integer id) {
        return jdbc.queryForMap("SELECT a.*, r.accion_id, r.version_accion FROM public.usuarios_recomendaciones_ia a JOIN public.recomendaciones_ia r ON r.id=a.recomendacion_ia_id WHERE a.id=? AND a.usuario_id=?",id,u1);
    }
    static void comprobarReintentoPersistido(Map<String,Object> antes,Map<String,Object> despues,java.time.LocalDateTime fechaDevuelta) {
        assertEquals(antes,despues,"El reintento debe conservar todos los campos persistidos");
        assertNotNull(antes.get("fecha_generacion"));
        assertEquals(((Timestamp)antes.get("fecha_generacion")).toLocalDateTime(),fechaDevuelta);
    }
    private <T>Future<T> enviar(String nombre,Callable<T> operacion) {
        return trabajadores.submit(()->{Thread.currentThread().setName(nombre); return operacion.call();});
    }
    private void esperarBloqueo(String espera,String titular,Future<?> futuro) throws Exception {
        long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
        while(System.nanoTime()<limite) {
            Integer pid=ds.pids.get(espera),dueno=ds.pids.get(titular);
            if(pid!=null && dueno!=null && Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))",Boolean.class,dueno,pid))) return;
            if(futuro.isDone()) fail("La segunda operación terminó sin observar el bloqueo PostgreSQL esperado");
            Thread.sleep(25); // Sondeo de condición del servidor, no sincronización basada solo en tiempo.
        }
        fail("No se observó bloqueo PostgreSQL dentro del plazo");
    }
    @Test void C_dosSolicitudesConcurrentesConvergenBajoBloqueoReal() throws Exception {
        var s=solicitud(u1); trabajadores=Executors.newFixedThreadPool(2); ds.pids.clear(); control.pausar("ia119-a");
        var a=enviar("ia119-a",()->guardar(s)); assertTrue(control.dentro.await(8,TimeUnit.SECONDS));
        var b=enviar("ia119-b",()->guardar(s));
        try { esperarBloqueo("ia119-b","ia119-a",b); } finally { control.liberar.countDown(); }
        var ra=a.get(25,TimeUnit.SECONDS); var rb=b.get(25,TimeUnit.SECONDS);
        assertEquals(ra.resultados().getFirst().asociacionId(),rb.resultados().getFirst().asociacionId()); assertTrue(rb.resultados().getFirst().reutilizada());
        assertEquals(1,cantidad("recomendaciones_ia")); assertEquals(1,cantidad("usuarios_recomendaciones_ia"));
    }
    @Test void D_dosUsuariosCompartenCanonicaSinCompartirInstantanea() throws Exception {
        var s1=solicitud(u1); cambiarKm(u2); var s2=solicitud(u2);
        trabajadores=Executors.newFixedThreadPool(2); var inicio=new CountDownLatch(1);
        var a=enviar("ia119-u1",()->{inicio.await();return guardar(s1);}); var b=enviar("ia119-u2",()->{inicio.await();return guardar(s2);}); inicio.countDown();
        var r1=a.get(25,TimeUnit.SECONDS).resultados().getFirst(); var r2=b.get(25,TimeUnit.SECONDS).resultados().getFirst();
        assertEquals(r1.recomendacionId(),r2.recomendacionId()); assertNotEquals(r1.asociacionId(),r2.asociacionId()); assertNotEquals(r1.motivo(),r2.motivo());
        assertEquals(1,cantidad("recomendaciones_ia")); assertEquals(2,cantidad("usuarios_recomendaciones_ia"));
    }
    private String normalizarPredicado(String p) { return p.replace("::text","").replaceAll("[\\s()]","").toLowerCase(Locale.ROOT); }
    private void verificarIndices() {
        for(String nombre:List.of("uq_recomendaciones_ia_accion_version","uq_usuarios_recomendaciones_ia_generacion")) {
            var r=jdbc.queryForMap("SELECT i.indisunique,i.indisvalid,i.indisready,t.relname AS tabla,pg_get_expr(i.indpred,i.indrelid) AS predicado FROM pg_index i JOIN pg_class x ON x.oid=i.indexrelid JOIN pg_class t ON t.oid=i.indrelid JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname='public' AND x.relname=?",nombre);
            assertEquals(true,r.get("indisunique")); assertEquals(true,r.get("indisvalid")); assertEquals(true,r.get("indisready"));
            var columnas=jdbc.queryForList("SELECT a.attname FROM pg_index i JOIN pg_class x ON x.oid=i.indexrelid JOIN pg_namespace n ON n.oid=x.relnamespace CROSS JOIN LATERAL unnest(i.indkey) WITH ORDINALITY AS k(attnum,orden) JOIN pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=k.attnum WHERE n.nspname='public' AND x.relname=? AND k.orden<=i.indnkeyatts ORDER BY k.orden",String.class,nombre);
            if(nombre.equals("uq_recomendaciones_ia_accion_version")) {
                assertEquals("recomendaciones_ia",r.get("tabla")); assertEquals(List.of("accion_id","version_accion"),columnas);
                assertEquals("accion_idisnotnullandversion_accionisnotnull",normalizarPredicado((String)r.get("predicado")));
            } else {
                assertEquals("usuarios_recomendaciones_ia",r.get("tabla")); assertEquals(List.of("usuario_id","referencia_contexto","metodo_generacion"),columnas);
                assertEquals("metodo_generacion='ollama_seleccion_presentacion_controlada'andreferencia_contextoisnotnull",normalizarPredicado((String)r.get("predicado")));
            }
        }
    }
    @Test void E_indicesFisicosCoincidenEnTablaColumnasYPredicados() { verificarIndices(); }
    private void violacionUnica(Runnable operacion,String indice) {
        var e=assertThrows(DataIntegrityViolationException.class,operacion::run);
        assertInstanceOf(SQLException.class,e.getMostSpecificCause()); var sql=(SQLException)e.getMostSpecificCause();
        assertEquals("23505",sql.getSQLState()); assertTrue(sql.getMessage().contains(indice));
    }
    @Test void F_indiceRechazaIdentidadCanonicaDuplicada() {
        guardar(solicitud(u1));
        violacionUnica(()->jdbc.update("INSERT INTO public.recomendaciones_ia(titulo,categoria_id,accion_id,version_accion) SELECT titulo,categoria_id,accion_id,version_accion FROM public.recomendaciones_ia WHERE accion_id=?",ACCION),"uq_recomendaciones_ia_accion_version");
        assertEquals(1,cantidad("recomendaciones_ia"));
    }
    @Test void G_indiceRechazaGeneracionDuplicada() {
        guardar(solicitud(u1));
        violacionUnica(()->jdbc.update("INSERT INTO public.usuarios_recomendaciones_ia(usuario_id,recomendacion_ia_id,referencia_contexto,metodo_generacion) SELECT usuario_id,recomendacion_ia_id,referencia_contexto,metodo_generacion FROM public.usuarios_recomendaciones_ia WHERE usuario_id=?",u1),"uq_usuarios_recomendaciones_ia_generacion");
        assertEquals(1,cantidad("usuarios_recomendaciones_ia"));
    }
    @Test void H_errorSQLTrasInsertCanonicoRevierteFisicamenteAmbasTablas() {
        var s=solicitud(u1); control.fallarAsociacion.set(true);
        try { assertThrows(RuntimeException.class,()->guardar(s)); } finally { control.fallarAsociacion.remove(); }
        assertTrue(control.canonicaInsertadaAntesDelFallo.get()); assertEquals(0,cantidad("recomendaciones_ia")); assertEquals(0,cantidad("usuarios_recomendaciones_ia"));
    }
    @Test void I_cambioDeHabitosAntesDeConfirmarDescartaSeleccion() {
        var s=solicitud(u1); cambiarKm(u1);
        assertEquals("CONTEXTO_MODIFICADO",assertThrows(ResponseStatusException.class,()->guardar(s)).getReason()); assertEquals(0,cantidad("recomendaciones_ia"));
    }
    private int legado(long categoria) {
        int r=jdbc.queryForObject("INSERT INTO public.recomendaciones_ia(titulo,descripcion,categoria_id,puntos_recompensa) VALUES (?,?,?,0) RETURNING id",Integer.class,"  Título legado  ","Descripción original\nExacta.",categoria);
        recomendacionesPropias.add(r);
        int id=jdbc.queryForObject("INSERT INTO public.usuarios_recomendaciones_ia(usuario_id,recomendacion_ia_id,estado,fecha_generacion) VALUES (?,?,'Pendiente',CURRENT_TIMESTAMP) RETURNING id",Integer.class,u1,r);
        asociacionesPropias.add(id); return id;
    }
    @Test void J_completadoConcurrenteBloqueaYRevalidaRealizadas() throws Exception {
        int asociacion=legado(energia); var s=solicitud(u1); trabajadores=Executors.newFixedThreadPool(2); ds.pids.clear();
        var dentro=new CountDownLatch(1); var liberar=new CountDownLatch(1);
        var completa=enviar("ia119-completa",()->{
            new TransactionTemplate(bean(PlatformTransactionManager.class)).executeWithoutResult(tx->{
                var u=new Usuario(); u.setId(u1); bean(RecomendacionService.class).completar(asociacion,u); dentro.countDown();
                try { if(!liberar.await(15,TimeUnit.SECONDS)) throw new IllegalStateException("Barrera vencida"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }); return true;
        });
        try {
            assertTrue(dentro.await(8,TimeUnit.SECONDS)); var guarda=enviar("ia119-guarda",()->guardar(s));
            try { esperarBloqueo("ia119-guarda","ia119-completa",guarda); } finally { liberar.countDown(); }
            assertTrue(completa.get(25,TimeUnit.SECONDS)); var e=assertThrows(ExecutionException.class,()->guarda.get(25,TimeUnit.SECONDS));
            assertInstanceOf(ResponseStatusException.class,e.getCause()); assertEquals("CONTEXTO_MODIFICADO",((ResponseStatusException)e.getCause()).getReason());
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM public.recomendaciones_ia WHERE accion_id=?",Integer.class,ACCION));
        } finally { liberar.countDown(); }
    }
    @Test void K_pendienteDeOtraCapturaNoSeSobrescribe() {
        var original=guardar(solicitud(u1)).resultados().getFirst(); cambiarKm(u1);
        assertEquals("CATEGORIA_PENDIENTE_OTRO_CONTEXTO",assertThrows(ResponseStatusException.class,()->guardar(solicitud(u1))).getReason());
        assertEquals(original.descripcion(),jdbc.queryForObject("SELECT descripcion_instantanea FROM public.usuarios_recomendaciones_ia WHERE id=?",String.class,original.asociacionId()));
        assertEquals(1,cantidad("usuarios_recomendaciones_ia"));
    }
    @Test void L_legadoConservaHistorialYConversionExacta() {
        int id=legado(transporte); var u=new Usuario(); u.setId(u1); var servicio=bean(RecomendacionService.class);
        servicio.completar(id,u); var h=servicio.historialCompletadas(u).getFirst();
        assertEquals("  Título legado  ",h.titulo()); assertEquals("Descripción original\nExacta.",h.descripcion());
        var conversion=servicio.convertirEnReto(id,u);
        var reto=jdbc.queryForMap("SELECT titulo,descripcion,puntos_recompensa FROM public.retos WHERE id=?",conversion.retoId());
        assertEquals(h.titulo(),reto.get("titulo")); assertEquals(h.descripcion(),reto.get("descripcion")); assertEquals(0,reto.get("puntos_recompensa"));
    }
    @Test void M_textosPersonalizadosCoincidenExactamenteTrasReleer() {
        var s=solicitud(u1); var p=s.seleccion().presentacion().resultados().getFirst().presentacion(); var r=guardar(s).resultados().getFirst();
        var a=bean(UsuarioRecomendacionIARepository.class).findByIdAndUsuario_Id(r.asociacionId(),u1).orElseThrow();
        assertEquals(p.titulo(),a.getTituloInstantanea()); assertEquals(p.descripcion(),a.getDescripcionInstantanea()); assertEquals(p.motivo(),a.getMotivoInstantanea());
        assertNotEquals(p.titulo(),a.getRecomendacionIA().getTitulo());
    }
    @Test void N_generarNoConcedePuntosNiCreaRetos() {
        guardar(solicitud(u1)); assertEquals(30,jdbc.queryForObject("SELECT eco_puntos FROM public.usuarios WHERE id=?",Integer.class,u1));
        assertEquals(0,cantidad("retos")); assertEquals(0,cantidad("usuarios_retos"));
    }
    @Test void O_usuarioTestigoNoCambia() {
        guardar(solicitud(u1)); assertEquals(testigoAntes,estadoTestigo());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM public.usuarios_recomendaciones_ia WHERE usuario_id=?",Integer.class,testigo));
    }
}

/** Comprobaciones puras de la asercion del escenario B: no abren conexiones. */
class PersistenciaRecomendacionesIAPrecisionTemporalTest {
    private Map<String,Object> fila() {
        return new HashMap<>(Map.of("id",1,"usuario_id",2L,"recomendacion_ia_id",3,
                "referencia_contexto","clave-idempotente","titulo_instantanea","Titulo",
                "descripcion_instantanea","Descripcion","motivo_instantanea","Motivo",
                "fecha_generacion",Timestamp.valueOf("2026-11-09 17:27:02.675286")));
    }
    @Test void comparaFechasPersistidasSinExigirLosNanosegundosDelObjetoInicial() {
        var inicial=java.time.LocalDateTime.parse("2026-11-09T17:27:02.675285600");
        var persistida=java.time.LocalDateTime.parse("2026-11-09T17:27:02.675286");
        assertEquals(400,java.time.Duration.between(inicial,persistida).toNanos());
        var antes=fila(); var despues=fila();
        PersistenciaRecomendacionesIAPostgresIntegrationTest.comprobarReintentoPersistido(antes,despues,persistida);
        assertEquals(fila(),antes); assertEquals(fila(),despues);
    }
    @Test void rechazaCambioPersistidoDeUnMicrosegundo() {
        var despues=fila(); despues.put("fecha_generacion",Timestamp.valueOf("2026-11-09 17:27:02.675287"));
        assertThrows(AssertionError.class,()->PersistenciaRecomendacionesIAPostgresIntegrationTest.comprobarReintentoPersistido(fila(),despues,java.time.LocalDateTime.parse("2026-11-09T17:27:02.675287")));
    }
    @Test void rechazaFechaDevueltaDistintaDeLaPersistida() {
        assertThrows(AssertionError.class,()->PersistenciaRecomendacionesIAPostgresIntegrationTest.comprobarReintentoPersistido(fila(),fila(),java.time.LocalDateTime.parse("2026-11-09T17:27:02.675285600")));
    }
    @Test void rechazaCambiosDeIdentidadPropietarioClaveYTextos() {
        for(String campo:List.of("id","usuario_id","recomendacion_ia_id","referencia_contexto","titulo_instantanea","descripcion_instantanea","motivo_instantanea")) {
            var despues=fila(); despues.put(campo,"alterado");
            assertThrows(AssertionError.class,()->PersistenciaRecomendacionesIAPostgresIntegrationTest.comprobarReintentoPersistido(fila(),despues,java.time.LocalDateTime.parse("2026-11-09T17:27:02.675286")),campo);
        }
    }
}
