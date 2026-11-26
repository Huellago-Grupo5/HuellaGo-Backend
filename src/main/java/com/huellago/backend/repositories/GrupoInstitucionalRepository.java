package com.huellago.backend.repositories;

import com.huellago.backend.entities.GrupoInstitucional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface GrupoInstitucionalRepository extends JpaRepository<GrupoInstitucional, Long> {
    List<GrupoInstitucional> findByInstitucion_IdOrderByIdAsc(Long institucionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GrupoInstitucional> findByIdAndInstitucion_Id(Long id, Long institucionId);
    boolean existsByInstitucion_IdAndNombreIgnoreCase(Long institucionId, String nombre);
    boolean existsByInstitucion_IdAndNombreIgnoreCaseAndIdNot(Long institucionId, String nombre, Long id);
}
