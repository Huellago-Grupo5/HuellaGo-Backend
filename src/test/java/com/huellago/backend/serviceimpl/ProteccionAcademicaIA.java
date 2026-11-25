package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Decision PASO127: solo el usuario sintetico 91, correo del fixture123 y Auto. Composicion academica explicita, sin registro automatico. */
final class ProteccionAcademicaIA {
    static final long USUARIO=91L;
    static final String CORREO="ia123@example.invalid";
    static final String FINALIDAD="DEMO_ACADEMICA_LOCAL", ACCION="TR_PLANIFICAR_DESPLAZAMIENTOS";
    static void identidad(Authentication a) {
        if(a==null || !a.isAuthenticated() || !(a.getPrincipal() instanceof UserSecurity p)
                || p.getUser()==null || !Long.valueOf(USUARIO).equals(p.getUser().getId())
                || !CORREO.equals(p.getUser().getCorreo()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"USUARIO_NO_AUTORIZADO_PARA_DEMO");
    }
    static void auto(ContextoRecomendacionesIADTO c) {
        if(c==null || c.transporte()==null || !"Auto".equals(c.transporte().medio()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"MEDIO_NO_AUTORIZADO_PARA_DEMO");
        ValidadorRecomendacionesIA.contexto(c);
    }
    static CapturaContextoAmbientalIAService captura(CapturaContextoAmbientalIAService real) {
        return new CapturaContextoAmbientalIAService() {
            private void propia(Authentication a,CapturaContextoAmbientalIADTO c) {
                identidad(a);
                if(c==null || !Long.valueOf(USUARIO).equals(c.usuarioId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"CAPTURA_AJENA");
                auto(c.contexto().contexto());
            }
            public CapturaContextoAmbientalIADTO capturar(Authentication a) {
                identidad(a); var c=real.capturar(a); propia(a,c); return c;
            }
            public Revalidacion revalidar(Authentication a,CapturaContextoAmbientalIADTO c) { propia(a,c); return real.revalidar(a,c); }
            public Revalidacion comprobarVigenciaEnTransaccion(Authentication a,CapturaContextoAmbientalIADTO c) {
                propia(a,c); return real.comprobarVigenciaEnTransaccion(a,c);
            }
        };
    }
    static PoliticaAutorizacionEditorialIAService editorial(PoliticaAutorizacionEditorialIAService real) {
        return new PoliticaAutorizacionEditorialIAService() {
            public Resultado evaluar(ContextoRecomendacionesIADTO c,Set<AccionAmbientalAdmisible.Referencia> realizadas) {
                auto(c); var r=real.evaluar(c,realizadas);
                if(r.elegibles().stream().anyMatch(a->!ACCION.equals(a.definicion().id()) || !"1".equals(a.definicion().version())))
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,"REGISTRO_DEMO_INVALIDO");
                return r;
            }
            public String huellaDefinicion(DefinicionPresentacionIADTO d) { return real.huellaDefinicion(d); }
        };
    }
}
