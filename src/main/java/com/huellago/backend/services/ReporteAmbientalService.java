package com.huellago.backend.services;

import com.huellago.backend.entities.Usuario;

public interface ReporteAmbientalService {
    byte[] generarReporte(Usuario usuario);
}
