package com.huellago.backend.repositories;

import com.huellago.backend.entities.RecuperacionContrasena;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface RecuperacionContrasenaRepository extends JpaRepository<RecuperacionContrasena, Long> {
    @Query("select r.usuario.id from RecuperacionContrasena r where r.tokenHash = :hash")
    Optional<Long> buscarUsuario(@Param("hash") String hash);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecuperacionContrasena r where r.tokenHash = :hash")
    Optional<RecuperacionContrasena> bloquear(@Param("hash") String hash);
    @Modifying(flushAutomatically = true)
    @Query("delete from RecuperacionContrasena r where r.usuario.id = :usuarioId")
    void eliminarDelUsuario(@Param("usuarioId") Long usuarioId);
}
