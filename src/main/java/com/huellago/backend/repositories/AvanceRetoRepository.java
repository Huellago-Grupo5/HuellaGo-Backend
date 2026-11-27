package com.huellago.backend.repositories;

import com.huellago.backend.entities.AvanceReto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvanceRetoRepository extends JpaRepository<AvanceReto, Long> {
    Optional<AvanceReto> findByParticipacion_IdAndClaveSolicitud(Integer participacionId, UUID claveSolicitud);
    List<AvanceReto> findByParticipacion_IdAndParticipacion_Usuario_IdOrderByRegistradoEnDescIdDesc(
            Integer participacionId, Long usuarioId);
}
