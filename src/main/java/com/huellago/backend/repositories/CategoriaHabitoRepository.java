package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.huellago.backend.entities.CategoriaHabito;

public interface CategoriaHabitoRepository extends JpaRepository<CategoriaHabito, Long> {

    public CategoriaHabito findByNombreIgnoreCase(String nombre);
}
