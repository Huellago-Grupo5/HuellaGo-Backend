package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.NotificacionRespuestaDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.NotificacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@Tag(name = "Notificaciones", description = "Notificaciones internas propias, sujetas a las preferencias del usuario.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/usuarios/me/notificaciones")
public class NotificacionController {
    private final NotificacionService notificaciones;

    public NotificacionController(NotificacionService notificaciones) { this.notificaciones = notificaciones; }

    @Operation(summary = "Listar mis notificaciones", description = "Listar mis notificaciones. Notificaciones internas propias, sujetas a las preferencias del usuario.")
    @GetMapping
    public List<NotificacionRespuestaDTO> listar(@AuthenticationPrincipal UserSecurity autenticado) {
        return notificaciones.listar(autenticado.getUser().getId());
    }

    @Operation(summary = "Contar mis notificaciones no leídas", description = "Contar mis notificaciones no leídas. Notificaciones internas propias, sujetas a las preferencias del usuario.")
    @GetMapping("/no-leidas")
    public Map<String, Long> noLeidas(@AuthenticationPrincipal UserSecurity autenticado) {
        return Map.of("cantidad", notificaciones.contarNoLeidas(autenticado.getUser().getId()));
    }

    @Operation(summary = "Marcar notificación propia como leída", description = "Marcar notificación propia como leída. Notificaciones internas propias, sujetas a las preferencias del usuario.")
    @PutMapping("/{id}/leer")
    public NotificacionRespuestaDTO leer(@PathVariable Integer id, @AuthenticationPrincipal UserSecurity autenticado) {
        return notificaciones.marcarLeida(autenticado.getUser().getId(), id);
    }

    @Operation(summary = "Marcar todas mis notificaciones como leídas", description = "Marcar todas mis notificaciones como leídas. Notificaciones internas propias, sujetas a las preferencias del usuario.")
    @PutMapping("/leer-todas")
    public Map<String, Integer> leerTodas(@AuthenticationPrincipal UserSecurity autenticado) {
        return Map.of("actualizadas", notificaciones.marcarTodasLeidas(autenticado.getUser().getId()));
    }
}
