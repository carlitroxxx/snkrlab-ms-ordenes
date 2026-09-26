package com.snkrlab.ordenes.controller;

import com.snkrlab.ordenes.model.DetalleOrden;
import com.snkrlab.ordenes.model.Orden;
import com.snkrlab.ordenes.service.OrdenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ordenes")
public class OrdenController {

    private final OrdenService ordenService;

    @Value("${ordenes.internal-key}")
    private String claveInterna;

    public OrdenController(OrdenService ordenService) {
        this.ordenService = ordenService;
    }

    private String usuarioIdDesde(Jwt jwt) {
        String usuarioId = jwt.getClaimAsString("oid");

        if (usuarioId == null || usuarioId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "El token no contiene el identificador del usuario."
            );
        }

        return usuarioId;
    }

    @PostMapping
    public ResponseEntity<Long> registrar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Internal-Key") String clave,
            @RequestBody List<DetalleOrden> items) {

        if (!claveInterna.equals(clave)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Orden orden = ordenService.registrar(
                usuarioIdDesde(jwt),
                items
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orden.getId());
    }

    @GetMapping
    public List<Orden> obtenerMisOrdenes(
            @AuthenticationPrincipal Jwt jwt) {

        return ordenService.obtenerPorUsuario(usuarioIdDesde(jwt));
    }

    @GetMapping("/{id}")
    public Orden obtenerOrden(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {

        return ordenService.obtenerPorId(id, usuarioIdDesde(jwt));
    }
}