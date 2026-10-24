package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.huellago.backend.entities.Sesion;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface SesionRepository extends JpaRepository<Sesion, Long> {
    Optional<Sesion> findByToken(String token);

    boolean existsByTokenAndActivaTrueAndFechaExpiracionAfter(String token, LocalDateTime fecha);

    List<Sesion> findByUsuario_IdAndActivaTrue(Long usuarioId);
}
