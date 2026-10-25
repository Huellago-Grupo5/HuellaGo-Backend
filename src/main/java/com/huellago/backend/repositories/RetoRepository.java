package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.huellago.backend.entities.Reto;
import com.huellago.backend.entities.TipoReto;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;

public interface RetoRepository extends JpaRepository<Reto, Integer> {
    List<Reto> findByActivoTrueOrderByFechaInicioAsc();
    List<Reto> findByTipo(TipoReto tipo);
    boolean existsByRecomendacionOrigen_Id(Integer usuarioRecomendacionId);
    boolean existsByInstitucion_IdAndTituloIgnoreCaseAndFechaInicioAndFechaFin(
            Long institucionId, String titulo, LocalDate fechaInicio, LocalDate fechaFin);
    Page<Reto> findByInstitucion_Id(Long institucionId, Pageable pagina);
    Optional<Reto> findByIdAndInstitucion_Id(Integer id, Long institucionId);

    @Query("select r from Reto r where r.institucion is not null and exists "
            + "(select ui.id from UsuarioInstitucion ui where ui.usuario.id = :usuarioId "
            + "and ui.institucion.id = r.institucion.id)")
    Page<Reto> buscarCampanasVinculadas(@Param("usuarioId") Long usuarioId, Pageable pagina);

    @Query(value = "SELECT id FROM retos WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Integer> bloquearParaAceptacion(@Param("id") Integer id);
}
