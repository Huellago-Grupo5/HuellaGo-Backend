package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.huellago.backend.entities.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {
}
