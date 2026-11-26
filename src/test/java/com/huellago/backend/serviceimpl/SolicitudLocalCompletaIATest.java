package com.huellago.backend.serviceimpl;
import com.huellago.backend.dtos.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;

/** Una solicitud interceptada, sin inferencia real. No modifica aprobaciones del catalogo. */
class SolicitudLocalCompletaIATest {
    @Test void catalogoCompletoCabeSinPerderCandidatasEvidenciasNiEsquema() {
        var f=new OllamaResumen135Test(); f.preparar(); var r=f.h.constructor.construir(68L);
        var candidatas=f.catalogo.compatibles(r.contexto(),Set.of());
        var alcance=new ValidadorRecomendacionFundamentadaIA().construirAlcanceExperimental(r.contexto(),candidatas,Set.of());
        String completa=f.cliente.solicitudV2(r,candidatas,alcance);
        String compacta=org.springframework.test.util.ReflectionTestUtils.invokeMethod(f.cliente,"solicitudV2",r,candidatas,alcance,true);
        assertTrue(completa.getBytes(StandardCharsets.UTF_8).length>16384);
        System.out.println("Bytes V2 completos/compactos: "+completa.getBytes(StandardCharsets.UTF_8).length+"/"+compacta.getBytes(StandardCharsets.UTF_8).length);
        assertTrue(compacta.getBytes(StandardCharsets.UTF_8).length<=16384);
        var normalizado=ValidadorRecomendacionesIA.contexto(r.contexto());
        var originalDatos=f.mapper.readTree(f.mapper.writeValueAsString(ContratoOllamaV2.datos(normalizado,candidatas)));
        var peticion=f.mapper.readTree(compacta);
        var datos=f.mapper.readTree(peticion.get("messages").get(1).get("content").asString());
        System.out.println("Bytes V2 esquema/acciones/hechos/resumen/sistema: "
                +f.mapper.writeValueAsString(peticion.get("format")).getBytes(StandardCharsets.UTF_8).length+"/"
                +f.mapper.writeValueAsString(datos.get("accionesCandidatas")).getBytes(StandardCharsets.UTF_8).length+"/"
                +f.mapper.writeValueAsString(datos.get("hechosDisponibles")).getBytes(StandardCharsets.UTF_8).length+"/"
                +f.mapper.writeValueAsString(datos.get("resumenAmbiental")).getBytes(StandardCharsets.UTF_8).length+"/"
                +peticion.get("messages").get(0).get("content").asString().getBytes(StandardCharsets.UTF_8).length);
        assertFalse(datos.has("contexto"));
        assertEquals(originalDatos.get("hechosDisponibles"),datos.get("hechosDisponibles"));
        assertEquals(candidatas.size(),datos.get("accionesCandidatas").size());
        assertEquals(f.mapper.readTree(f.mapper.writeValueAsString(ContratoOllamaV2.esquemaConAlcance(normalizado,candidatas))),peticion.get("format"));
        var ramas=peticion.get("format").get("$defs").get("accion").get("oneOf");
        assertEquals(candidatas.size(),ramas.size());
        for(int i=0;i<candidatas.size();i++) {
            var original=originalDatos.get("accionesCandidatas").get(i);
            var reducida=datos.get("accionesCandidatas").get(i);
            for(String campo:List.of("id","version","categoria","descripcion","evidenciasRequeridas","informacionAdicionalPermitida","restricciones","estadoRevision"))
                assertEquals(original.get(campo),reducida.get(campo),campo);
            assertFalse(reducida.has("parametros"));
            var propiedades=ramas.get(i).get("properties");
            assertEquals(candidatas.get(i).definicion().id(),propiedades.get("id").get("enum").get(0).asString());
            assertEquals(candidatas.get(i).definicion().version(),propiedades.get("version").get("enum").get(0).asString());
            var parametros=propiedades.get("parametros").get("properties");
            for(String nombre:candidatas.get(i).definicion().parametros().keySet())
                assertNotNull(parametros.get(nombre),nombre);
        }
        var decisiones=candidatas.stream().map(c->c.definicion().categoria()).distinct()
                .map(c->new Propuesta(c,Decision.ABSTENERSE,null,List.of(),null,null,null,List.of(),MotivoAbstencion.NO_VERIFICABLE)).toList();
        var respuesta=f.mapper.writeValueAsString(new RecomendacionFundamentadaIADTO("2",decisiones));
        f.servidor.expect(requestTo("http://localhost:11434/api/chat")).andExpect(req->{
            String cuerpo=((MockClientHttpRequest)req).getBodyAsString();
            assertTrue(cuerpo.getBytes(StandardCharsets.UTF_8).length<=16384);
            assertEquals(peticion,f.mapper.readTree(cuerpo));
        }).andRespond(withSuccess(f.envoltura(respuesta),MediaType.APPLICATION_JSON));
        assertEquals(ResultadoEvaluacionRecomendacionIADTO.Estado.ACEPTADA_ESTRUCTURALMENTE,
                f.cliente.generarV2ConAlcanceInterno(r,candidatas,Set.of()).evaluacion().estado());
        f.servidor.verify();
    }

