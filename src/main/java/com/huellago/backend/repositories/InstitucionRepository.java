package com.huellago.backend.repositories;

import com.huellago.backend.entities.Institucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface InstitucionRepository extends JpaRepository<Institucion, Long> {
    @Query(value = "SELECT id FROM instituciones WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> bloquearCampanas(@Param("id") Long id);
    boolean existsByPaisAndIdentificadorFiscal(String pais, String identificadorFiscal);
}
