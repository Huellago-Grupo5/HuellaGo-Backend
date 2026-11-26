package com.huellago.backend.services;

import com.huellago.backend.dtos.AmbienteDTO;

public interface AmbienteService {
    AmbienteDTO.CalidadAire calidadAire(double latitud, double longitud);
    AmbienteDTO.PuntosSostenibles puntosSostenibles(double latitud, double longitud,
                                                  int radioMetros, AmbienteDTO.TipoPunto tipo);
}
