package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.huellago.backend.entities.HuellaCarbono;

import java.util.List;
import java.time.LocalDateTime;

public interface HuellaCarbonoRepository extends JpaRepository<HuellaCarbono, Long> {

    public HuellaCarbono findTopByUsuario_IdOrderByFechaCalculoDesc(Long usuarioId);

    public List<HuellaCarbono> findByUsuario_IdOrderByFechaCalculoDesc(Long usuarioId);

    public HuellaCarbono findTopByUsuario_IdAndFechaCalculoBetweenOrderByFechaCalculoDesc(
            Long usuarioId, LocalDateTime inicio, LocalDateTime fin);

    @Query("SELECT COUNT(h) FROM HuellaCarbono h WHERE h.usuario.id = :usuarioId")
    public Long contarHuellasUsuario(Long usuarioId);

    @Query(value = "SELECT co2_total FROM huellas_carbono WHERE usuario_id = :usuarioId ORDER BY fecha_calculo DESC LIMIT 1", nativeQuery = true)
    public java.math.BigDecimal buscarHuellaActual_SQL(Long usuarioId);
}
