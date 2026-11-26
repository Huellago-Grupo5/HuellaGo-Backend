package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Motivo.*;
import static com.huellago.backend.dtos.ResultadoPresentacionRecomendacionesIADTO.*;
import static org.junit.jupiter.api.Assertions.*;

/** Todas las selecciones son SINTÉTICAS construidas a mano, NO inferencias reales.
 * Sin HTTP, mocks de éxito IA, repositorios, Spring, DataSource ni certificación ambiental. */
class PresentacionRecomendacionesIAServiceImplTest {
    private static final String[] IDS = {"TR_PLANIFICAR_DESPLAZAMIENTOS","TR_REVISAR_MOVILIDAD_ACTIVA",
            "EN_REVISAR_REGISTRO_HOGAR","AL_PLANIFICAR_APROVECHAMIENTO","RE_INICIAR_SEPARACION","RE_REVISAR_ENTREGA"};
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final CatalogoAccionesAmbientalesServiceImpl catalogo = new CatalogoAccionesAmbientalesServiceImpl();
    private final PresentacionRecomendacionesIAServiceImpl servicio = new PresentacionRecomendacionesIAServiceImpl();

    @ParameterizedTest @ValueSource(strings={"TR_PLANIFICAR_DESPLAZAMIENTOS","TR_REVISAR_MOVILIDAD_ACTIVA","EN_REVISAR_REGISTRO_HOGAR","AL_PLANIFICAR_APROVECHAMIENTO","RE_INICIAR_SEPARACION","RE_REVISAR_ENTREGA"})
    void seisPerfilesSinteticosConservanSeleccionYDatos(String id) {
        var c=contexto(id); String enfoque=enfoqueEjemplo(id); var s=sintetica(c,id,enfoque);
        String antes=mapper.writeValueAsString(s), datosAntes=mapper.writeValueAsString(c);
        var r=presentar(c,s); var d=r.resultados().getFirst(); var p=d.presentacion();
        assertEquals(Estado.SELECCION_ACEPTADA,r.estado()); assertEquals(ACEPTADA_ESTRUCTURALMENTE,r.evaluacionVerificada().estado());
        assertEquals(EstadoDetalle.VISTA_PREVIA_BORRADOR,d.estado()); assertEquals(Motivo.REVISION_HUMANA_PENDIENTE,d.motivo());
        assertSame(s.propuesta().resultados().getFirst(),d.seleccionOriginal());
        assertEquals(id,p.accionId()); assertEquals("1",p.versionAccion()); assertEquals("1",p.versionPresentacion());
        assertEquals(enfoque,p.enfoque()); assertTrue(p.enfoqueSeleccionado());
        assertEquals(Revision.BORRADOR,p.estado()); assertEquals(PENDIENTE_REVISION,p.revisionHumana());
        assertFalse(r.publicable()); assertFalse(p.publicable());
        assertFalse(p.titulo().isBlank()); assertTrue(p.titulo().length()<=160);
        assertFalse(p.descripcion().isBlank()); assertTrue(p.descripcion().length()<=1000);
        assertFalse(p.motivo().isBlank()); assertTrue(p.motivo().length()<=1000);
        assertFalse(p.motivo().contains("{")); assertFalse(p.motivo().contains("null"));
        assertEquals(antes,mapper.writeValueAsString(s)); assertEquals(datosAntes,mapper.writeValueAsString(c));
        var requeridas=catalogo.compatibles(c,Set.of()).stream().filter(a->a.definicion().id().equals(id)).findFirst().orElseThrow().definicion().evidenciasRequeridas();
        assertEquals(requeridas,p.camposContextoPermitidos());
        assertFalse(mapper.writeValueAsString(r).contains("correo")); assertFalse(mapper.writeValueAsString(r).contains("JWT"));
        if(id.equals(IDS[0])) { assertTrue(p.motivo().contains("Auto, 100 km/semana y 5 días/semana")); }
        if(id.equals(IDS[1])) { assertTrue(p.motivo().contains("Bicicleta, 20 km/semana y 4 días/semana")); assertFalse(p.descripcion().contains("automóvil")); }
        if(id.equals(IDS[2])) { assertTrue(p.motivo().contains("12.345 kWh/día del hogar")); assertTrue(p.motivo().contains("Casa, 3 personas")); }
        if(id.equals(IDS[3])) { assertTrue(p.motivo().contains("Vegetariana")); assertFalse(p.descripcion().contains("carne")); }
        if(id.equals(IDS[4])) assertTrue(p.motivo().contains("reciclaje: no; uso de plásticos: Medio"));
        if(id.equals(IDS[5])) { assertTrue(p.motivo().contains("Registraste que reciclas y un uso de plásticos Bajo")); assertFalse(p.descripcion().contains("comienza")); }
    }

    @ParameterizedTest @ValueSource(strings={"TR_PLANIFICAR_DESPLAZAMIENTOS","TR_REVISAR_MOVILIDAD_ACTIVA","EN_REVISAR_REGISTRO_HOGAR","AL_PLANIFICAR_APROVECHAMIENTO","RE_INICIAR_SEPARACION","RE_REVISAR_ENTREGA"})
    void dosEnfoquesTienenPasosDistintos(String id) {
        var c=contexto(id); var a=catalogo.compatibles(c,Set.of()).stream().filter(x->x.definicion().id().equals(id)).findFirst().orElseThrow();
        var enfoques=a.definicion().parametros().get("enfoque").valoresPermitidos().stream().map(v->((Texto)v).valor()).sorted().toList();
        assertEquals(2,enfoques.size()); Set<String> pasos=new HashSet<>();
        for(String enfoque:enfoques) {
            var s=sintetica(c,id,enfoque); var p=presentar(c,s).resultados().getFirst().presentacion();
            assertEquals(enfoque,p.enfoque()); assertTrue(p.enfoqueSeleccionado()); assertTrue(pasos.add(p.descripcion()));
            assertEquals(new Texto(enfoque),s.propuesta().resultados().getFirst().accion().parametros().get("enfoque"));
        }
    }

