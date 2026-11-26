package com.huellago.backend.repositories;

import com.huellago.backend.entities.Reto;
import com.huellago.backend.entities.UsuarioReto;
import com.huellago.backend.repositories.projections.TotalesParticipacionVista;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Join;
import java.time.LocalDate;

public class UsuarioRetoReporteRepositoryImpl implements UsuarioRetoReporteRepository {
    private final EntityManager em;
    public UsuarioRetoReporteRepositoryImpl(EntityManager em) { this.em = em; }

    @Override
    public TotalesParticipacionVista totalesParaReporte(Long institucionId, LocalDate desde,
                                                       LocalDate hasta, Integer campanaId) {
        var cb = em.getCriteriaBuilder();
        var consulta = cb.createTupleQuery();
        var participacion = consulta.from(UsuarioReto.class);
        Join<UsuarioReto, Reto> reto = participacion.join("reto");
        consulta.multiselect(cb.count(participacion), cb.countDistinct(participacion.get("usuario").get("id")))
                .where(PredicadosReporteInstitucional.crear(cb, reto, institucionId, desde, hasta, campanaId));
        var fila = em.createQuery(consulta).getSingleResult();
        return new Totales(fila.get(0, Long.class), fila.get(1, Long.class));
    }

    private record Totales(Long getTotal, Long getUsuarios) implements TotalesParticipacionVista { }
}
