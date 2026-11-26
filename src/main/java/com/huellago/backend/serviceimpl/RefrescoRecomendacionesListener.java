package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Usuario;
import com.huellago.backend.events.InformacionAmbientalActualizada;
import com.huellago.backend.repositories.UsuarioRepository;
import com.huellago.backend.services.RecomendacionService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class RefrescoRecomendacionesListener {
    private final RecomendacionService recomendaciones;
    private final UsuarioRepository usuarios;
    private final EntityManager entityManager;

    public RefrescoRecomendacionesListener(RecomendacionService recomendaciones, UsuarioRepository usuarios,
                                           EntityManager entityManager) {
        this.recomendaciones = recomendaciones;
        this.usuarios = usuarios;
        this.entityManager = entityManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void refrescar(InformacionAmbientalActualizada evento) {
        // Vuelca los cambios del flujo original antes de descartar su caché de entidades.
        // Los bloqueos SQL siguen vigentes hasta el commit de esta misma transacción.
        entityManager.flush();
        entityManager.clear();
        // En cierres colectivos se bloquean TODOS los usuarios antes del catálogo compartido.
        // Evita invertir el orden usuario -> categoría entre evaluaciones concurrentes.
        for (Long id : evento.usuarioIds()) {
            usuarios.bloquearParaRecomendaciones(id).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        }
        for (Long id : evento.usuarioIds()) {
            Usuario usuario = new Usuario();
            usuario.setId(id);
            recomendaciones.generar(usuario);
        }
    }
}
