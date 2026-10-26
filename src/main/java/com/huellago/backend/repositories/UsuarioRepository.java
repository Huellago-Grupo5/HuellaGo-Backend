package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.huellago.backend.entities.Usuario;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query("select u from Usuario u where lower(trim(u.correo)) = :correo")
    Usuario findByCorreoNormalizado(@Param("correo") String correo);

    @Query("select count(u) > 0 from Usuario u where lower(trim(u.correo)) = :correo")
    boolean existeCorreoNormalizado(@Param("correo") String correo);

    // Serializa las solicitudes de generación del mismo usuario sin cambiar el esquema.
    @Query(value = "SELECT id FROM usuarios WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> bloquearParaRecomendaciones(@Param("id") Long id);

    public boolean existsByCorreo(String correo);

    @Query("select u from Usuario u where lower(trim(u.correo)) = lower(trim(:correo))")
    public Usuario findByCorreo(@Param("correo") String correo);

    public boolean existsByCorreoAndIdNot(String correo, Long id);

    @Query("select count(u) > 0 from Usuario u where lower(trim(u.correo)) = lower(trim(:correo)) and u.id <> :id")
    public boolean existsByCorreoIgnoreCaseAndIdNot(@Param("correo") String correo, @Param("id") Long id);

}
