package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.huellago.backend.entities.UsuarioReto;

import java.util.List;
import java.util.Optional;

public interface UsuarioRetoRepository extends JpaRepository<UsuarioReto, Integer> {

    boolean existsByUsuario_IdAndReto_Id(Long usuarioId, Integer retoId);

    List<UsuarioReto> findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaAceptacionDesc(
            Long usuarioId, String estado);

    Optional<UsuarioReto> findByIdAndUsuario_Id(Integer id, Long usuarioId);

    @Query("SELECT COUNT(ur) FROM UsuarioReto ur WHERE ur.usuario.id = :usuarioId AND LOWER(ur.estado) = 'activo'")
    public Long contarRetosActivosUsuario(Long usuarioId);

    @Query(value = "SELECT COUNT(*) FROM usuarios_retos WHERE usuario_id = :usuarioId AND LOWER(estado) = 'activo'", nativeQuery = true)
    public Long contarRetosActivosUsuario_SQL(Long usuarioId);
}
