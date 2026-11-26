package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.GrupoInstitucionalDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.GrupoInstitucionalService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@Tag(name = "Grupos y afiliaciones", description = "Los grupos son EQUIPO o FACULTAD y pertenecen a una institución; las operaciones administrativas exigen su rol.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/instituciones")
public class GrupoInstitucionalController {
    private final GrupoInstitucionalService servicio;
    public GrupoInstitucionalController(GrupoInstitucionalService servicio) { this.servicio = servicio; }
    @Operation(summary = "Crear invitación de afiliación", description = "Crear invitación de afiliación. Los grupos son EQUIPO o FACULTAD y pertenecen a una institución; las operaciones administrativas exigen su rol.")
    @PostMapping("/{id}/invitaciones")
    public ResponseEntity<GrupoInstitucionalDTO.Invitacion> invitar(@PathVariable Long id,
            @AuthenticationPrincipal UserSecurity usuario) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.invitar(usuario.getUser().getId(), id));
    }
    @Operation(summary = "Afiliarse mediante código", description = "Afiliarse mediante código. Los grupos son EQUIPO o FACULTAD y pertenecen a una institución; las operaciones administrativas exigen su rol.")
    @PostMapping("/afiliaciones")
    public ResponseEntity<GrupoInstitucionalDTO.Afiliacion> afiliar(@Valid @RequestBody GrupoInstitucionalDTO.Canje datos,
            @AuthenticationPrincipal UserSecurity usuario) {
        return ResponseEntity.ok(servicio.afiliar(usuario.getUser().getId(), datos.codigo()));
    }
    @Operation(summary = "Crear grupo institucional", description = "Crear grupo institucional. Los grupos son EQUIPO o FACULTAD y pertenecen a una institución; las operaciones administrativas exigen su rol.")
    @PostMapping("/{id}/grupos")
    public ResponseEntity<GrupoInstitucionalDTO.Grupo> crear(@PathVariable Long id,
            @Valid @RequestBody GrupoInstitucionalDTO.Datos datos, @AuthenticationPrincipal UserSecurity usuario) {
        var grupo = servicio.crear(usuario.getUser().getId(), id, datos);
        return ResponseEntity.created(URI.create("/instituciones/" + id + "/grupos/" + grupo.id())).body(grupo);
    }
    @Operation(summary = "Listar grupos institucionales", description = "Listar grupos institucionales. Los grupos son EQUIPO o FACULTAD y pertenecen a una institución; las operaciones administrativas exigen su rol.")
    @GetMapping("/{id}/grupos")
    public List<GrupoInstitucionalDTO.Grupo> listar(@PathVariable Long id,
            @AuthenticationPrincipal UserSecurity usuario) {
        return servicio.listar(usuario.getUser().getId(), id);
    }
    @Operation(summary = "Actualizar grupo institucional", description = "Actualizar grupo institucional. Los grupos son EQUIPO o FACULTAD y pertenecen a una institución; las operaciones administrativas exigen su rol.")
    @PutMapping("/{id}/grupos/{grupoId}")
    public GrupoInstitucionalDTO.Grupo actualizar(@PathVariable Long id, @PathVariable Long grupoId,
            @Valid @RequestBody GrupoInstitucionalDTO.Datos datos, @AuthenticationPrincipal UserSecurity usuario) {
        return servicio.actualizar(usuario.getUser().getId(), id, grupoId, datos);
    }
    @Operation(summary = "Eliminar grupo institucional", description = "Eliminar grupo institucional. Los grupos son EQUIPO o FACULTAD y pertenecen a una institución; las operaciones administrativas exigen su rol.")
    @DeleteMapping("/{id}/grupos/{grupoId}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @PathVariable Long grupoId,
            @AuthenticationPrincipal UserSecurity usuario) {
        servicio.eliminar(usuario.getUser().getId(), id, grupoId);
        return ResponseEntity.noContent().build();
    }
    @Operation(summary = "Asignar miembro a grupo", description = "Asignar miembro a grupo. Los grupos son EQUIPO o FACULTAD y pertenecen a una institución; las operaciones administrativas exigen su rol.")
    @PutMapping("/{id}/grupos/{grupoId}/miembros/{miembroId}")
    public GrupoInstitucionalDTO.Afiliacion asignar(@PathVariable Long id, @PathVariable Long grupoId,
            @PathVariable Long miembroId, @AuthenticationPrincipal UserSecurity usuario) {
        return servicio.asignar(usuario.getUser().getId(), id, grupoId, miembroId);
    }
    @Operation(summary = "Desasignar miembro de grupo", description = "Desasignar miembro de grupo. Los grupos son EQUIPO o FACULTAD y pertenecen a una institución; las operaciones administrativas exigen su rol.")
    @DeleteMapping("/{id}/grupos/{grupoId}/miembros/{miembroId}")
    public GrupoInstitucionalDTO.Afiliacion desasignar(@PathVariable Long id, @PathVariable Long grupoId,
            @PathVariable Long miembroId, @AuthenticationPrincipal UserSecurity usuario) {
        return servicio.desasignar(usuario.getUser().getId(), id, grupoId, miembroId);
    }
    @Operation(summary = "Consultar ranking de grupos", description = "Consultar ranking de grupos. Los grupos son EQUIPO o FACULTAD y pertenecen a una institución; las operaciones administrativas exigen su rol.")
    @GetMapping("/{id}/ranking-grupos")
    public GrupoInstitucionalDTO.Ranking ranking(@PathVariable Long id,
            @AuthenticationPrincipal UserSecurity usuario) {
        return servicio.ranking(usuario.getUser().getId(), id);
    }
}