    @ParameterizedTest @ValueSource(strings={"TR_PLANIFICAR_DESPLAZAMIENTOS","TR_REVISAR_MOVILIDAD_ACTIVA","EN_REVISAR_REGISTRO_HOGAR","AL_PLANIFICAR_APROVECHAMIENTO","RE_INICIAR_SEPARACION","RE_REVISAR_ENTREGA"})
    void sinEnfoqueEsVistaPreviaExplicitaNoParametroElegidoPorJava(String id) {
        var c=contexto(id); var s=sintetica(c,id,null); var p=presentar(c,s).resultados().getFirst().presentacion();
        assertEquals("SIN_ENFOQUE",p.enfoque()); assertFalse(p.enfoqueSeleccionado()); assertFalse(p.publicable());
        assertTrue(s.propuesta().resultados().getFirst().accion().parametros().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings={"id","version","enfoque","parametroExtra","evidencia","ambito","booleano","categoria","textoLibre"})
    void propuestasInvalidasSeRechazanAunqueEtiquetaDigaAceptada(String caso) {
        String id=caso.equals("ambito")?IDS[2]:IDS[4]; var c=contexto(id); var s=sintetica(c,id,null);
        var arbol=mapper.readTree(s.propuestaJson()); var p=(ObjectNode)arbol.get("resultados").get(0);
        switch(caso) {
            case "id" -> ((ObjectNode)p.get("accion")).put("id","INEXISTENTE");
            case "version" -> ((ObjectNode)p.get("accion")).put("version","99");
            case "enfoque" -> ((ObjectNode)p.get("accion").get("parametros")).set("enfoque",mapper.readTree("{\"tipo\":\"TEXTO\",\"valor\":\"INVENTADO\"}"));
            case "parametroExtra" -> ((ObjectNode)p.get("accion").get("parametros")).set("extra",mapper.readTree("{\"tipo\":\"TEXTO\",\"valor\":\"x\"}"));
            case "evidencia" -> ((ObjectNode)p.get("evidencias").get(0).get("valor")).put("valor","ALTERADO");
            case "ambito" -> { for(var e:p.get("evidencias")) if(e.get("ruta").asString().equals("/energia/consumoKwhDiario")) ((ObjectNode)e).put("ambito","NO_APLICA"); }
            case "booleano" -> { for(var e:p.get("evidencias")) if(e.get("ruta").asString().equals("/residuos/reciclas")) ((ObjectNode)e.get("valor")).put("valor",true); }
            case "categoria" -> p.put("categoria","Alimentación");
            case "textoLibre" -> { p.put("titulo","Texto no aprobado"); p.put("descripcion","Texto no aprobado"); p.put("motivo","Texto no aprobado"); }
        }
        String json=mapper.writeValueAsString(arbol);
        var alterada=new ResultadoGeneracionFundamentadaIADTO(json,mapper.readValue(json,RecomendacionFundamentadaIADTO.class),s.evaluacion());
        var r=presentar(c,alterada); assertEquals(Estado.SELECCION_RECHAZADA,r.estado()); assertTrue(r.resultados().isEmpty()); assertFalse(r.publicable());
        if(caso.equals("ambito")) assertTrue(r.evaluacionVerificada().motivos().contains(AMBITO_INVALIDO));
        if(caso.equals("booleano")) assertTrue(r.evaluacionVerificada().motivos().contains(EVIDENCIA_NO_COINCIDE));
    }

    @Test void accionIncompatibleNoProducePresentacion() {
        var s=sintetica(contexto(IDS[0]),IDS[0],null); var c=contexto(IDS[1]); var r=presentar(c,s);
        assertEquals(Estado.SELECCION_RECHAZADA,r.estado()); assertTrue(r.resultados().isEmpty());
    }

    @Test void accionRealizadaNoSeSustituyePorOtra() {
        var c=contexto(IDS[0]); var s=sintetica(c,IDS[0],null); var realizadas=Set.of(new AccionAmbientalAdmisible.Referencia(IDS[0],"1"));
        var r=servicio.presentarParaPruebasSinteticas(c,s,catalogo.compatibles(c,realizadas),realizadas);
        assertEquals(Estado.SELECCION_RECHAZADA,r.estado()); assertTrue(r.evaluacionVerificada().motivos().contains(ACCION_REPETIDA)); assertTrue(r.resultados().isEmpty());
    }

    @Test void abstencionOriginalNoProduceTextoNiInferenciaAtribuida() {
        var c=contexto(IDS[4]); var p=new Propuesta("Residuos",Decision.ABSTENERSE,null,List.of(),null,null,null,List.of(),MotivoAbstencion.NO_VERIFICABLE);
        var s=resultado(new RecomendacionFundamentadaIADTO("2",List.of(p))); var r=presentar(c,s); var d=r.resultados().getFirst();
        assertEquals(Estado.SELECCION_ACEPTADA,r.estado()); assertEquals(EstadoDetalle.ABSTENCION_V2,d.estado());
        assertSame(p,d.seleccionOriginal()); assertEquals(MotivoAbstencion.NO_VERIFICABLE,d.seleccionOriginal().motivoAbstencion());
        assertNull(d.presentacion()); assertEquals(Motivo.ABSTENCION_ORIGINAL,d.motivo());
        // Es una abstención SINTÉTICA de prueba. El prototipo no afirma haber invocado a Qwen3.
    }

    @Test void alternativaAutoAhoraTieneSuPropiaFamiliaSinCambiarSeleccion() {
        var c=contexto(IDS[0]); var s=sintetica(c,"TR_EXPLORAR_ALTERNATIVAS_AUTO",null); var r=presentar(c,s); var d=r.resultados().getFirst();
        assertEquals(Estado.SELECCION_ACEPTADA,r.estado()); assertEquals(EstadoDetalle.VISTA_PREVIA_BORRADOR,d.estado());
        assertEquals(Motivo.REVISION_HUMANA_PENDIENTE,d.motivo()); assertEquals("SIN_ENFOQUE",d.presentacion().enfoque());
        assertEquals("TR_EXPLORAR_ALTERNATIVAS_AUTO",d.seleccionOriginal().accion().id());
    }

    @Test void contextoIncompletoNoSeRellena() {
        var c=contexto(IDS[2]); var s=sintetica(c,IDS[2],null);
        var incompleto=new ContextoRecomendacionesIADTO(null,new ContextoRecomendacionesIADTO.Energia(null,3,"Electricidad",new BigDecimal("12.345")),null,null);
        var r=servicio.presentarParaPruebasSinteticas(incompleto,s,List.of(),Set.of());
        assertEquals(Estado.SELECCION_RECHAZADA,r.estado()); assertTrue(r.evaluacionVerificada().motivos().contains(CONTEXTO_INVALIDO));
    }

    @Test void consumoAusenteNoSeInterpretaComoCero() {
        var c=contexto(IDS[2]); var s=sintetica(c,IDS[2],null);
        var sinConsumo=new ContextoRecomendacionesIADTO(null,new ContextoRecomendacionesIADTO.Energia("Casa",3,"Electricidad",null),null,null);
        var r=presentar(sinConsumo,s); assertEquals(Estado.SELECCION_RECHAZADA,r.estado()); assertTrue(r.resultados().isEmpty());
    }

    @Test void ceroRealSeConservaYAmbitoHogarNoSeDividePorHabitantes() {
        var c=new ContextoRecomendacionesIADTO(null,new ContextoRecomendacionesIADTO.Energia("Casa",3,"Electricidad",BigDecimal.ZERO),null,null);
        var s=sintetica(c,IDS[2],null); var r=presentar(c,s);
        assertTrue(r.resultados().getFirst().presentacion().motivo().contains("0 kWh/día del hogar"));
        assertEquals(Ambito.HOGAR,s.propuesta().resultados().getFirst().evidencias().stream().filter(e->e.ruta().endsWith("consumoKwhDiario")).findFirst().orElseThrow().ambito());
    }

    @Test void datosNormalizadosMantienenSignificado() {
        var c=new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte(" auto ",new BigDecimal("100.000"),5),null,null,null);
        var s=sintetica(c,IDS[0],null); var r=presentar(c,s);
        assertTrue(r.resultados().getFirst().presentacion().motivo().contains("Auto, 100 km/semana y 5 días/semana"));
        assertEquals(" auto ",c.transporte().medio());
    }

