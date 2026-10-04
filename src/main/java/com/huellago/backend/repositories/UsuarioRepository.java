package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import com.huellago.backend.entities.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    boolean existsByCorreo(String correo);

    Usuario findByCorreo(String correo);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE usuarios SET visibilidad_comunidad = :visibilidad, fecha_actualizacion = NOW() WHERE id = :id", nativeQuery = true)
    int actualizarVisibilidadNativa(@Param("id") Long id, @Param("visibilidad") Boolean visibilidad);
}