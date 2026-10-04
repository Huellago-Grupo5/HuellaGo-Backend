package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.huellago.backend.entities.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    public boolean existsByCorreo(String correo);

    public Usuario findByCorreo(String correo);

    public boolean existsByCorreoAndIdNot(String correo, Long id);

}
