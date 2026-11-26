package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.*;
import com.huellago.backend.exceptions.OllamaClientException;
import com.huellago.backend.repositories.HabitoRepository;
import com.huellago.backend.services.*;
import com.huellago.backend.services.AccionAmbientalAdmisible.Referencia;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.Decision.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.*;
import static com.huellago.backend.dtos.ResultadoPresentacionRecomendacionesIADTO.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** PASO 100: selección HTTP REAL en A/B y ausencia local de candidatas en C.
 * Solo el repositorio de hábitos es Mockito. No usa redacción libre, Spring ni bases de datos.
 * Los textos de presentación son composición local BORRADOR, no textos redactados por Qwen3. */
@EnabledIfEnvironmentVariable(named="HUELLAGO_OLLAMA_REAL_TEST",matches="true")
@Execution(ExecutionMode.SAME_THREAD)
class OllamaPresentacionControladaRealIntegrationTest {
    private final JsonMapper mapper=JsonMapper.builder().build();
    private final CatalogoAccionesAmbientalesServiceImpl catalogo=new CatalogoAccionesAmbientalesServiceImpl();
    private static final Set<String> FAMILIAS=Set.of("TR_PLANIFICAR_DESPLAZAMIENTOS","TR_REVISAR_MOVILIDAD_ACTIVA",
            "EN_REVISAR_REGISTRO_HOGAR","AL_PLANIFICAR_APROVECHAMIENTO","RE_INICIAR_SEPARACION","RE_REVISAR_ENTREGA");
    private record Perfil(ContextoRecomendacionesIAService.Resultado contexto,HabitoRepository repo,List<Habito> habitos) { }

