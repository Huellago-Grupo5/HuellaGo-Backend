package com.huellago.backend.serviceimpl;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Permisos sinteticos, proxies reales y gestor sin recursos; no escribe PostgreSQL. */
class DestinoUniversitarioLocalIATest {
    final PersistenciaRecomendacionesIAServiceImplTest f=new PersistenciaRecomendacionesIAServiceImplTest();
    @BeforeEach void preparar() { f.preparar(); }
    PersistenciaRecomendacionesIAService local() {
        return f.proxy(new PersistenciaRecomendacionesIAServiceImpl(f.usuarios,f.acciones,f.asociaciones,f.categorias,f.captura,f.em,f.editorial,false,true),PersistenciaRecomendacionesIAService.class);
    }
    @Test void permisoDePruebaNoSePromueveAUniversitarioNiPublicable() {
        for(var d:java.util.List.of(PersistenciaRecomendacionesIAService.Destino.USO_UNIVERSITARIO_LOCAL,PersistenciaRecomendacionesIAService.Destino.PUBLICABLE))
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->f.servicio.referenciaEditorial(f.auth,f.inicial,d));
        f.sinEscrituras();
    }
    @Test void finalidadLocalTieneRecetaDistintaYSigueCerradaAPublicacion() {
        var s=local(); var destino=PersistenciaRecomendacionesIAService.Destino.USO_UNIVERSITARIO_LOCAL;
        var receta=s.referenciaEditorial(f.auth,f.inicial,destino); assertNotEquals(f.receta,receta);
        var a=s.persistir(f.auth,f.inicial,f.seleccion,receta,destino); var b=s.persistir(f.auth,f.inicial,f.seleccion,receta,destino);
        assertEquals(a.resultados().getFirst().asociacionId(),b.resultados().getFirst().asociacionId()); assertTrue(b.resultados().getFirst().reutilizada());
        assertEquals(1,f.guardadas.size()); assertFalse(a.publicable());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->s.referenciaEditorial(f.auth,f.inicial,PersistenciaRecomendacionesIAService.Destino.PUBLICABLE));
    }
    @Test void contextoModificadoSigueRechazadoBajoBloqueo() {
        var s=local(); var d=PersistenciaRecomendacionesIAService.Destino.USO_UNIVERSITARIO_LOCAL; var receta=s.referenciaEditorial(f.auth,f.inicial,d);
        f.datos.get(1).setValor(new java.math.BigDecimal("101"));
        assertEquals("CONTEXTO_MODIFICADO",assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->s.persistir(f.auth,f.inicial,f.seleccion,receta,d)).getReason()); f.sinEscrituras();
    }
}
