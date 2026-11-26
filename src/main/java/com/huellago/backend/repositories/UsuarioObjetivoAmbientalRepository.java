package com.huellago.backend.repositories;
import com.huellago.backend.entities.UsuarioObjetivoAmbiental;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface UsuarioObjetivoAmbientalRepository extends JpaRepository<UsuarioObjetivoAmbiental, Long> {
    List<UsuarioObjetivoAmbiental> findByUsuarioId(Long usuarioId);
    void deleteByUsuarioId(Long usuarioId);
}
