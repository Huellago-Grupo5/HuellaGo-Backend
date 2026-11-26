package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.huellago.backend.entities.CategoriaHabito;
import java.util.Optional;

public interface CategoriaHabitoRepository extends JpaRepository<CategoriaHabito, Long> {

    @Query(
            value = "SELECT id FROM categorias_habito WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> bloquearParaRecomendaciones(@Param("id") Long id);

    public CategoriaHabito findByNombreIgnoreCase(String nombre);
}
