package com.huellago.backend.serviceimpl;
import com.huellago.backend.services.GeneracionManualIAService;
import com.huellago.backend.repositories.UsuarioRecomendacionIARepository;
/** Compatibilidad del lanzador anterior; reutiliza el adaptador academico de src/main. */
final class EntradaDemoIA127 extends EntradaAcademicaIA {
    EntradaDemoIA127(GeneracionManualIAService g, UsuarioRecomendacionIARepository a,
            org.springframework.transaction.PlatformTransactionManager tm) { super(g,a,tm); }
}
