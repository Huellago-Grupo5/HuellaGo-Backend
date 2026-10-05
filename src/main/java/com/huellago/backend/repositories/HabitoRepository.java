package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.huellago.backend.entities.Habito;

import java.util.Optional;

public interface HabitoRepository extends JpaRepository<Habito, Long> {

    public java.util.List<Habito> findByUsuario_Id(Long usuarioId);

    Optional<Habito> findByIdAndUsuario_Id(Long id, Long usuarioId);

    public Long countByUsuario_Id(Long usuarioId);

    public Long countByUsuario_IdAndCategoria_NombreIgnoreCase(Long usuarioId, String nombre);

    @Query("SELECT COUNT(h) FROM Habito h WHERE h.usuario.id = :usuarioId")
    public Long contarHabitosUsuario(Long usuarioId);

    @Query("SELECT COUNT(h) FROM Habito h WHERE h.usuario.id = :usuarioId AND LOWER(h.categoria.nombre) = LOWER(:nombreCategoria)")
    public Long contarHabitosUsuarioPorCategoria(Long usuarioId, String nombreCategoria);

    @Query(value = "SELECT COUNT(*) FROM habitos WHERE usuario_id = :usuarioId", nativeQuery = true)
    public Long contarHabitosUsuario_SQL(Long usuarioId);
}
