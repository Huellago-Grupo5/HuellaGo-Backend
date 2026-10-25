package com.huellago.backend.repositories;

import com.huellago.backend.entities.PublicacionComunitaria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicacionComunitariaRepository extends JpaRepository<PublicacionComunitaria, Long> {
    boolean existsByAutor_IdAndInsignia_Id(Long autorId, Integer insigniaId);
    boolean existsByParticipacion_Id(Integer participacionId);
}
