package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.huellago.backend.entities.Habito;

public interface HabitoRepository extends JpaRepository<Habito, Long> {
}
