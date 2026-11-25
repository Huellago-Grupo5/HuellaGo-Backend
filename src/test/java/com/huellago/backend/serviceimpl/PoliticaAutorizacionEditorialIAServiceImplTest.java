package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.services.*;
import com.huellago.backend.services.PoliticaAutorizacionEditorialIAService.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.*;
import static com.huellago.backend.dtos.ResultadoPresentacionRecomendacionesIADTO.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Permisos SINTÉTICOS, solo inyectados en instancias de prueba. No son aprobaciones productivas.
 * Sin red, repositorios, Spring Boot o certificación ambiental. */
class PoliticaAutorizacionEditorialIAServiceImplTest {
    static final String ID = "TR_PLANIFICAR_DESPLAZAMIENTOS";
    final CatalogoAccionesAmbientalesServiceImpl catalogo = new CatalogoAccionesAmbientalesServiceImpl();
    final PresentacionRecomendacionesIAServiceImpl presentador = new PresentacionRecomendacionesIAServiceImpl();
    final PoliticaAutorizacionEditorialIAServiceImpl vacia = new PoliticaAutorizacionEditorialIAServiceImpl();
    final JsonMapper mapper = JsonMapper.builder().build();
    ContextoRecomendacionesIADTO contexto(String medio, boolean reciclas) {
        return new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte(medio, new BigDecimal("100"), 5),
                new ContextoRecomendacionesIADTO.Energia("Casa", 3, "Electricidad", new BigDecimal("12.345")),
                new ContextoRecomendacionesIADTO.Alimentacion("Vegetariana"), new ContextoRecomendacionesIADTO.Residuos("Medio", reciclas));
    }
    List<DefinicionPresentacionIADTO> familia(String id) {
        return presentador.definicionesParaRevision().stream().filter(d -> id.equals(d.accionId())).toList();
    }
    Autorizacion permiso(DefinicionPresentacionIADTO d) {
        return new Autorizacion(d.accionId(), d.versionAccion(), d.enfoque(), d.versionPresentacion(),
                vacia.huellaDefinicion(d), d.camposContextoPermitidos());
    }
    List<Autorizacion> permisos(String id) { return familia(id).stream().map(this::permiso).toList(); }
    PoliticaAutorizacionEditorialIAServiceImpl politica(List<Autorizacion> permisos) {
        return new PoliticaAutorizacionEditorialIAServiceImpl(catalogo, presentador, () -> permisos);
    }
    @Test void estadoInicialExcluyeLasDiezFamiliasSinAlterarBorradores() {
        Set<String> observadas = new HashSet<>();
        for (String medio : List.of("Auto", "Bicicleta")) for (boolean reciclas : List.of(false, true)) {
            var r = vacia.evaluar(contexto(medio, reciclas), Set.of());
            assertTrue(r.elegibles().isEmpty()); assertFalse(r.publicable());
            assertEquals(r.compatibles().size(), r.excluidas().size());
            assertTrue(r.excluidas().values().stream().allMatch(m -> m == MotivoExclusion.SIN_AUTORIZACION));
            r.excluidas().keySet().forEach(k -> observadas.add(k.id()));
        }
        assertEquals(10, observadas.size());
        assertTrue(catalogo.listarBorradores().stream().allMatch(c -> c.estadoRevision() == CatalogoAccionesAmbientalesService.EstadoRevision.BORRADOR));
    }
    @ParameterizedTest @ValueSource(ints={1,2,3})
    void soloTresVariantesSinteticasHabilitanLaFamiliaDePrueba(int cantidad) {
        var r = politica(permisos(ID).subList(0, cantidad)).evaluar(contexto("Auto", false), Set.of());
        assertEquals(cantidad == 3 ? 1 : 0, r.elegibles().size());
        if (cantidad == 3) assertEquals(ID, r.elegibles().getFirst().definicion().id());
        else assertEquals(MotivoExclusion.FAMILIA_INCOMPLETA, r.excluidas().get(new AccionAmbientalAdmisible.Referencia(ID,"1")));
        assertTrue(vacia.evaluar(contexto("Auto",false),Set.of()).elegibles().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"accion","versionAccion","enfoque","versionPresentacion","sha","campos","duplicado"})
    void permisosIncorrectosUObsoletosNoHabilitan(String caso) {
        List<Autorizacion> lista = new ArrayList<>(permisos(ID)); var p = lista.getFirst();
        lista.set(0, new Autorizacion(caso.equals("accion") ? "ACCION_DESCONOCIDA" : p.accionId(),
                caso.equals("versionAccion") ? "999" : p.versionAccion(), caso.equals("enfoque") ? "INVENTADO" : p.enfoque(),
                caso.equals("versionPresentacion") ? "999" : p.versionPresentacion(),
                caso.equals("sha") ? "0".repeat(64) : p.sha256(), caso.equals("campos") ? Set.of("/energia/fuente") : p.camposContextoPermitidos()));
        if(caso.equals("duplicado")) lista.add(lista.getFirst());
        assertTrue(politica(lista).evaluar(contexto("Auto",false),Set.of()).elegibles().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"titulo","descripcion","motivo","campos","version"})
    void modificarDefinicionTrasRevisionInvalidaHuella(String campo) {
        var originales = presentador.definicionesParaRevision(); var objetivo = familia(ID).getFirst();
        var alterada = new DefinicionPresentacionIADTO(objetivo.accionId(), objetivo.versionAccion(), objetivo.enfoque(),
                campo.equals("version")?"2":objetivo.versionPresentacion(), campo.equals("titulo")?"Título cambiado":objetivo.titulo(),
                campo.equals("descripcion")?objetivo.descripcion()+" ":objetivo.descripcion(),
                campo.equals("motivo")?"Motivo cambiado":objetivo.motivo(),
                campo.equals("campos")?Set.of("/transporte/medio"):objetivo.camposContextoPermitidos());
        var copia = originales.stream().map(d -> d.equals(objetivo)?alterada:d).toList();
        var fuente = mock(PresentacionRecomendacionesIAService.class);
        when(fuente.definicionesParaRevision()).thenReturn(copia);
        var politica = new PoliticaAutorizacionEditorialIAServiceImpl(catalogo, fuente, () -> permisos(ID));
        assertTrue(politica.evaluar(contexto("Auto",false),Set.of()).elegibles().isEmpty());
        assertNotEquals(vacia.huellaDefinicion(objetivo), vacia.huellaDefinicion(alterada));
    }
    @Test void contenidoBaseNoContieneDatosPersonalizadosYEsInmutable() {
        var defs = presentador.definicionesParaRevision();
        assertEquals(30,defs.size()); assertEquals(10,defs.stream().map(DefinicionPresentacionIADTO::accionId).distinct().count());
        assertTrue(familia(ID).stream().allMatch(d -> d.motivo().contains("{/transporte/kmSemana}")));
        assertFalse(mapper.writeValueAsString(defs).contains("100 km/semana"));
        assertThrows(UnsupportedOperationException.class, () -> defs.clear());
        assertThrows(UnsupportedOperationException.class, () -> defs.getFirst().camposContextoPermitidos().clear());
        for (var c : catalogo.listarBorradores()) {
            var f = familia(c.definicion().id()); assertEquals(3,f.size());
            Set<String> enfoques = c.definicion().parametros().get("enfoque").valoresPermitidos().stream().map(v -> ((Texto)v).valor()).collect(Collectors.toSet());
            enfoques.add("SIN_ENFOQUE"); assertEquals(enfoques,f.stream().map(DefinicionPresentacionIADTO::enfoque).collect(Collectors.toSet()));
        }
    }
    @Test void huellaEsEstableAnteOrdenDeCamposPeroExactaAnteTextos() {
        var d = familia(ID).getFirst(); var campos = new ArrayList<>(d.camposContextoPermitidos()); Collections.reverse(campos);
        var otra = new DefinicionPresentacionIADTO(d.accionId(),d.versionAccion(),d.enfoque(),d.versionPresentacion(),d.titulo(),d.descripcion(),d.motivo(),new LinkedHashSet<>(campos));
        assertEquals(vacia.huellaDefinicion(d), vacia.huellaDefinicion(otra));
        assertTrue(vacia.huellaDefinicion(d).matches("[0-9a-f]{64}"));
    }
    @Test void autorizacionNoAnulaCompatibilidadNiAccionesRealizadas() {
        var p = politica(permisos("TR_EXPLORAR_ALTERNATIVAS_AUTO"));
        assertTrue(p.evaluar(contexto("Bicicleta",false),Set.of()).elegibles().isEmpty());
        assertTrue(p.evaluar(contexto("Auto",false),Set.of(new AccionAmbientalAdmisible.Referencia("TR_EXPLORAR_ALTERNATIVAS_AUTO","1"))).elegibles().isEmpty());
    }
    @Test void ceroAutorizacionesProduceSinPropuestaSinInvocarSelector() {
        var c = contexto("Auto",false); var lista = vacia.evaluar(c,Set.of()).elegibles();
        var selector = mock(GeneradorRecomendacionesIAService.class);
        var orquestador = new OrquestadorRecomendacionesIAServiceImpl(selector,catalogo,presentador);
        var r = orquestador.orquestarParaPruebasSinteticas(new ContextoRecomendacionesIAService.Resultado(c,List.of(),List.of(),ReglasInterpretacionAmbiental.REGLAS),lista,Set.of());
        assertEquals(Estado.SIN_PROPUESTA,r.presentacion().estado()); assertNull(r.seleccion().propuesta());
        assertTrue(r.categoriasRespondidas().isEmpty()); assertFalse(r.publicable()); verifyNoInteractions(selector);
    }
    @Test void subconjuntoSinteticamenteAutorizadoValidaYPresentaSinEnfoqueSinPublicar() {
        var c = contexto("Auto",false); var elegibles = politica(permisos(ID)).evaluar(c,Set.of()).elegibles();
        var alcance = new ValidadorRecomendacionFundamentadaIA().construirAlcanceExperimental(c,elegibles,Set.of());
        var hechos = ContratoOllamaV2.hechos(ValidadorRecomendacionesIA.contexto(c));
        var ev = elegibles.getFirst().definicion().evidenciasRequeridas().stream().sorted().map(r -> {
            var h = mapper.readTree(mapper.writeValueAsString(hechos.get(r)));
            return new Evidencia(r,mapper.readValue(mapper.writeValueAsString(h.get("valor")),Valor.class),Ambito.valueOf(h.get("ambito").asString()));
        }).toList();
        var propuesta = new RecomendacionFundamentadaIADTO("2",List.of(new Propuesta("Transporte",Decision.PROPONER,new Accion(ID,"1",Map.of()),ev,null,null,null,List.of(),null)));
        String json = mapper.writeValueAsString(propuesta);
        var evaluacion = new ValidadorRecomendacionFundamentadaIA().validarConAlcanceExperimental(json,c,alcance,Set.of());
        assertEquals(ACEPTADA_ESTRUCTURALMENTE,evaluacion.estado());
        var r = presentador.presentarConAlcanceParaPruebasSinteticas(c,new ResultadoGeneracionFundamentadaIADTO(json,propuesta,evaluacion),alcance,Set.of());
        var vista = r.resultados().getFirst().presentacion();
        assertEquals("SIN_ENFOQUE",vista.enfoque()); assertFalse(vista.enfoqueSeleccionado());
        assertEquals(Revision.BORRADOR,vista.estado()); assertEquals(PENDIENTE_REVISION,vista.revisionHumana()); assertFalse(vista.publicable());
    }
}
