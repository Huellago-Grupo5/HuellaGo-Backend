package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.huellago.backend.entities.Reto;
import com.huellago.backend.entities.TipoReto;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetoRepository extends JpaRepository<Reto, Integer> {
    List<Reto> findByActivoTrueOrderByFechaInicioAsc();
    List<Reto> findByTipo(TipoReto tipo);
    boolean existsByRecomendacionOrigen_Id(Integer usuarioRecomendacionId);

    @Query(value = "SELECT id FROM retos WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Integer> bloquearParaAceptacion(@Param("id") Integer id);
}
