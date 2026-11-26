package com.huellago.backend.repositories;

import com.huellago.backend.entities.InvitacionInstitucional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface InvitacionInstitucionalRepository extends JpaRepository<InvitacionInstitucional, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InvitacionInstitucional> findByCodigoHash(String codigoHash);
}
