package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.huellago.backend.entities.UsuarioRecomendacionIA;

public interface UsuarioRecomendacionIARepository extends JpaRepository<UsuarioRecomendacionIA, Integer> {

    @Query("SELECT COUNT(ur) FROM UsuarioRecomendacionIA ur WHERE ur.usuario.id = :usuarioId AND LOWER(ur.estado) = 'pendiente'")
    public Long contarRecomendacionesPendientesUsuario(Long usuarioId);

    @Query(value = "SELECT COUNT(*) FROM usuarios_recomendaciones_ia WHERE usuario_id = :usuarioId AND LOWER(estado) = 'completado'", nativeQuery = true)
    public Long contarRecomendacionesCompletadasUsuario_SQL(Long usuarioId);
}
