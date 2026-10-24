package com.huellago.backend.controllers;
import com.huellago.backend.dtos.*;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.ObjetivoAmbientalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @CrossOrigin("*") @RequestMapping
public class ObjetivoAmbientalController {
    private final ObjetivoAmbientalService service;
    public ObjetivoAmbientalController(ObjetivoAmbientalService service) { this.service = service; }
    @GetMapping("/objetivos-ambientales")
    public ResponseEntity<List<ObjetivoAmbientalDTO>> disponibles() { return ResponseEntity.ok(service.listarDisponibles()); }
    @GetMapping("/usuarios/me/objetivos")
    public ResponseEntity<List<ObjetivoAmbientalDTO>> seleccionados(@AuthenticationPrincipal UserSecurity auth) { return ResponseEntity.ok(service.listarSeleccionados(auth.getUsername())); }
    @PutMapping("/usuarios/me/objetivos")
    public ResponseEntity<List<ObjetivoAmbientalDTO>> actualizar(@Valid @RequestBody SeleccionObjetivosAmbientalesDTO dto, @AuthenticationPrincipal UserSecurity auth) { return ResponseEntity.ok(service.actualizarSeleccion(auth.getUsername(), dto)); }
}