    @Test void subconjuntoConContextoCompletoConservaAccionesEvidenciasYEsquema() {
        var f=new OllamaResumen135Test(); f.preparar(); var r=f.h.constructor.construir(68L);
        var candidatas=f.permitidas(r);
        var alcance=new ValidadorRecomendacionFundamentadaIA().construirAlcanceExperimental(r.contexto(),candidatas,Set.of());
        String solicitudEsperada=f.cliente.solicitudV2(r,candidatas,alcance);
        assertTrue(solicitudEsperada.getBytes(StandardCharsets.UTF_8).length<=16384);
        var decisiones=List.of(new Propuesta("Transporte",Decision.ABSTENERSE,null,List.of(),null,null,null,List.of(),MotivoAbstencion.NO_VERIFICABLE));
        var original=f.mapper.writeValueAsString(new RecomendacionFundamentadaIADTO("2",decisiones));
        f.servidor.expect(requestTo("http://localhost:11434/api/chat")).andExpect(req->{
            String cuerpo=((MockClientHttpRequest)req).getBodyAsString(); int bytes=cuerpo.getBytes(StandardCharsets.UTF_8).length;
            System.out.println("Solicitud interna completa compacta: "+bytes+" bytes"); assertTrue(bytes<=16384);
            assertEquals(f.mapper.readTree(solicitudEsperada),f.mapper.readTree(cuerpo));
            var peticion=f.mapper.readTree(cuerpo); var datos=f.mapper.readTree(peticion.get("messages").get(1).get("content").asString());
            var normalizado=ValidadorRecomendacionesIA.contexto(r.contexto());
            assertEquals(f.mapper.readTree(f.mapper.writeValueAsString(ContratoOllamaV2.hechos(normalizado))),datos.get("hechosDisponibles"));
            assertEquals(f.mapper.readTree(f.mapper.writeValueAsString(ContratoOllamaV2.esquemaConAlcance(normalizado,candidatas))),peticion.get("format"));
            var originalDatos=f.mapper.readTree(f.mapper.writeValueAsString(ContratoOllamaV2.datos(normalizado,candidatas)));
            assertEquals(originalDatos.get("accionesCandidatas"),datos.get("accionesCandidatas"));
            assertEquals("12.345",datos.get("hechosDisponibles").get("/energia/consumoKwhDiario").get("valor").get("valor").toString());
            assertEquals("HOGAR",datos.get("hechosDisponibles").get("/energia/consumoKwhDiario").get("ambito").asString());
            assertTrue(datos.get("resumenAmbiental").get("focos").isEmpty());
        }).andRespond(withSuccess(f.envoltura(original),MediaType.APPLICATION_JSON));
        var resultado=f.cliente.generarV2ConAlcanceInterno(r,candidatas,Set.of());
        assertEquals(original,resultado.propuestaJson());
        assertEquals(ResultadoEvaluacionRecomendacionIADTO.Estado.ACEPTADA_ESTRUCTURALMENTE,resultado.evaluacion().estado());
        f.servidor.verify();
    }
}
