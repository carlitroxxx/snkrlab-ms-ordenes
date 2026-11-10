package com.snkrlab.ordenes.controller;

import com.snkrlab.ordenes.dto.ConfirmarOrdenDTO;
import com.snkrlab.ordenes.model.Orden;
import com.snkrlab.ordenes.service.ConfirmacionOrdenService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ordenes")
public class ConfirmacionOrdenController {
    private final ConfirmacionOrdenService service;
    public ConfirmacionOrdenController(ConfirmacionOrdenService service) { this.service = service; }

    @PostMapping("/{id}/confirmacion")
    public Orden confirmar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConfirmarOrdenDTO solicitud) {
        return service.confirmar(id, solicitud.pagoId(), jwt);
    }
}
