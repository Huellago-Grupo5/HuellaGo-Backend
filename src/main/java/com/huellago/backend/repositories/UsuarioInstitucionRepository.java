package com.huellago.backend.repositories;

import com.huellago.backend.entities.RolInstitucional;
import com.huellago.backend.entities.UsuarioInstitucion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioInstitucionRepository extends JpaRepository<UsuarioInstitucion, Long> {
    boolean existsByUsuario_IdAndInstitucion_PaisAndInstitucion_IdentificadorFiscal(
            Long usuarioId, String pais, String identificadorFiscal);
    boolean existsByUsuario_IdAndInstitucion_IdAndRol(
            Long usuarioId, Long institucionId, RolInstitucional rol);
}