    @Test void etiquetaRechazadaNoSePromueveAAceptada() {
        var c=contexto(IDS[0]); var s=sintetica(c,IDS[0],null);
        var rechazada=new ResultadoGeneracionFundamentadaIADTO(s.propuestaJson(),s.propuesta(),new ResultadoEvaluacionRecomendacionIADTO(RECHAZADA,List.of(),List.of(CONTRATO_INVALIDO)));
        assertEquals(Estado.SELECCION_RECHAZADA,presentar(c,rechazada).estado());
    }

    @Test void jsonYPropuestaJavaDiferentesSeRechazan() {
        var c=contexto(IDS[0]); var s=sintetica(c,IDS[0],null);
        var otra=sintetica(c,IDS[0],"AGRUPACION");
        assertEquals(Estado.SELECCION_RECHAZADA,presentar(c,new ResultadoGeneracionFundamentadaIADTO(s.propuestaJson(),otra.propuesta(),s.evaluacion())).estado());
    }

    @Test void definicionesManipuladasNoAutorizanAcciones() {
        var c=contexto(IDS[0]); var s=sintetica(c,IDS[0],null); var disponibles=catalogo.compatibles(c,Set.of()); var a=disponibles.getFirst();
        var falsa=new CatalogoAccionesAmbientalesService.Candidata(a.definicion(),"Objetivo alterado",a.descripcion(),a.restricciones(),a.estadoRevision());
        var r=servicio.presentarParaPruebasSinteticas(c,s,List.of(falsa),Set.of());
        assertEquals(Estado.SELECCION_RECHAZADA,r.estado()); assertTrue(r.evaluacionVerificada().motivos().contains(DEFINICIONES_INVALIDAS));
    }

    @Test void contextoSinCandidatasNoEsAbstencionDelModelo() {
        var c=contexto(IDS[0]); var realizadas=catalogo.compatibles(c,Set.of()).stream().map(a->new AccionAmbientalAdmisible.Referencia(a.definicion().id(),a.definicion().version())).collect(java.util.stream.Collectors.toSet());
        var s=new ResultadoGeneracionFundamentadaIADTO(null,null,new ResultadoEvaluacionRecomendacionIADTO(PENDIENTE_REVISION,List.of(),List.of()));
        var r=servicio.presentarParaPruebasSinteticas(c,s,List.of(),realizadas);
        assertEquals(Estado.SIN_PROPUESTA,r.estado()); assertNull(r.evaluacionVerificada()); assertTrue(r.resultados().isEmpty()); assertFalse(r.publicable());
    }

    @Test void ausenciaDeSeleccionNoDisparaMotorAutonomo() {
        var c=contexto(IDS[0]); var r=servicio.presentarParaPruebasSinteticas(c,null,catalogo.compatibles(c,Set.of()),Set.of());
        assertEquals(Estado.SELECCION_RECHAZADA,r.estado()); assertTrue(r.resultados().isEmpty());
    }

    @Test void variasCategoriasConservanAbstencionesYPresentacionExacta() {
        var t=contexto(IDS[0]); var e=contexto(IDS[2]);
        var c=new ContextoRecomendacionesIADTO(t.transporte(),e.energia(),null,null);
        var ps=List.of(sintetica(t,"TR_EXPLORAR_ALTERNATIVAS_AUTO",null).propuesta().resultados().getFirst(),
                new Propuesta("Energía",Decision.ABSTENERSE,null,List.of(),null,null,null,List.of(),MotivoAbstencion.NO_VERIFICABLE));
        var r=presentar(c,resultado(new RecomendacionFundamentadaIADTO("2",ps)));
        assertEquals(Estado.SELECCION_ACEPTADA,r.estado()); assertEquals(EstadoDetalle.VISTA_PREVIA_BORRADOR,r.resultados().get(0).estado());
        assertEquals(EstadoDetalle.ABSTENCION_V2,r.resultados().get(1).estado()); assertFalse(r.publicable());
    }

