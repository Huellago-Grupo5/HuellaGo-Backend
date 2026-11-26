package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ContextoRecomendacionesIADTO;
import com.huellago.backend.dtos.RecomendacionFundamentadaIADTO;
import com.huellago.backend.services.AccionAmbientalAdmisible;
import com.huellago.backend.services.CatalogoAccionesAmbientalesService;
import com.huellago.backend.services.CatalogoAccionesAmbientalesService.Candidata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Estado.*;
import static com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO.Motivo.*;
import static org.junit.jupiter.api.Assertions.*;

/** Catálogo, contexto sintético y validador reales, sin Spring, HTTP ni repositorios. */
class CatalogoAccionesAmbientalesServiceImplTest {
    final CatalogoAccionesAmbientalesServiceImpl catalogo = new CatalogoAccionesAmbientalesServiceImpl();
    final ValidadorRecomendacionFundamentadaIA validador = new ValidadorRecomendacionFundamentadaIA();
    final JsonMapper mapper = JsonMapper.builder().build();

    @Test void diezBorradoresVersionadosConMetadatosEIdentidadUnica() {
        var lista = catalogo.listarBorradores(); assertEquals(10, lista.size());
        assertEquals(10, lista.stream().map(c -> c.definicion().id()).distinct().count());
        assertEquals(Map.of("Transporte", 3L, "Energía", 2L, "Alimentación", 2L, "Residuos", 3L),
                lista.stream().collect(Collectors.groupingBy(c -> c.definicion().categoria(), Collectors.counting())));
        for (var c : lista) {
            assertEquals("1", c.definicion().version());
            assertTrue(c.definicion().id().matches("[A-Z][A-Z_]+"));
            assertEquals(CatalogoAccionesAmbientalesService.EstadoRevision.BORRADOR, c.estadoRevision());
            assertFalse(c.objetivo().isBlank()); assertFalse(c.descripcion().isBlank());
            assertFalse(c.restricciones().isEmpty()); assertFalse(c.definicion().evidenciasRequeridas().isEmpty());
            assertTrue(c.definicion().evidenciasRequeridas().containsAll(c.definicion().condiciones().keySet()));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"Bus", "Auto", "Moto", "Bicicleta", "Caminando"})
    void transporteCompatibleSinInferirOtrosMedios(String medio) {
        var c = transporte(medio);
        var ids = ids(c);
        if (Set.of("Bus", "Moto").contains(medio)) assertEquals(Set.of("TR_PLANIFICAR_DESPLAZAMIENTOS"), ids);
        else if (medio.equals("Auto")) assertEquals(Set.of("TR_PLANIFICAR_DESPLAZAMIENTOS", "TR_EXPLORAR_ALTERNATIVAS_AUTO"), ids);
        else assertEquals(Set.of("TR_REVISAR_MOVILIDAD_ACTIVA"), ids);
        assertEquals(medio, c.transporte().medio());
    }

