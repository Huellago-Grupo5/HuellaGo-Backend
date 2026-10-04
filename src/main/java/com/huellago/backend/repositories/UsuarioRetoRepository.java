package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.huellago.backend.entities.UsuarioReto;

public interface UsuarioRetoRepository extends JpaRepository<UsuarioReto, Integer> {

    @Query("SELECT COUNT(ur) FROM UsuarioReto ur WHERE ur.usuario.id = :usuarioId AND LOWER(ur.estado) = 'activo'")
    public Long contarRetosActivosUsuario(Long usuarioId);

    @Query(value = "SELECT COUNT(*) FROM usuarios_retos WHERE usuario_id = :usuarioId AND LOWER(estado) = 'activo'", nativeQuery = true)
    public Long contarRetosActivosUsuario_SQL(Long usuarioId);
}
