package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.huellago.backend.entities.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {
    boolean existsByUsuario_IdAndClaveEvento(Long usuarioId, String claveEvento);
    java.util.List<Notificacion> findByUsuario_IdOrderByFechaCreacionDescIdDesc(Long usuarioId);
    java.util.Optional<Notificacion> findByIdAndUsuario_Id(Integer id, Long usuarioId);
    @org.springframework.data.jpa.repository.Query("select count(n) from Notificacion n where n.usuario.id = :usuarioId and (n.leida = false or n.leida is null)")
    long contarNoLeidas(@org.springframework.data.repository.query.Param("usuarioId") Long usuarioId);
}
