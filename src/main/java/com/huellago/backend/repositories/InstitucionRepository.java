package com.huellago.backend.repositories;

import com.huellago.backend.entities.Institucion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitucionRepository extends JpaRepository<Institucion, Long> {
    boolean existsByPaisAndIdentificadorFiscal(String pais, String identificadorFiscal);
}
