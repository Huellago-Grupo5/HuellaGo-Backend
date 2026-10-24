package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.huellago.backend.entities.Usuario;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Serializa las solicitudes de generación del mismo usuario sin cambiar el esquema.
    @Query(value = "SELECT id FROM usuarios WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> bloquearParaRecomendaciones(@Param("id") Long id);

    public boolean existsByCorreo(String correo);

    public Usuario findByCorreo(String correo);

    public boolean existsByCorreoAndIdNot(String correo, Long id);

    public boolean existsByCorreoIgnoreCaseAndIdNot(String correo, Long id);

}
