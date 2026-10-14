package com.snkrlab.ordenes.controller;

import java.util.List;
import java.util.Map;

import com.snkrlab.ordenes.dto.EnviarComprobanteDTO;
import com.snkrlab.ordenes.model.DetalleOrden;
import com.snkrlab.ordenes.model.Orden;
import com.snkrlab.ordenes.service.ComprobanteService;
import com.snkrlab.ordenes.service.OrdenService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ordenes")
public class OrdenController {

    private final OrdenService ordenService;
    private final ComprobanteService comprobanteService;

    @Value("${ordenes.internal-key}")
    private String claveInterna;

    @Value("${app.seguridad.cognito-issuer}")
    private String cognitoIssuer;

    public OrdenController(
            OrdenService ordenService,
            ComprobanteService comprobanteService) {

        this.ordenService = ordenService;
        this.comprobanteService = comprobanteService;
    }

    private String usuarioIdDesde(Jwt jwt) {
        String emisor = jwt.getClaimAsString("iss");
        boolean esCognito = cognitoIssuer.equals(emisor);

        String identificador = esCognito
                ? jwt.getSubject()
                : jwt.getClaimAsString("oid");

        if (identificador == null || identificador.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "El token no contiene el identificador del usuario."
            );
        }

        return esCognito
                ? "cognito:" + identificador
                : identificador;
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

        return ordenService.obtenerPorId(
                id,
                usuarioIdDesde(jwt)
        );
    }

    @PostMapping("/{id}/comprobante")
    public ResponseEntity<Map<String, String>> enviarComprobante(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody EnviarComprobanteDTO solicitud) {

        comprobanteService.enviarComprobante(
                id,
                usuarioIdDesde(jwt),
                solicitud.getCorreo()
        );

        return ResponseEntity.accepted().body(
                Map.of(
                        "mensaje",
                        "Solicitud de envío recibida. "
                                + "El comprobante se procesará por correo."
                )
        );
    }
}