    @ParameterizedTest @ValueSource(strings = {"Mixta", "Vegetariana", "Vegana"})
    void tiposDeAlimentacionCompatiblesSinObligacionAnimal(String tipo) {
        var c = new ContextoRecomendacionesIADTO(null, null, new ContextoRecomendacionesIADTO.Alimentacion(tipo), null);
        var lista = catalogo.compatibles(c, Set.of());
        assertEquals(Set.of("AL_PLANIFICAR_APROVECHAMIENTO", "AL_REVISAR_PRESENTACION"), ids(c));
        for (var a : lista) {
            assertTrue(a.restricciones().stream().anyMatch(s -> s.contains("No prescribir") || s.contains("No formular")));
            assertEquals(ACEPTADA_ESTRUCTURALMENTE, validar(a, c, Map.of(), null).estado());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void diferenciaEntreIniciarYMejorarReciclaje(boolean reciclas) {
        var c = residuos("Medio", reciclas);
        assertEquals(Set.of("RE_REVISAR_REUTILIZACION", reciclas ? "RE_REVISAR_ENTREGA" : "RE_INICIAR_SEPARACION"), ids(c));
    }

    @ParameterizedTest @ValueSource(strings = {"Bajo", "Medio", "Alto"})
    void plasticosNoEsNivelDeReciclaje(String plasticos) {
        assertTrue(ids(residuos(plasticos, false)).contains("RE_INICIAR_SEPARACION"));
        assertFalse(ids(residuos(plasticos, true)).contains("RE_INICIAR_SEPARACION"));
        assertTrue(candidata("RE_REVISAR_REUTILIZACION").restricciones().stream().anyMatch(s -> s.contains("no nivel de reciclaje")));
    }

    @ParameterizedTest @ValueSource(strings = {"Electricidad", "Gas", "Solar"})
    void energiaSinConsumoOpcionalNoInventaRegistro(String fuente) {
        var c = energia(fuente, null);
        assertEquals(Set.of("EN_REVISAR_USOS"), ids(c));
        assertNull(c.energia().consumoKwhDiario());
    }

    @ParameterizedTest @ValueSource(strings = {"0", "12.345", "1000000.0001"})
    void consumoRegistradoIncluyeCeroYSinUmbrales(String cantidad) {
        var c = energia("Electricidad", new BigDecimal(cantidad));
        assertEquals(Set.of("EN_REVISAR_USOS", "EN_REVISAR_REGISTRO_HOGAR"), ids(c));
        assertEquals(new BigDecimal(cantidad), c.energia().consumoKwhDiario());
        assertEquals(ACEPTADA_ESTRUCTURALMENTE, validar(candidata("EN_REVISAR_REGISTRO_HOGAR"), c, Map.of(), null).estado());
    }

    @Test void categoriasAusentesInvalidasOIncompletasSeOmitenSinPerderLasCompletas() {
        var c = new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte("Bus", BigDecimal.TEN, 8),
                new ContextoRecomendacionesIADTO.Energia("Casa", null, "Electricidad", null), null,
                new ContextoRecomendacionesIADTO.Residuos("Bajo", true));
        assertEquals(ids(residuos("Bajo", true)), ids(c));
        var energiaIncompatible = new ContextoRecomendacionesIADTO(null,
                new ContextoRecomendacionesIADTO.Energia("Casa", 3, "Gas", BigDecimal.ONE), null, c.residuos());
        assertEquals(ids(residuos("Bajo", true)), ids(energiaIncompatible));
    }

    @Test void sinDatosONingunaCategoriaValidaNoHayFallback() {
        assertTrue(catalogo.compatibles(null, Set.of()).isEmpty());
        assertTrue(catalogo.compatibles(new ContextoRecomendacionesIADTO(null, null, null, null), Set.of()).isEmpty());
        assertTrue(ids(transporte("NoAdmitido")).isEmpty());
    }

    @Test void normalizacionCompatibleConValidadorExistente() {
        assertEquals(ids(transporte("Bus")), ids(transporte(" bus ")));
        assertEquals(ids(energia("Electricidad", null)), ids(energia(" electricidad ", null)));
    }

    @Test void excluirRealizadasPorIdentificadorYVersionSinAfectarOtras() {
        var c = transporte("Auto");
        var ref = new AccionAmbientalAdmisible.Referencia("TR_PLANIFICAR_DESPLAZAMIENTOS", "1");
        var realizadas = Set.of(ref);
        assertEquals(List.of("TR_EXPLORAR_ALTERNATIVAS_AUTO"), catalogo.compatibles(c, realizadas).stream().map(a -> a.definicion().id()).toList());
        assertEquals(2, catalogo.compatibles(c, Set.of(new AccionAmbientalAdmisible.Referencia(ref.id(), "2"))).size());
        assertEquals(2, catalogo.compatibles(c, Set.of(new AccionAmbientalAdmisible.Referencia("AJENA", "1"))).size());
        assertEquals(Set.of(ref), realizadas);
    }

    @Test void todasRealizadasDaColeccionVaciaYNoAfectaOtraConsulta() {
        var c = completo();
        var realizadas = catalogo.compatibles(c, Set.of()).stream()
                .map(a -> new AccionAmbientalAdmisible.Referencia(a.definicion().id(), a.definicion().version())).collect(Collectors.toSet());
        assertTrue(catalogo.compatibles(c, realizadas).isEmpty());
        assertFalse(catalogo.compatibles(c, Set.of()).isEmpty());
        assertThrows(NullPointerException.class, () -> catalogo.compatibles(c, null));
    }

    @ParameterizedTest @ValueSource(strings = {"TR_PLANIFICAR_DESPLAZAMIENTOS", "TR_EXPLORAR_ALTERNATIVAS_AUTO",
            "TR_REVISAR_MOVILIDAD_ACTIVA", "EN_REVISAR_USOS", "EN_REVISAR_REGISTRO_HOGAR",
            "AL_PLANIFICAR_APROVECHAMIENTO", "AL_REVISAR_PRESENTACION", "RE_INICIAR_SEPARACION", "RE_REVISAR_ENTREGA", "RE_REVISAR_REUTILIZACION"})
    void cadaDefinicionUtilizableDirectamentePorV2(String id) {
        var candidata = candidata(id);
        var base = completo();
        var c = new ContextoRecomendacionesIADTO(id.equals("TR_REVISAR_MOVILIDAD_ACTIVA")
                ? transporte("Bicicleta").transporte() : base.transporte(), base.energia(), base.alimentacion(),
                id.equals("RE_REVISAR_ENTREGA") ? residuos("Medio", true).residuos() : base.residuos());
        assertTrue(ids(c).contains(id));
        assertEquals(ACEPTADA_ESTRUCTURALMENTE, validar(candidata, c, Map.of(), null).estado());
        var regla = candidata.definicion().parametros().get("enfoque");
        for (Valor valor : regla.valoresPermitidos())
            assertEquals(ACEPTADA_ESTRUCTURALMENTE, validar(candidata, c, Map.of("enfoque", valor), null).estado());
        assertTrue(validar(candidata, c, Map.of("inventado", new Texto("No autorizado")), null).motivos().contains(PARAMETRO_NO_AUTORIZADO));
    }

    @Test void validadorRechazaReinicioConReciclajeEvidenciaAusenteYVersionFalsa() {
        assertTrue(validar(candidata("RE_INICIAR_SEPARACION"), residuos("Medio", true), Map.of(), null)
                .motivos().contains(ACCION_INCOMPATIBLE));
        assertTrue(validar(candidata("EN_REVISAR_REGISTRO_HOGAR"), energia("Electricidad", null), Map.of(), null)
                .motivos().contains(EVIDENCIAS_INSUFICIENTES));
        assertTrue(validar(candidata("RE_REVISAR_ENTREGA"), residuos("Medio", true), Map.of(), "99")
                .motivos().contains(VERSION_ACCION_INVALIDA));
    }

    @Test void textoPersonalizadoNoQuedaCertificadoPorElCatalogo() {
        var a = candidata("RE_REVISAR_ENTREGA"); var c = residuos("Medio", true);
        var propuestas = propuestas(a, c, Map.of(), null);
        var p = propuestas.getFirst();
        var conTexto = new Propuesta(p.categoria(), p.decision(), p.accion(), p.evidencias(), "Título ficticio",
                "Descripción ficticia", "Motivo ficticio", p.informacionAdicional(), null);
        var r = validador.validar(mapper.writeValueAsString(new RecomendacionFundamentadaIADTO("2", List.of(conTexto))),
                c, List.of(a.definicion()), Set.of());
        assertEquals(PENDIENTE_REVISION, r.estado());
    }

    @Test void catalogoYDefinicionesSonInmutablesSinDatosPersonales() {
        var a = candidata("RE_REVISAR_ENTREGA");
        assertThrows(UnsupportedOperationException.class, () -> catalogo.listarBorradores().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalogo.compatibles(completo(), Set.of()).clear());
        assertThrows(UnsupportedOperationException.class, () -> a.restricciones().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.definicion().condiciones().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.definicion().condiciones().values().iterator().next().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.definicion().evidenciasRequeridas().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.definicion().parametros().get("enfoque").valoresPermitidos().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.definicion().informacionAdicionalPermitida().clear());
        String antes = mapper.writeValueAsString(catalogo.listarBorradores());
        catalogo.compatibles(completo(), Set.of());
        assertEquals(antes, mapper.writeValueAsString(catalogo.listarBorradores()));
        for (String prohibido : List.of("usuarioId", "correo", "password", "jwt", "token")) assertFalse(antes.contains("\"" + prohibido + "\""));
        assertFalse(antes.contains("12.345"));
    }

    private Candidata candidata(String id) { return catalogo.listarBorradores().stream().filter(c -> c.definicion().id().equals(id)).findFirst().orElseThrow(); }
    private Set<String> ids(ContextoRecomendacionesIADTO c) { return catalogo.compatibles(c, Set.of()).stream().map(a -> a.definicion().id()).collect(Collectors.toSet()); }
    private ContextoRecomendacionesIADTO transporte(String medio) {
        return new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte(medio, new BigDecimal("80"), 5), null, null, null);
    }
    private ContextoRecomendacionesIADTO energia(String fuente, BigDecimal consumo) {
        return new ContextoRecomendacionesIADTO(null, new ContextoRecomendacionesIADTO.Energia("Casa", 3, fuente, consumo), null, null);
    }
    private ContextoRecomendacionesIADTO residuos(String plasticos, boolean reciclas) {
        return new ContextoRecomendacionesIADTO(null, null, null, new ContextoRecomendacionesIADTO.Residuos(plasticos, reciclas));
    }
    private ContextoRecomendacionesIADTO completo() {
        return new ContextoRecomendacionesIADTO(transporte("Auto").transporte(), energia("Electricidad", new BigDecimal("12.345")).energia(),
                new ContextoRecomendacionesIADTO.Alimentacion("Mixta"), residuos("Medio", false).residuos());
    }
    private com.huellago.backend.dtos.ResultadoEvaluacionRecomendacionIADTO validar(Candidata a, ContextoRecomendacionesIADTO c, Map<String, Valor> parametros, String version) {
        return validador.validar(mapper.writeValueAsString(new RecomendacionFundamentadaIADTO("2", propuestas(a, c, parametros, version))),
                c, List.of(a.definicion()), Set.of());
    }
    private List<Propuesta> propuestas(Candidata a, ContextoRecomendacionesIADTO c, Map<String, Valor> parametros, String version) {
        var datos = ValidadorRecomendacionesIA.contexto(c);
        var campos = (Map<?, ?>) datos.get(a.definicion().categoria());
        List<Evidencia> evidencias = new ArrayList<>();
        for (String ruta : a.definicion().evidenciasRequeridas()) {
            Object valor = campos.get(ruta.substring(ruta.lastIndexOf('/') + 1));
            if (valor == null) continue;
            Valor tipado = valor instanceof String s ? new Texto(s) : valor instanceof Boolean b ? new Booleano(b) : new Numero(new BigDecimal(valor.toString()));
            evidencias.add(new Evidencia(ruta, tipado, ruta.equals("/energia/consumoKwhDiario") ? Ambito.HOGAR : Ambito.NO_APLICA));
        }
        List<Propuesta> ps = new ArrayList<>();
        for (String categoria : datos.keySet()) ps.add(categoria.equals(a.definicion().categoria())
                ? new Propuesta(categoria, Decision.PROPONER, new Accion(a.definicion().id(), version == null ? a.definicion().version() : version, parametros), evidencias, null, null, null, List.of(), null)
                : new Propuesta(categoria, Decision.ABSTENERSE, null, List.of(), null, null, null, List.of(), MotivoAbstencion.NO_VERIFICABLE));
        return ps;
    }
}
