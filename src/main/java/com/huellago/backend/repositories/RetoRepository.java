package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.huellago.backend.entities.Reto;

import java.util.List;

public interface RetoRepository extends JpaRepository<Reto, Integer> {
    List<Reto> findByActivoTrueOrderByFechaInicioAsc();
}
