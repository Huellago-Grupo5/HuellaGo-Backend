package com.huellago.backend.repositories;

import com.huellago.backend.entities.Reto;
import com.huellago.backend.repositories.projections.CampanaReporteVista;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.query.QueryUtils;
import java.time.LocalDate;

public class RetoReporteRepositoryImpl implements RetoReporteRepository {
    private final EntityManager em;
    public RetoReporteRepositoryImpl(EntityManager em) { this.em = em; }

    @Override
    public Page<CampanaReporteVista> buscarParaReporte(Long institucionId, LocalDate desde, LocalDate hasta,
                                                      Integer campanaId, Pageable pagina) {
        var cb = em.getCriteriaBuilder();
        var seleccion = cb.createTupleQuery();
        var reto = seleccion.from(Reto.class);
        seleccion.multiselect(reto.get("id"), reto.get("titulo"), reto.get("descripcion"),
                reto.get("fechaInicio"), reto.get("fechaFin"), reto.get("activo"));
        seleccion.where(PredicadosReporteInstitucional.crear(cb, reto, institucionId, desde, hasta, campanaId));
        seleccion.orderBy(QueryUtils.toOrders(pagina.getSort(), reto, cb));
        var consulta = em.createQuery(seleccion);
        if (pagina.isPaged()) consulta.setFirstResult(Math.toIntExact(pagina.getOffset())).setMaxResults(pagina.getPageSize());
        var filas = consulta.getResultList().stream().map(this::vista).toList();

        var conteo = cb.createQuery(Long.class);
        var raizConteo = conteo.from(Reto.class);
        conteo.select(cb.count(raizConteo)).where(PredicadosReporteInstitucional.crear(
                cb, raizConteo, institucionId, desde, hasta, campanaId));
        return new PageImpl<>(filas, pagina, em.createQuery(conteo).getSingleResult());
    }

    private CampanaReporteVista vista(Tuple fila) {
        return new Campana(fila.get(0, Integer.class), fila.get(1, String.class), fila.get(2, String.class),
                fila.get(3, LocalDate.class), fila.get(4, LocalDate.class), fila.get(5, Boolean.class));
    }

    private record Campana(Integer getId, String getTitulo, String getDescripcion, LocalDate getFechaInicio,
                           LocalDate getFechaFin, Boolean getActivo) implements CampanaReporteVista { }
}
