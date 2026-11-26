package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Usuario;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.huellago.backend.services.PersistenciaRecomendacionesIAService.Destino.*;

/** Solo decisiones/contextos SINTETICOS, proxies sin recursos y repositorios Mockito.
 * No simula que exista una aprobacion editorial productiva. */
class FinalidadGeneracionIA125BTest {
    private static final String CIERRE="AUTORIZACION_PUBLICABLE_NO_DISPONIBLE";
    private void cerrado(org.junit.jupiter.api.function.Executable accion) {
        var e=assertThrows(ResponseStatusException.class,accion);
        assertEquals(HttpStatus.FORBIDDEN,e.getStatusCode()); assertEquals(CIERRE,e.getReason());
    }
    @Test void coordinadorPublicableCerradoAntesDeLeerOInferir() {
        var captura=mock(CapturaContextoAmbientalIAService.class); var editorial=mock(PoliticaAutorizacionEditorialIAService.class);
        var orquestador=mock(OrquestadorRecomendacionesIAService.class); var escritor=mock(PersistenciaRecomendacionesIAService.class);
        var usuario=new Usuario(); usuario.setId(77L);
        var auth=new UsernamePasswordAuthenticationToken(new UserSecurity(usuario),null,List.of());
        var servicio=new GeneracionManualIAServiceImpl(captura,editorial,orquestador,escritor);
        cerrado(()->servicio.generarParaPublicacionAutorizada(auth));
        verifyNoInteractions(captura,editorial,orquestador,escritor);
    }
    @Test void nuevaOperacionExigeAutenticacion() {
        var f=new GeneracionManualIAServiceImplTest(); f.preparar();
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ResponseStatusException.class,
                ()->f.servicio.generarParaPublicacionAutorizada(null)).getStatusCode());
        verifyNoInteractions(f.capturas,f.selector,f.escritor);
    }
    @Test void noSePermiteTransaccionActivaNiEnElPuertoCerrado() {
        var f=new GeneracionManualIAServiceImplTest(); f.preparar();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class,()->f.servicio.generarParaPublicacionAutorizada(f.auth)); }
        finally { TransactionSynchronizationManager.clear(); }
        verifyNoInteractions(f.capturas,f.selector,f.escritor);
    }
    @ParameterizedTest @ValueSource(strings={"ninguna","fixture114","sha","version","parcial1","parcial2","bandera"})
    void permisosInternosNuncaHabilitanPublicacion(String caso) {
        var f=new PersistenciaRecomendacionesIAServiceImplTest(); f.preparar();
        f.permisos.clear(); f.permisos.addAll(new RegistroEditorialPruebaInternaFixture().consultar());
        switch(caso) {
            case "ninguna" -> f.permisos.clear();
            case "sha", "version" -> {
                var a=f.permisos.getFirst();
                f.permisos.set(0,new PoliticaAutorizacionEditorialIAService.Autorizacion(a.accionId(),a.versionAccion(),a.enfoque(),
                        caso.equals("version")?"99":a.versionPresentacion(),caso.equals("sha")?"0".repeat(64):a.sha256(),a.camposContextoPermitidos()));
            }
            case "parcial1" -> f.permisos.subList(1,3).clear();
            case "parcial2" -> f.permisos.removeLast();
        }
        var orquestador=mock(OrquestadorRecomendacionesIAService.class);
        var generacion=new GeneracionManualIAServiceImpl(f.captura,f.editorial,orquestador,f.servicio);
        String propiedad="huellago.recomendaciones.ia.enabled",anterior=System.getProperty(propiedad);
        if(caso.equals("bandera")) System.setProperty(propiedad,"true");
        try {
            cerrado(()->generacion.generarParaPublicacionAutorizada(f.auth));
            // El escritor de este fixture tiene pruebaInternaHabilitada=true: tampoco permite PUBLICABLE.
            cerrado(()->f.servicio.referenciaEditorial(f.auth,f.inicial,PUBLICABLE));
            cerrado(()->f.servicio.persistir(f.auth,f.inicial,f.seleccion,f.receta,PUBLICABLE));
        } finally {
            if(anterior==null) System.clearProperty(propiedad); else System.setProperty(propiedad,anterior);
        }
        verifyNoInteractions(orquestador,f.usuarios,f.habitos,f.asociaciones,f.em);
        f.sinEscrituras();
    }
    @Test void constructorProductivoCerradoEnAmbasFinalidades() {
        var f=new PersistenciaRecomendacionesIAServiceImplTest(); f.preparar();
        var escritor=new PersistenciaRecomendacionesIAServiceImpl(f.usuarios,f.acciones,f.asociaciones,f.categorias,f.captura,f.em);
        cerrado(()->escritor.referenciaEditorial(f.auth,f.inicial,PUBLICABLE));
        assertEquals("DESTINO_NO_AUTORIZADO",assertThrows(ResponseStatusException.class,
                ()->escritor.referenciaEditorial(f.auth,f.inicial,PRUEBA_INTERNA)).getReason());
        verifyNoInteractions(f.usuarios,f.habitos,f.asociaciones,f.em);
    }
    @Test void referenciaAnteriorEquivaleALaFinalidadInternaExplicita() {
        var f=new PersistenciaRecomendacionesIAServiceImplTest(); f.preparar();
        assertEquals(f.receta,f.servicio.referenciaEditorial(f.auth,f.inicial));
        assertEquals(f.receta,f.servicio.referenciaEditorial(f.auth,f.inicial,PRUEBA_INTERNA));
        var a=f.guardar(); var b=f.guardar();
        assertEquals(a.resultados().getFirst().asociacionId(),b.resultados().getFirst().asociacionId());
        assertEquals(a.resultados().getFirst().fechaGeneracion(),b.resultados().getFirst().fechaGeneracion());
        assertTrue(b.resultados().getFirst().reutilizada()); assertFalse(a.publicable());
        assertEquals("BORRADOR",a.resultados().getFirst().estadoEditorial());
        assertEquals("PENDIENTE_REVISION",a.resultados().getFirst().revisionHumana());
        verify(f.usuarios,times(2)).bloquearParaRecomendaciones(77L);
        verify(f.asociaciones,times(1)).saveAndFlush(any());
    }
    @ParameterizedTest @ValueSource(strings={"sha","version","parcial1","parcial2"})
    void integridadEditorialSigueExigidaParaPruebaInterna(String caso) {
        var f=new PersistenciaRecomendacionesIAServiceImplTest(); f.preparar();
        if(caso.startsWith("parcial")) f.permisos.subList(caso.equals("parcial1")?1:2,3).clear();
        else {
            var a=f.permisos.getFirst();
            f.permisos.set(0,new PoliticaAutorizacionEditorialIAService.Autorizacion(a.accionId(),a.versionAccion(),a.enfoque(),
                    caso.equals("version")?"99":a.versionPresentacion(),caso.equals("sha")?"0".repeat(64):a.sha256(),a.camposContextoPermitidos()));
        }
        var e=assertThrows(ResponseStatusException.class,()->f.servicio.referenciaEditorial(f.auth,f.inicial,PRUEBA_INTERNA));
        assertEquals("SIN_AUTORIZACION_EDITORIAL",e.getReason()); f.sinEscrituras();
    }
    @Test void finalidadNulaNoSeConvierteEnPrueba() {
        var f=new PersistenciaRecomendacionesIAServiceImplTest(); f.preparar();
        assertEquals("DESTINO_NO_AUTORIZADO",assertThrows(ResponseStatusException.class,
                ()->f.servicio.referenciaEditorial(f.auth,f.inicial,null)).getReason()); f.sinEscrituras();
    }
    @Test void contratosProductivosNoImportanFixturesNiRegistranRutas() throws Exception {
        for(String relativa:List.of("services/GeneracionManualIAService.java","serviceimpl/GeneracionManualIAServiceImpl.java",
                "services/PersistenciaRecomendacionesIAService.java","serviceimpl/PersistenciaRecomendacionesIAServiceImpl.java")) {
            String fuente=Files.readString(Path.of("src/main/java/com/huellago/backend/"+relativa));
            assertFalse(fuente.contains("RegistroEditorialPruebaInternaFixture")); assertFalse(fuente.contains("@PostMapping"));
            assertFalse(fuente.contains("System.getenv")); assertFalse(fuente.contains("System.getProperty"));
        }
    }
}
