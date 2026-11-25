package com.huellago.backend.serviceimpl;
import com.huellago.backend.services.*;
import com.huellago.backend.services.PoliticaAutorizacionEditorialIAService.*;
import java.util.List;
/** Alias de pruebas; no duplica el registro academico ni concede PUBLICABLE. */
final class RegistroEditorialPruebaInternaFixture implements Registro {
    private final AutorizacionAcademicaIA registro;
    RegistroEditorialPruebaInternaFixture() { this(new PresentacionRecomendacionesIAServiceImpl()); }
    RegistroEditorialPruebaInternaFixture(PresentacionRecomendacionesIAService p) { registro=new AutorizacionAcademicaIA(p); }
    public List<Autorizacion> consultar() { return registro.consultar(); }
}
