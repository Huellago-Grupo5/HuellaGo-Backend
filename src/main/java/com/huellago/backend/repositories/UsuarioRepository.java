package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.huellago.backend.entities.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
}
