package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.huellago.backend.entities.Usuario;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    interface FilaRanking {
        Long getId();
        String getNombres();
        Integer getEcoPuntos();
        Integer getNivel();
    }

    @Query("select u.id as id, u.nombres as nombres, u.ecoPuntos as ecoPuntos, "
            + "u.nivel as nivel from Usuario u where u.visibilidadComunidad = true "
            + "and u.ecoPuntos is not null and u.nivel is not null "
            + "order by u.ecoPuntos desc, u.id asc")
    List<FilaRanking> listarRankingComunitario();

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
