package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.huellago.backend.entities.UsuarioInsignia;

import java.util.List;

public interface UsuarioInsigniaRepository extends JpaRepository<UsuarioInsignia, Integer> {
    boolean existsByUsuario_IdAndInsignia_Id(Long usuarioId, Integer insigniaId);

    List<UsuarioInsignia> findByUsuario_IdOrderByFechaObtencionDesc(Long usuarioId);
}
