package com.huellago.backend.serviceimpl;
/** Alias para los tests anteriores: proteccion del destino en una sola implementacion. */
final class FuenteHttpIA123 extends FuenteAcademicaIA {
    FuenteHttpIA123(EntornoHttpIA123 e) { super(e.claveJdbc()); }
    FuenteHttpIA123(EntornoHttpIA123 e,Abrir a) { super(e.claveJdbc(),a); }
}
