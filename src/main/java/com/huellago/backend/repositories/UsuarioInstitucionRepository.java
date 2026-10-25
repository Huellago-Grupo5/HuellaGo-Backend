package com.huellago.backend.repositories;

import com.huellago.backend.entities.RolInstitucional;
import com.huellago.backend.entities.UsuarioInstitucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UsuarioInstitucionRepository extends JpaRepository<UsuarioInstitucion, Long> {
    boolean existsByUsuario_IdAndInstitucion_Id(Long usuarioId, Long institucionId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select ui from UsuarioInstitucion ui where ui.usuario.id = :usuarioId and ui.institucion.id = :institucionId")
    Optional<UsuarioInstitucion> bloquearVinculo(@Param("usuarioId") Long usuarioId,
                                                @Param("institucionId") Long institucionId);
    boolean existsByUsuario_IdAndInstitucion_PaisAndInstitucion_IdentificadorFiscal(
            Long usuarioId, String pais, String identificadorFiscal);
    boolean existsByUsuario_IdAndInstitucion_IdAndRol(
            Long usuarioId, Long institucionId, RolInstitucional rol);
}
