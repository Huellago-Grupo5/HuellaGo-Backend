package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.huellago.backend.entities.RecomendacionIA;
import java.util.Optional;

public interface RecomendacionIARepository extends JpaRepository<RecomendacionIA, Integer> {
    // Lista deliberada: todavía no existe una unicidad revisada para esta identidad.
    java.util.List<RecomendacionIA> findByAccionIdAndVersionAccionOrderByIdAsc(
            String accionId, String versionAccion);

    Optional<RecomendacionIA> findFirstByCategoria_IdAndTituloIgnoreCaseOrderByIdAsc(
            Long categoriaId, String titulo);
}
