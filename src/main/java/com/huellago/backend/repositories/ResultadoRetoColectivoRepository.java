package com.huellago.backend.repositories;

import com.huellago.backend.entities.ResultadoRetoColectivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResultadoRetoColectivoRepository extends JpaRepository<ResultadoRetoColectivo, Long> {
    Optional<ResultadoRetoColectivo> findByReto_Id(Integer retoId);
    boolean existsByReto_Id(Integer retoId);
}