    @ParameterizedTest @ValueSource(strings={"A","B","C"})
    void seleccionRealYPresentacionLocal(String escenario) throws Exception {
        Path carpeta=Path.of("target/paso100"); Files.createDirectories(carpeta);
        Path informe=carpeta.resolve(escenario+"-resultado.json");
        assertFalse(Files.exists(informe),"No repetir ni sobrescribir una ronda real");
        var perfil=construir(escenario); var contexto=perfil.contexto().contexto();
        var iniciales=catalogo.compatibles(contexto,Set.of());
        Set<Referencia> realizadas=escenario.equals("C")?iniciales.stream().map(a->new Referencia(a.definicion().id(),a.definicion().version())).collect(Collectors.toUnmodifiableSet()):Set.of();
        var candidatas=catalogo.compatibles(contexto,realizadas);
        String contextoAntes=mapper.writeValueAsString(perfil.contexto()),habitosAntes=instantanea(perfil.habitos());
        guardar(carpeta,escenario+"-contexto.json",perfil.contexto());
        guardar(carpeta,escenario+"-candidatas-iniciales.json",iniciales);
        guardar(carpeta,escenario+"-candidatas-admisibles.json",candidatas);
        Map<String,Object> registro=new LinkedHashMap<>();
        registro.put("escenario",escenario); registro.put("tipoEjecucion",escenario.equals("C")?"LOCAL_SIN_INFERENCIA":"SELECCION_HTTP_REAL_PRESENTACION_LOCAL");
        registro.put("inicio",Instant.now().toString()); registro.put("accionesRealizadas",realizadas);
        registro.put("cantidadCandidatas",candidatas.size()); registro.put("llamadasHttp",0);
        registro.put("revisionSemantica","PENDIENTE_EVALUACION_HUMANA_SEPARADA");
        var ajustes=new OllamaConfiguration.Ajustes(URI.create("http://localhost:11434"),"qwen3:4b-instruct",Duration.ofSeconds(3),Duration.ofSeconds(120),65536);
        var http=new OllamaConfiguration().ollamaRestClient(ajustes).mutate().requestInterceptor((req,body,execution)->{
            assertFalse(escenario.equals("C"),"C no debe invocar Ollama");
            assertEquals(0,registro.get("llamadasHttp"),"Una única llamada de selección, sin redacción ni reintentos");
            registro.put("bytesSolicitud",body.length);
            Files.write(carpeta.resolve(escenario+"-solicitud.json"),body,StandardOpenOption.CREATE_NEW);
            assertTrue(body.length<=16384); assertEquals(HttpMethod.POST,req.getMethod());
            assertEquals("http://localhost:11434/api/chat",req.getURI().toString()); assertNull(req.getHeaders().getFirst("Authorization"));
            var json=mapper.readTree(body); assertEquals("qwen3:4b-instruct",json.get("model").asString()); assertFalse(json.get("stream").asBoolean());
            var datos=mapper.readTree(json.get("messages").get(1).get("content").asString());
            assertEquals(mapper.readTree(mapper.writeValueAsString(ValidadorRecomendacionesIA.contexto(contexto))),datos.get("contexto"));
            Set<Referencia> refs=new HashSet<>();
            for(var a:datos.get("accionesCandidatas")) {
                refs.add(new Referencia(a.get("id").asString(),a.get("version").asString()));
                assertEquals("BORRADOR",a.get("estadoRevision").asString());
            }
            assertEquals(candidatas.stream().map(a->new Referencia(a.definicion().id(),a.definicion().version())).collect(Collectors.toSet()),refs);
            assertTrue(Collections.disjoint(refs,realizadas));
            registro.put("llamadasHttp",1); long t=System.nanoTime(); ClientHttpResponse respuesta;
            try { respuesta=execution.execute(req,body); }
            catch(IOException|RuntimeException e) { registro.put("duracionHttpMs",ms(t)); throw e; }
            try {
                registro.put("httpStatus",respuesta.getStatusCode().value());
                byte[] bytes=respuesta.getBody().readNBytes(ajustes.maxRespuestaBytes()+1);
                registro.put("duracionHttpMs",ms(t)); registro.put("bytesRespuesta",bytes.length);
                registro.put("capturaCompletaDentroDelLimite",bytes.length<=ajustes.maxRespuestaBytes());
                Files.write(carpeta.resolve(escenario+"-respuesta-http.json"),bytes,StandardOpenOption.CREATE_NEW);
                InputStream copia=new ByteArrayInputStream(bytes);
                return new ClientHttpResponse() {
                    public HttpStatusCode getStatusCode() throws IOException { return respuesta.getStatusCode(); }
                    public String getStatusText() throws IOException { return respuesta.getStatusText(); }
                    public HttpHeaders getHeaders() { return respuesta.getHeaders(); }
                    public InputStream getBody() { return copia; }
                    public void close() { respuesta.close(); }
                };
            } catch(IOException|RuntimeException|Error e) { respuesta.close(); throw e; }
        }).build();
        long inicio=System.nanoTime();
        try {
            GeneradorRecomendacionesIAService selector=new OllamaRecomendacionesServiceImpl(http,ajustes,catalogo);
            long t=System.nanoTime(); ResultadoGeneracionFundamentadaIADTO seleccion;
            try { seleccion=selector.generarV2ParaPruebasSinteticas(perfil.contexto(),realizadas); }
            finally { registro.put("duracionSeleccionMs",ms(t)); }
            guardar(carpeta,escenario+"-seleccion.json",seleccion);
            guardar(carpeta,escenario+"-validacion-v2.json",seleccion.evaluacion());
            if(seleccion.propuestaJson()!=null) Files.writeString(carpeta.resolve(escenario+"-json-original-qwen3.json"),seleccion.propuestaJson(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            String seleccionAntes=mapper.writeValueAsString(seleccion);
            PresentacionRecomendacionesIAService presentador=new PresentacionRecomendacionesIAServiceImpl();
            t=System.nanoTime(); var presentacion=presentador.presentarParaPruebasSinteticas(contexto,seleccion,candidatas,realizadas);
            registro.put("duracionPresentacionLocalMs",ms(t));
            guardar(carpeta,escenario+"-presentacion.json",presentacion);
            registro.put("presentacion",presentacion); registro.put("publicable",presentacion.publicable());
            assertFalse(presentacion.publicable()); assertFalse(seleccion.publicable());
            assertEquals(seleccionAntes,mapper.writeValueAsString(seleccion));
            var categorias=ValidadorRecomendacionesIA.contexto(contexto).keySet();
            registro.put("cobertura",cobertura(categorias.size(),seleccion,presentacion));
            if(escenario.equals("C")) {
                assertTrue(candidatas.isEmpty()); assertFalse(iniciales.isEmpty());
                assertEquals(iniciales.size(),realizadas.size()); assertEquals(0,registro.get("llamadasHttp"));
                assertNull(seleccion.propuesta()); assertNull(seleccion.propuestaJson());
                assertTrue(seleccion.evaluacion().evaluaciones().isEmpty());
                assertEquals(Estado.SIN_PROPUESTA,presentacion.estado()); assertTrue(presentacion.resultados().isEmpty());
                registro.put("origenResultado","SIN_PROPUESTA_SIN_DECISION_DE_QWEN3");
            } else {
                assertEquals(1,registro.get("llamadasHttp")); assertEquals(200,registro.get("httpStatus"));
                if(seleccion.evaluacion().estado()!=ACEPTADA_ESTRUCTURALMENTE) {
                    assertEquals(Estado.SELECCION_RECHAZADA,presentacion.estado()); assertTrue(presentacion.resultados().isEmpty());
                    fail("Selección real rechazada: "+seleccion.evaluacion());
                }
                var comprobada=new ValidadorRecomendacionFundamentadaIA().validar(seleccion.propuestaJson(),contexto,
                        candidatas.stream().map(a->a.definicion()).toList(),realizadas);
                assertEquals(seleccion.evaluacion(),comprobada);
                assertEquals(Estado.SELECCION_ACEPTADA,presentacion.estado()); assertEquals(comprobada,presentacion.evaluacionVerificada());
                assertEquals(categorias.size(),seleccion.propuesta().resultados().size());
                assertEquals(categorias,seleccion.propuesta().resultados().stream().map(p->p.categoria()).collect(Collectors.toSet()));
                assertEquals(seleccion.propuesta().resultados().size(),presentacion.resultados().size());
                for(int i=0;i<presentacion.resultados().size();i++) {
                    var d=presentacion.resultados().get(i); var p=seleccion.propuesta().resultados().get(i);
                    assertSame(p,d.seleccionOriginal());
                    if(p.decision()==ABSTENERSE) { assertEquals(EstadoDetalle.ABSTENCION_V2,d.estado()); assertNull(d.presentacion()); continue; }
                    assertTrue(candidatas.stream().anyMatch(a->a.definicion().id().equals(p.accion().id())&&a.definicion().version().equals(p.accion().version())));
                    assertNull(p.titulo()); assertNull(p.descripcion()); assertNull(p.motivo());
                    if(!FAMILIAS.contains(p.accion().id())) {
                        assertEquals(EstadoDetalle.SIN_PRESENTACION,d.estado()); assertEquals(Motivo.COMBINACION_NO_DEFINIDA,d.motivo()); assertNull(d.presentacion()); continue;
                    }
                    assertEquals(EstadoDetalle.VISTA_PREVIA_BORRADOR,d.estado()); var v=d.presentacion();
                    assertEquals(p.accion().id(),v.accionId()); assertEquals(p.accion().version(),v.versionAccion());
                    var enfoque=p.accion().parametros().get("enfoque");
                    assertEquals(enfoque==null?"SIN_ENFOQUE":((RecomendacionFundamentadaIADTO.Texto)enfoque).valor(),v.enfoque());
                    assertEquals(enfoque!=null,v.enfoqueSeleccionado()); assertEquals(Revision.BORRADOR,v.estado());
                    assertEquals(PENDIENTE_REVISION,v.revisionHumana()); assertFalse(v.publicable());
                    assertFalse(v.titulo().isBlank()); assertFalse(v.descripcion().isBlank()); assertFalse(v.motivo().isBlank());
                    if(escenario.equals("B")) {
                        assertFalse((v.titulo()+v.descripcion()+v.motivo()).contains("Auto"));
                        assertNotEquals("RE_INICIAR_SEPARACION",v.accionId());
                    }
                }
            }
            registro.put("verificacionAutomatica","APROBADA_NO_CERTIFICA_CALIDAD_AMBIENTAL");
        } catch(RuntimeException|AssertionError e) {
            registro.put("error",e.getClass().getSimpleName()+": "+e.getMessage());
            if(e instanceof OllamaClientException o) { registro.put("tipoErrorCliente",o.getTipo()); registro.put("estadoHttpError",o.getEstadoHttp()); }
            throw e;
        } finally {
            registro.put("duracionTotalMs",ms(inicio));
            boolean sinCambios=contextoAntes.equals(mapper.writeValueAsString(perfil.contexto()))&&habitosAntes.equals(instantanea(perfil.habitos()));
            registro.put("habitosYContextoSinCambios",sinCambios);
            registro.put("catalogoSoloBorradores",catalogo.listarBorradores().stream().allMatch(a->a.estadoRevision().name().equals("BORRADOR")));
            guardar(carpeta,escenario+"-resultado.json",registro);
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(registro));
            verify(perfil.repo()).findByUsuario_Id(10000L); verifyNoMoreInteractions(perfil.repo()); assertTrue(sinCambios);
        }
    }

    private Map<String,Object> cobertura(int categorias,ResultadoGeneracionFundamentadaIADTO s,ResultadoPresentacionRecomendacionesIADTO r) {
        var ps=s.propuesta()==null?List.<RecomendacionFundamentadaIADTO.Propuesta>of():s.propuesta().resultados();
        var propuestas=ps.stream().filter(p->p.decision()==PROPONER).toList();
        Set<String> aceptadas=s.evaluacion().evaluaciones().stream().filter(d->d.estado()==ACEPTADA_ESTRUCTURALMENTE).map(d->d.categoria()).collect(Collectors.toSet());
        Set<String> rechazadas=s.evaluacion().evaluaciones().stream().filter(d->d.estado()==RECHAZADA).map(d->d.categoria()).collect(Collectors.toSet());
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("categoriasCompletas",categorias); m.put("decisionesProponer",propuestas.size());
        m.put("decisionesAbstenerse",ps.stream().filter(p->p.decision()==ABSTENERSE).count());
        m.put("propuestasRechazadas",propuestas.stream().filter(p->rechazadas.contains(p.categoria())).count());
        m.put("propuestasAceptadasIndividualmente",propuestas.stream().filter(p->aceptadas.contains(p.categoria())).count());
        m.put("seleccionGlobalAceptada",s.evaluacion().estado()==ACEPTADA_ESTRUCTURALMENTE);
        m.put("propuestasConPresentacion",r.resultados().stream().filter(d->d.estado()==EstadoDetalle.VISTA_PREVIA_BORRADOR).count());
        m.put("propuestasSinPresentacionDefinida",r.resultados().stream().filter(d->d.estado()==EstadoDetalle.SIN_PRESENTACION).count());
        m.put("propuestasConEnfoque",propuestas.stream().filter(p->p.accion().parametros().containsKey("enfoque")).count());
        m.put("propuestasConParametrosVacios",propuestas.stream().filter(p->p.accion().parametros().isEmpty()).count());
        return m;
    }
    private void guardar(Path carpeta,String nombre,Object o) throws IOException {
        Files.writeString(carpeta.resolve(nombre),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(o),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    private static long ms(long t) { return Duration.ofNanos(System.nanoTime()-t).toMillis(); }
    private String instantanea(List<Habito> lista) {
        return mapper.writeValueAsString(lista.stream().map(h->List.of(h.getId(),h.getUsuario().getId(),h.getCategoria().getNombre(),h.getNombre(),h.getValor(),h.getUnidad(),h.getFechaRegistro().toString(),h.getFechaActualizacion().toString())).toList());
    }
    private Perfil construir(String escenario) {
        boolean a=escenario.equals("A"); List<Habito> lista=new ArrayList<>();
        agregar(lista,"Transporte","medio","0",a?"Auto":"Bicicleta");
        agregar(lista,"Transporte","kmSemana",a?"100":"20","km/semana"); agregar(lista,"Transporte","diasSemana",a?"5":"4","dias/semana");
        if(a) {
            agregar(lista,"Energía","vivienda","0","Casa"); agregar(lista,"Energía","personas","3","personas");
            agregar(lista,"Energía","fuente","0","Electricidad"); agregar(lista,"Energía","consumoKwhDiario","12.345","kWh/dia");
            agregar(lista,"Alimentación","tipo","0","Vegetariana");
        }
        agregar(lista,"Residuos","plasticos","0",a?"Medio":"Bajo"); agregar(lista,"Residuos","reciclas",a?"0":"1","boolean");
        var repo=mock(HabitoRepository.class); when(repo.findByUsuario_Id(10000L)).thenReturn(lista);
        var resultado=new ContextoRecomendacionesIAServiceImpl(repo).construir(10000L);
        assertEquals(a?List.of():List.of("Energía","Alimentación"),resultado.categoriasIncompletas());
        return new Perfil(resultado,repo,lista);
    }
    private void agregar(List<Habito> lista,String categoria,String nombre,String valor,String unidad) {
        var u=new Usuario(); u.setId(10000L); var c=new CategoriaHabito(); c.setNombre(categoria);
        var h=new Habito(); h.setId((long)lista.size()+1); h.setUsuario(u); h.setCategoria(c); h.setNombre(nombre);
        h.setValor(new BigDecimal(valor)); h.setUnidad(unidad); h.setFechaRegistro(LocalDateTime.of(2026,11,8,12,0));
        h.setFechaActualizacion(h.getFechaRegistro()); lista.add(h);
    }
}
