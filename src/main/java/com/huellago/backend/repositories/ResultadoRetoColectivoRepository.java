package com.huellago.backend.repositories;

import com.huellago.backend.entities.ResultadoRetoColectivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.huellago.backend.repositories.projections.ResultadoReporteVista;

public interface ResultadoRetoColectivoRepository extends JpaRepository<ResultadoRetoColectivo, Long> {
    @Query("select rc.reto.id as retoId, rc.participantes as participantes, rc.progresoFinal as progresoFinal, "
            + "rc.estado as estado, rc.fechaCierre as fechaCierre from ResultadoRetoColectivo rc where rc.reto.id in :ids")
    List<ResultadoReporteVista> buscarParaReporte(@Param("ids") List<Integer> ids);
    Optional<ResultadoRetoColectivo> findByReto_Id(Integer retoId);
    boolean existsByReto_Id(Integer retoId);
}
