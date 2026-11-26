package com.huellago.backend.dtos;

import com.huellago.backend.services.CatalogoAccionesAmbientalesService.Candidata;
import java.util.List;
import java.util.Set;

/** Alcance experimental sintético. Su construcción no acredita legitimidad: se revalida contra el catálogo. */
public record AlcanceSeleccionIADTO(Set<String> completas, List<Candidata> canonicasCompatibles,
        List<Candidata> permitidas, Set<String> seleccionables, Set<String> excluidasSinPermitidas,
        Set<String> incompletas) {
    public AlcanceSeleccionIADTO {
        completas = Set.copyOf(completas);
        canonicasCompatibles = List.copyOf(canonicasCompatibles);
        permitidas = List.copyOf(permitidas);
        seleccionables = Set.copyOf(seleccionables);
        excluidasSinPermitidas = Set.copyOf(excluidasSinPermitidas);
        incompletas = Set.copyOf(incompletas);
    }
}