    @Test void resultadoYDefinicionesSonInmutables() {
        var c=contexto(IDS[0]); var r=presentar(c,sintetica(c,IDS[0],null));
        assertThrows(UnsupportedOperationException.class,()->r.resultados().clear());
        assertThrows(UnsupportedOperationException.class,()->r.resultados().getFirst().presentacion().camposContextoPermitidos().clear());
        assertThrows(UnsupportedOperationException.class,()->r.resultados().getFirst().seleccionOriginal().accion().parametros().put("enfoque",new Texto("NECESIDAD")));
    }

    @Test void documentaSeisVistasPreviasSinteticasSinCertificarlas() throws Exception {
        List<Object> ejemplos=new ArrayList<>();
        for(String id:IDS) {
            var c=contexto(id); var s=sintetica(c,id,enfoqueEjemplo(id)); var r=presentar(c,s);
            ejemplos.add(Map.of("tipoSeleccion","SINTETICA_SIN_INFERENCIA","contexto",c,"seleccion",s,"vistaPrevia",r,"publicable",r.publicable()));
        }
        Files.createDirectories(Path.of("target/paso99"));
        Files.writeString(Path.of("target/paso99/ejemplos-sinteticos.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ejemplos),StandardCharsets.UTF_8);
    }

    @Test void catalogoCompletoTieneDiezFamiliasYTreintaVariantesBorrador() throws Exception {
        assertEquals(10,catalogo.listarBorradores().size()); Set<String> claves=new HashSet<>();
        Set<String> familias=new HashSet<>(); List<Object> ejemplos=new ArrayList<>();
        for(var candidata:catalogo.listarBorradores()) {
            var a=candidata.definicion(); var c=contexto(a.id()); Set<String> pasosConEnfoque=new HashSet<>();
            List<String> enfoques=new ArrayList<>(a.parametros().get("enfoque").valoresPermitidos().stream().map(v->((Texto)v).valor()).sorted().toList());
            assertEquals(2,enfoques.size()); enfoques.add(null);
            for(String enfoque:enfoques) {
                var s=sintetica(c,a.id(),enfoque); String original=mapper.writeValueAsString(s);
                var r=presentar(c,s); var d=r.resultados().getFirst(); var p=d.presentacion();
                assertEquals(Estado.SELECCION_ACEPTADA,r.estado()); assertEquals(EstadoDetalle.VISTA_PREVIA_BORRADOR,d.estado());
                assertNotNull(p); assertEquals(a.id(),p.accionId()); assertEquals(a.version(),p.versionAccion()); assertEquals("1",p.versionPresentacion());
                assertEquals(enfoque==null?"SIN_ENFOQUE":enfoque,p.enfoque()); assertEquals(enfoque!=null,p.enfoqueSeleccionado());
                assertEquals(a.evidenciasRequeridas(),p.camposContextoPermitidos()); assertEquals(Revision.BORRADOR,p.estado());
                assertEquals(PENDIENTE_REVISION,p.revisionHumana()); assertFalse(p.publicable()); assertFalse(r.publicable());
                assertFalse(p.titulo().isBlank()); assertTrue(p.titulo().length()<=160);
                assertFalse(p.descripcion().isBlank()); assertTrue(p.descripcion().length()<=1000); assertFalse(p.descripcion().contains("{"));
                assertFalse(p.motivo().isBlank()); assertTrue(p.motivo().length()<=1000); assertFalse(p.motivo().contains("{"));
                assertSame(s.propuesta().resultados().getFirst(),d.seleccionOriginal()); assertEquals(original,mapper.writeValueAsString(s));
                assertTrue(claves.add(a.id()+":"+a.version()+":"+p.enfoque())); familias.add(a.id());
                if(enfoque!=null) assertTrue(pasosConEnfoque.add(p.descripcion()),"Los dos enfoques deben producir pasos distintos");
                else assertTrue(s.propuesta().resultados().getFirst().accion().parametros().isEmpty());
                ejemplos.add(Map.of("tipoSeleccion","SINTETICA_SIN_INFERENCIA","contexto",c,"seleccion",s,"vistaPrevia",r,"publicable",false));
            }
            assertEquals(2,pasosConEnfoque.size());
        }
        assertEquals(10,familias.size()); assertEquals(30,claves.size());
        assertEquals(catalogo.listarBorradores().stream().map(a->a.definicion().id()).collect(java.util.stream.Collectors.toSet()),familias);
        Files.createDirectories(Path.of("target/paso103"));
        Files.writeString(Path.of("target/paso103/treinta-variantes-despues.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ejemplos),StandardCharsets.UTF_8);
    }

    @ParameterizedTest @ValueSource(strings={"Electricidad","Gas","Solar"})
    void usosEnergeticosAdaptadosAFuenteSinInventarConsumo(String fuente) {
        var c=new ContextoRecomendacionesIADTO(null,new ContextoRecomendacionesIADTO.Energia("Casa",3,fuente,null),null,null);
        for(String enfoque:new String[]{null,"IDENTIFICAR_USOS","COMPROBAR_NECESIDAD"}) {
            var r=presentar(c,sintetica(c,"EN_REVISAR_USOS",enfoque)); var p=r.resultados().getFirst().presentacion();
            assertEquals(Estado.SELECCION_ACEPTADA,r.estado()); assertTrue(p.descripcion().contains("("+fuente+")"));
            assertTrue(p.motivo().contains("fuente "+fuente)); assertFalse(p.motivo().contains("kWh"));
            assertFalse(p.descripcion().contains("desconecta")); assertFalse(p.motivo().contains("continuo"));
            assertEquals(Set.of("/energia/vivienda","/energia/personas","/energia/fuente"),p.camposContextoPermitidos());
        }
    }

    @ParameterizedTest @ValueSource(strings={"ausente","cero","presente"})
    void consumoOpcionalNoCambiaSignificadoNiSeConvierteAPersona(String caso) {
        BigDecimal consumo=caso.equals("ausente")?null:caso.equals("cero")?BigDecimal.ZERO:new BigDecimal("12.345");
        var c=new ContextoRecomendacionesIADTO(null,new ContextoRecomendacionesIADTO.Energia("Casa",3,"Electricidad",consumo),null,null);
        var s=sintetica(c,"EN_REVISAR_USOS",null);
        if(consumo!=null) {
            var p=s.propuesta().resultados().getFirst(); var ev=new ArrayList<>(p.evidencias());
            ev.add(new Evidencia("/energia/consumoKwhDiario",new Numero(consumo),Ambito.HOGAR));
            s=resultado(new RecomendacionFundamentadaIADTO("2",List.of(new Propuesta(p.categoria(),p.decision(),p.accion(),ev,null,null,null,List.of(),null))));
        }
        String antes=mapper.writeValueAsString(s); var r=presentar(c,s); var v=r.resultados().getFirst().presentacion();
        assertEquals(Estado.SELECCION_ACEPTADA,r.estado()); assertEquals(antes,mapper.writeValueAsString(s));
        assertFalse(v.motivo().contains("kWh")); // La familia de usos no necesita ni interpreta el consumo opcional.
        if(consumo!=null) {
            var e=r.resultados().getFirst().seleccionOriginal().evidencias().stream().filter(x->x.ruta().endsWith("consumoKwhDiario")).findFirst().orElseThrow();
            assertEquals(Ambito.HOGAR,e.ambito()); assertEquals(0,consumo.compareTo(((Numero)e.valor()).valor()));
        }
    }

    @ParameterizedTest @ValueSource(strings={"Mixta","Vegetariana","Vegana"})
    void presentacionesAlimentariasRespetanTipoSinImponerAlimentos(String tipo) {
        var c=new ContextoRecomendacionesIADTO(null,null,new ContextoRecomendacionesIADTO.Alimentacion(tipo),null);
        for(String enfoque:new String[]{null,"PRESENTACION","DISPONIBILIDAD"}) {
            var r=presentar(c,sintetica(c,"AL_REVISAR_PRESENTACION",enfoque)); var p=r.resultados().getFirst().presentacion();
            assertTrue(p.motivo().contains("es "+tipo)); assertEquals(Set.of("/alimentacion/tipo"),p.camposContextoPermitidos());
            assertFalse(p.descripcion().contains("carne")); assertFalse(p.descripcion().contains("menor huella")); assertFalse(p.publicable());
        }
    }

    @ParameterizedTest @ValueSource(strings={"Bajo","Medio","Alto"})
    void reutilizacionConservaNivelCualitativoYAmbosBooleanos(String plasticos) {
        for(boolean reciclas:new boolean[]{true,false}) {
            var c=new ContextoRecomendacionesIADTO(null,null,null,new ContextoRecomendacionesIADTO.Residuos(plasticos,reciclas));
            for(String enfoque:new String[]{null,"NECESIDAD","REUTILIZACION_VIABLE"}) {
                var r=presentar(c,sintetica(c,"RE_REVISAR_REUTILIZACION",enfoque)); var p=r.resultados().getFirst().presentacion();
                assertTrue(p.motivo().contains("reciclaje: "+(reciclas?"sí":"no"))); assertTrue(p.motivo().contains("plásticos: "+plasticos));
                assertFalse(p.motivo().contains("kg")); assertFalse(p.descripcion().contains("CO2"));
                if("REUTILIZACION_VIABLE".equals(enfoque)) {
                    assertTrue(p.descripcion().contains("destinada a ese uso")); assertTrue(p.descripcion().contains("si" )||p.descripcion().contains("Si"));
                    assertTrue(p.descripcion().contains("no la reutilices para ese fin"));
                }
            }
        }
    }

    @ParameterizedTest @ValueSource(strings={"Bus","Moto","Bicicleta","Caminando"})
    void alternativasAutoNoSeOfrecenAOtrosMedios(String medio) {
        var auto=contexto(IDS[0]); var s=sintetica(auto,"TR_EXPLORAR_ALTERNATIVAS_AUTO",null);
        var c=new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte(medio,BigDecimal.TEN,4),null,null,null);
        assertEquals(Estado.SELECCION_RECHAZADA,presentar(c,s).estado());
    }

    @Test void evidenciaAusenteSeRechazaSinCompletarla() {
        var c=contexto("EN_REVISAR_USOS"); var s=sintetica(c,"EN_REVISAR_USOS",null); var p=s.propuesta().resultados().getFirst();
        var ev=p.evidencias().stream().filter(e->!e.ruta().endsWith("fuente")).toList();
        var alterada=resultado(new RecomendacionFundamentadaIADTO("2",List.of(new Propuesta(p.categoria(),p.decision(),p.accion(),ev,null,null,null,List.of(),null))));
        var r=presentar(c,alterada); assertEquals(Estado.SELECCION_RECHAZADA,r.estado());
        assertTrue(r.evaluacionVerificada().motivos().contains(EVIDENCIAS_INSUFICIENTES)); assertTrue(r.resultados().isEmpty());
    }

    @Test void sinPresentacionPorDatoNoRepresentableNoGeneraFallback() {
        var c=new ContextoRecomendacionesIADTO(null,new ContextoRecomendacionesIADTO.Energia("Casa",3,"Electricidad",new BigDecimal("1E+101")),null,null);
        var s=sintetica(c,IDS[2],null); var r=presentar(c,s); var d=r.resultados().getFirst();
        assertEquals(Estado.SELECCION_ACEPTADA,r.estado()); assertEquals(EstadoDetalle.SIN_PRESENTACION,d.estado());
        assertEquals(Motivo.DATOS_NO_REPRESENTABLES,d.motivo()); assertNull(d.presentacion()); assertFalse(r.publicable());
        assertSame(s.propuesta().resultados().getFirst(),d.seleccionOriginal());
    }

    @Test void exactamenteDiezCambiosEditorialesYVeinteVariantesIdenticas() throws Exception {
        // Huellas de la l?nea base del PASO 101, capturadas ANTES de editar. No requiere archivos target.
        Map<String,String> anteriores=Map.ofEntries(
                Map.entry("TR_PLANIFICAR_DESPLAZAMIENTOS|1|AGRUPACION", "fa1fee1d542bd8232ff780b2f5ff901a62211a2b72951836d0ceb763ec9101ac"),
                Map.entry("TR_PLANIFICAR_DESPLAZAMIENTOS|1|NECESIDAD", "d76148c78e83f41b3fbc9961c0ba61563e72f6ac51db780670b163fbbca7401d"),
                Map.entry("TR_PLANIFICAR_DESPLAZAMIENTOS|1|SIN_ENFOQUE", "843f97b1fe06076649279d91fba1c4efe44074e6c73923caea732516efd7d6e0"),
                Map.entry("TR_EXPLORAR_ALTERNATIVAS_AUTO|1|DISPONIBILIDAD", "d5e01e2e1b7af2a436352888df80f83f2f980485a6ac03cef5cc8b1dc3921f74"),
                Map.entry("TR_EXPLORAR_ALTERNATIVAS_AUTO|1|VIABILIDAD", "091e91a99ba74f871c941038037b60f59a76d6c2c4694a300f00c0d61d4438d9"),
                Map.entry("TR_EXPLORAR_ALTERNATIVAS_AUTO|1|SIN_ENFOQUE", "f04b8e0056cb53d78241b416cc17ce66aaae85cc59e08661269e63d915a74754"),
                Map.entry("TR_REVISAR_MOVILIDAD_ACTIVA|1|ORGANIZACION", "2722604ec908875384a3a10b2800ac4ee473c07d38ba768405d6cbd7519fa8d0"),
                Map.entry("TR_REVISAR_MOVILIDAD_ACTIVA|1|VIABILIDAD", "e52e8420819e47d5aa7b81978b4e671324d105dfc39eafba18d769ff3c9affc5"),
                Map.entry("TR_REVISAR_MOVILIDAD_ACTIVA|1|SIN_ENFOQUE", "f8c50610aaa9c42bf37691b3da6d55ee41171a44dc798d5ca064cd68329c9020"),
                Map.entry("EN_REVISAR_USOS|1|COMPROBAR_NECESIDAD", "663ccf32ab7a21da0797fb8a5835a39ea3b0dc77be3b122e5c17bc1e700efec3"),
                Map.entry("EN_REVISAR_USOS|1|IDENTIFICAR_USOS", "4faea1731a22d5ae3ccc5da574bb0538153579093ee8a685e2d319732fe682af"),
                Map.entry("EN_REVISAR_USOS|1|SIN_ENFOQUE", "d0cda34375a046374878a144c40bc6519ce696a3162907e358cc59e78b505ecd"),
                Map.entry("EN_REVISAR_REGISTRO_HOGAR|1|ALCANCE", "52dc628996a20723f4c938345478fc3125a58a8175aaf9fc7c82b571fe0801b3"),
                Map.entry("EN_REVISAR_REGISTRO_HOGAR|1|PROCEDENCIA_REGISTRO", "14f9b8cf459ae33fd091a5f65dd4616c89204953a75d8355e89c7f3ecdff3c80"),
                Map.entry("EN_REVISAR_REGISTRO_HOGAR|1|SIN_ENFOQUE", "48808832cac916bc2cfc333fe7b5a1a6974a9c6f80f0c626378e3d97afca920e"),
                Map.entry("AL_PLANIFICAR_APROVECHAMIENTO|1|DISPONIBILIDAD", "a7b8195bb2298f6ca3b41ec78a3918f689e29177c687a51d26dd93434716bc47"),
                Map.entry("AL_PLANIFICAR_APROVECHAMIENTO|1|PLANIFICACION", "7585e783564dd436ed1a1b8872b07f1f600171f4318b2806a62a7c19da151a75"),
                Map.entry("AL_PLANIFICAR_APROVECHAMIENTO|1|SIN_ENFOQUE", "a2dc9666c42f6624161a9386c9808fa851a9dc0c2f8a6a2c9c3d730381deeb7f"),
                Map.entry("AL_REVISAR_PRESENTACION|1|DISPONIBILIDAD", "8c02dc1051954cbf83ea7b39605eb97f21953d987a9048a1b1421942e3521ab4"),
                Map.entry("AL_REVISAR_PRESENTACION|1|PRESENTACION", "aaacc0bfd7e08f0c73eeb90c5f6f29e0cb51e493c73e83c8e23dd396a9114f7a"),
                Map.entry("AL_REVISAR_PRESENTACION|1|SIN_ENFOQUE", "9b2d970907aabcd9a643a120c9e7472f4aca794d86194997f51f4876c52d20b2"),
                Map.entry("RE_INICIAR_SEPARACION|1|CONDICIONES_LOCALES", "07fcc1873fa561671258d3f1596dd7a97e94b44cef696b042552cd956b160dc3"),
                Map.entry("RE_INICIAR_SEPARACION|1|SEPARACION", "be9cc9152185fc87e2e24b1cda40375a66b4ebcb872dab19f336e8b7ca744538"),
                Map.entry("RE_INICIAR_SEPARACION|1|SIN_ENFOQUE", "e5f136dbf66ff5ddb3f3a69c77e4ba32c8832edc9fde1a562fa0580dad80db22"),
                Map.entry("RE_REVISAR_ENTREGA|1|CONDICIONES_ENTREGA", "ed836d003f55d83c5538850d21ba9ef45501c1300c7f9c76dbd2a25fad63134a"),
                Map.entry("RE_REVISAR_ENTREGA|1|MATERIALES_ADMITIDOS", "9618e7fd7fa6a30b618750ccb32e98f90e8d769fc0d1a230e506af72bed9ddc7"),
                Map.entry("RE_REVISAR_ENTREGA|1|SIN_ENFOQUE", "61b9f978d3520f04de855ef6a55a14e5bd9379172e7d2ab7098abdcf501ed974"),
                Map.entry("RE_REVISAR_REUTILIZACION|1|NECESIDAD", "e3d78e6716f24ca7c747936f16fe760e667e09d42fa2399da32bbb7197d8de86"),
                Map.entry("RE_REVISAR_REUTILIZACION|1|REUTILIZACION_VIABLE", "3935666418092241f491b1bbbcf25c5dea74c23c24d5376a385c647a8589d811"),
                Map.entry("RE_REVISAR_REUTILIZACION|1|SIN_ENFOQUE", "b93911d7096b733a8cb86d23bdd27e9ba9248a2fc5b25cc5eb6a2491f8d705a0"));
        Set<String> autorizadas=Set.of(
                "TR_PLANIFICAR_DESPLAZAMIENTOS|1|SIN_ENFOQUE", "TR_REVISAR_MOVILIDAD_ACTIVA|1|SIN_ENFOQUE",
                "EN_REVISAR_REGISTRO_HOGAR|1|ALCANCE", "EN_REVISAR_REGISTRO_HOGAR|1|PROCEDENCIA_REGISTRO", "EN_REVISAR_REGISTRO_HOGAR|1|SIN_ENFOQUE",
                "RE_REVISAR_ENTREGA|1|CONDICIONES_ENTREGA", "RE_REVISAR_ENTREGA|1|MATERIALES_ADMITIDOS", "RE_REVISAR_ENTREGA|1|SIN_ENFOQUE",
                "RE_REVISAR_REUTILIZACION|1|NECESIDAD", "RE_REVISAR_REUTILIZACION|1|SIN_ENFOQUE");
        Set<String> cambios=new TreeSet<>(),identicas=new TreeSet<>(); Map<String,String> metadatos=new TreeMap<>();
        for(var candidata:catalogo.listarBorradores()) {
            var a=candidata.definicion(); var c=contexto(a.id());
            List<String> enfoques=new ArrayList<>(a.parametros().get("enfoque").valoresPermitidos().stream().map(v->((Texto)v).valor()).toList());
            enfoques.add(null);
            for(String enfoque:enfoques) {
                var r=presentar(c,sintetica(c,a.id(),enfoque)); var p=r.resultados().getFirst().presentacion();
                assertNotNull(p); String clave=p.accionId()+"|"+p.versionAccion()+"|"+p.enfoque();
                assertTrue(anteriores.containsKey(clave));
                if(anteriores.get(clave).equals(huellaPresentacion(p,true))) identicas.add(clave); else cambios.add(clave);
                metadatos.put(clave,huellaPresentacion(p,false)); assertFalse(p.publicable()); assertFalse(r.publicable());
            }
        }
        assertEquals(30,metadatos.size()); assertEquals(anteriores.keySet(),metadatos.keySet());
        assertEquals(autorizadas,cambios); assertEquals(20,identicas.size());
        // T?tulos, referencias, enfoques, campos autorizados y estados: id?nticos en LAS TREINTA.
        assertEquals("9ccde1abcea1e3006b75a8d3c4ce6a3283d26ae88b329c49e94070bca40f234a",sha256(mapper.writeValueAsString(metadatos)));
        Files.createDirectories(Path.of("target/paso103"));
        Files.writeString(Path.of("target/paso103/comparacion-comprobada.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                Map.of("cambiosEditoriales",cambios,"variantesIdenticas",identicas,"metadatosDeLasTreintaIdenticos",true)),StandardCharsets.UTF_8);
    }

    @ParameterizedTest @ValueSource(strings={"ALCANCE","PROCEDENCIA_REGISTRO","SIN_ENFOQUE"})
    void registroEnergeticoDistingueHogarConocidoYMedicionDesconocida(String enfoque) {
        var c=contexto(IDS[2]); var s=sintetica(c,IDS[2],enfoque.equals("SIN_ENFOQUE")?null:enfoque);
        var r=presentar(c,s); var p=r.resultados().getFirst().presentacion();
        assertEquals("Registraste vivienda Casa, 3 personas y Electricidad, con 12.345 kWh/día del hogar. No tenemos información sobre cómo se obtuvo ese valor, qué medición o estimación lo respalda ni su periodo original.",p.motivo());
        assertFalse(p.motivo().contains("el alcance de ese valor no"));
        var e=s.propuesta().resultados().getFirst().evidencias().stream().filter(v->v.ruta().endsWith("consumoKwhDiario")).findFirst().orElseThrow();
        assertEquals(Ambito.HOGAR,e.ambito()); assertEquals(0,new BigDecimal("12.345").compareTo(((Numero)e.valor()).valor()));
        if(enfoque.equals("ALCANCE")) assertEquals("Consulta qué periodo y qué parte del hogar cubría la medición o estimación original utilizada para obtener tu registro en kWh/día del hogar.",p.descripcion());
        if(enfoque.equals("SIN_ENFOQUE")) assertEquals("Consulta de dónde obtuviste el registro eléctrico del hogar y comprueba el periodo y la cobertura de la medición o estimación original.",p.descripcion());
    }

    @ParameterizedTest @ValueSource(strings={"CONDICIONES_ENTREGA","MATERIALES_ADMITIDOS","SIN_ENFOQUE"})
    void reciclajeRegistradoNoInventaViaDeEntrega(String enfoque) {
        var c=contexto(IDS[5]); var r=presentar(c,sintetica(c,IDS[5],enfoque.equals("SIN_ENFOQUE")?null:enfoque));
        var p=r.resultados().getFirst().presentacion();
        assertEquals("Registraste que reciclas y un uso de plásticos Bajo. No conocemos cómo lo realizas ni las indicaciones de una posible vía de entrega.",p.motivo());
        String esperada=switch(enfoque) {
            case "CONDICIONES_ENTREGA"->"Si entregas materiales a una persona, organización o servicio, consulta sus indicaciones de preparación y entrega.";
            case "MATERIALES_ADMITIDOS"->"Si utilizas una vía de entrega, consulta qué materiales admite antes de preparar la próxima entrega.";
            default->"Comprueba si tu forma de reciclar incluye entregar materiales. Si es así, consulta qué materiales admite esa vía y cuáles son sus condiciones antes de preparar una entrega.";
        };
        assertEquals(esperada,p.descripcion()); assertFalse(p.descripcion().contains("la vía de reciclaje que utilizas"));
    }

    @ParameterizedTest @ValueSource(strings={"NECESIDAD","SIN_ENFOQUE"})
    void reutilizacionCompruebaExistenciaYConservaSeguridad(String enfoque) {
        var c=contexto("RE_REVISAR_REUTILIZACION"); var r=presentar(c,sintetica(c,"RE_REVISAR_REUTILIZACION",enfoque.equals("SIN_ENFOQUE")?null:enfoque));
        var p=r.resultados().getFirst().presentacion();
        assertTrue(p.descripcion().startsWith("Comprueba si entre tus usos de plástico hay alguno desechable. Si identificas uno,"));
        if(enfoque.equals("NECESIDAD")) assertTrue(p.descripcion().endsWith("sin comprometer la seguridad ni las necesidades esenciales."));
        else {
            assertTrue(p.descripcion().contains("destinada a ese uso")); assertTrue(p.descripcion().contains("higienizarse con seguridad"));
            assertTrue(p.descripcion().contains("sin que necesites comprarla"));
            assertTrue(p.descripcion().endsWith("Si no puedes comprobar su adecuación, higiene o seguridad, no la reutilices para ese fin."));
        }
        assertTrue(p.motivo().contains("uso de plásticos: Medio")); assertFalse(p.publicable());
    }

    @ParameterizedTest @ValueSource(strings={"TR_PLANIFICAR_DESPLAZAMIENTOS","TR_REVISAR_MOVILIDAD_ACTIVA"})
    void transporteSinEnfoqueSoloSolicitaComprobaciones(String id) {
        var c=contexto(id); var s=sintetica(c,id,null); var p=presentar(c,s).resultados().getFirst().presentacion();
        assertEquals(id.equals(IDS[0])?"Anota tus próximas gestiones y comprueba qué necesidades debe cubrir cada salida antes de organizarla.":
                "Anota una próxima gestión y comprueba qué necesitas para realizarla con el medio que ya utilizas, considerando horario, carga y seguridad.",p.descripcion());
        assertEquals("SIN_ENFOQUE",p.enfoque()); assertFalse(p.enfoqueSeleccionado());
        assertTrue(s.propuesta().resultados().getFirst().accion().parametros().isEmpty());
    }

    private String huellaPresentacion(Presentacion p,boolean textos) throws Exception {
        List<Object> datos=new ArrayList<>(List.of(p.accionId(),p.versionAccion(),p.enfoque(),p.enfoqueSeleccionado(),p.versionPresentacion(),p.titulo()));
        if(textos) { datos.add(p.descripcion()); datos.add(p.motivo()); }
        datos.add(new TreeSet<>(p.camposContextoPermitidos())); datos.add(p.estado().name()); datos.add(p.revisionHumana().name());
        return sha256(mapper.writeValueAsString(datos));
    }
    private static String sha256(String texto) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
    }

    private ResultadoPresentacionRecomendacionesIADTO presentar(ContextoRecomendacionesIADTO c,ResultadoGeneracionFundamentadaIADTO s) {
        return servicio.presentarParaPruebasSinteticas(c,s,catalogo.compatibles(c,Set.of()),Set.of());
    }
    private ResultadoGeneracionFundamentadaIADTO sintetica(ContextoRecomendacionesIADTO c,String id,String enfoque) {
        var a=catalogo.compatibles(c,Set.of()).stream().filter(x->x.definicion().id().equals(id)).findFirst().orElseThrow().definicion();
        var hechos=ContratoOllamaV2.hechos(ValidadorRecomendacionesIA.contexto(c));
        var ev=a.evidenciasRequeridas().stream().sorted().map(r->{var h=mapper.readTree(mapper.writeValueAsString(hechos.get(r)));
            return new Evidencia(r,mapper.readValue(mapper.writeValueAsString(h.get("valor")),Valor.class),Ambito.valueOf(h.get("ambito").asString()));}).toList();
        var p=new Propuesta(a.categoria(),Decision.PROPONER,new Accion(id,"1",enfoque==null?Map.of():Map.of("enfoque",new Texto(enfoque))),ev,null,null,null,List.of(),null);
        return resultado(new RecomendacionFundamentadaIADTO("2",List.of(p)));
    }
    private ResultadoGeneracionFundamentadaIADTO resultado(RecomendacionFundamentadaIADTO p) {
        // Etiqueta SINTÉTICA: el servicio debe verificarla y no confiar en ella.
        return new ResultadoGeneracionFundamentadaIADTO(mapper.writeValueAsString(p),p,new ResultadoEvaluacionRecomendacionIADTO(ACEPTADA_ESTRUCTURALMENTE,List.of(),List.of()));
    }
    private ContextoRecomendacionesIADTO contexto(String id) {
        if(id.startsWith("TR_")) return new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte(
                id.equals(IDS[1])?"Bicicleta":"Auto",new BigDecimal(id.equals(IDS[1])?"20":"100"),id.equals(IDS[1])?4:5),null,null,null);
        if(id.startsWith("EN_")) return new ContextoRecomendacionesIADTO(null,new ContextoRecomendacionesIADTO.Energia("Casa",3,"Electricidad",new BigDecimal("12.345")),null,null);
        if(id.startsWith("AL_")) return new ContextoRecomendacionesIADTO(null,null,new ContextoRecomendacionesIADTO.Alimentacion("Vegetariana"),null);
        return new ContextoRecomendacionesIADTO(null,null,null,new ContextoRecomendacionesIADTO.Residuos(id.equals(IDS[5])?"Bajo":"Medio",id.equals(IDS[5])));
    }
    private String enfoqueEjemplo(String id) {
        return switch(id) { case "TR_PLANIFICAR_DESPLAZAMIENTOS"->"AGRUPACION"; case "TR_REVISAR_MOVILIDAD_ACTIVA"->"ORGANIZACION";
            case "EN_REVISAR_REGISTRO_HOGAR"->"PROCEDENCIA_REGISTRO"; case "AL_PLANIFICAR_APROVECHAMIENTO"->"PLANIFICACION";
            case "RE_INICIAR_SEPARACION"->"CONDICIONES_LOCALES"; case "RE_REVISAR_ENTREGA"->"MATERIALES_ADMITIDOS"; default->throw new IllegalArgumentException(); };
    }
}
