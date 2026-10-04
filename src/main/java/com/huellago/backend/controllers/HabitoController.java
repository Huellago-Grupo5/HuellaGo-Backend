package com.huellago.backend.controllers;

import com.huellago.backend.dtos.AlimentacionResiduosDTO;
import com.huellago.backend.dtos.EnergiaDTO;
import com.huellago.backend.dtos.HabitoRespuestaDTO;
import com.huellago.backend.dtos.TransporteDTO;
import com.huellago.backend.services.HabitoService;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.security.UsuarioAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@CrossOrigin("*")
@RequestMapping("/habitos")
public class HabitoController {

    @Autowired
    HabitoService habitoService;

    @Autowired
    UsuarioAuthorizationService usuarioAuthorizationService;

    @PostMapping("/transporte")
    public ResponseEntity<List<HabitoRespuestaDTO>> registrarTransporte(@RequestBody TransporteDTO transporteDTO,
                                                                         @AuthenticationPrincipal UserSecurity userSecurity) {
        usuarioAuthorizationService.validarPropietario(transporteDTO.getUsuarioId(), userSecurity);
        return new ResponseEntity<>(habitoService.registrarTransporte(transporteDTO), HttpStatus.CREATED);
    }

    @PostMapping("/energia")
    public ResponseEntity<List<HabitoRespuestaDTO>> registrarEnergia(@RequestBody EnergiaDTO energiaDTO,
                                                                      @AuthenticationPrincipal UserSecurity userSecurity) {
        usuarioAuthorizationService.validarPropietario(energiaDTO.getUsuarioId(), userSecurity);
        return new ResponseEntity<>(habitoService.registrarEnergia(energiaDTO), HttpStatus.CREATED);
    }

    @PostMapping("/alimentacion-residuos")
    public ResponseEntity<List<HabitoRespuestaDTO>> registrarAlimentacionResiduos(
            @RequestBody AlimentacionResiduosDTO dto,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        usuarioAuthorizationService.validarPropietario(dto.getUsuarioId(), userSecurity);
        return new ResponseEntity<>(
                habitoService.registrarAlimentacionResiduos(dto),
                HttpStatus.CREATED
        );
    }
}
