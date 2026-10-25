package com.huellago.backend.repositories;

import com.huellago.backend.entities.Reto;
import com.huellago.backend.entities.TipoReto;
import jakarta.persistence.criteria.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** El mismo filtro para selección, paginación y totales; nunca enlaza parámetros opcionales nulos. */
final class PredicadosReporteInstitucional {
    private PredicadosReporteInstitucional() { }

    static Predicate[] crear(CriteriaBuilder cb, From<?, Reto> reto, Long institucionId,
                             LocalDate desde, LocalDate hasta, Integer campanaId) {
        List<Predicate> filtros = new ArrayList<>();
        filtros.add(cb.equal(reto.get("institucion").get("id"), institucionId));
        filtros.add(cb.equal(reto.get("tipo"), TipoReto.COLECTIVO));
        if (desde != null) filtros.add(cb.greaterThanOrEqualTo(reto.<LocalDate>get("fechaFin"), desde));
        if (hasta != null) filtros.add(cb.lessThanOrEqualTo(reto.<LocalDate>get("fechaInicio"), hasta));
        if (campanaId != null) filtros.add(cb.equal(reto.get("id"), campanaId));
        return filtros.toArray(Predicate[]::new);
    }
}
