package com.snkrlab.ordenes.service;

import com.snkrlab.ordenes.client.PagoClient;
import com.snkrlab.ordenes.dto.PagoConsultaDTO;
import com.snkrlab.ordenes.model.Orden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PagoValidacionService {
    private final PagoClient pagos;
    private final String cognitoIssuer;

    public PagoValidacionService(PagoClient pagos,
            @Value("${app.seguridad.cognito-issuer}") String cognitoIssuer) {
        this.pagos = pagos;
        this.cognitoIssuer = cognitoIssuer;
    }

    public PagoConsultaDTO validar(Orden orden, Long pagoId, Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        boolean cognito = cognitoIssuer.equals(jwt.getClaimAsString("iss"));
        String id = cognito ? jwt.getSubject() : jwt.getClaimAsString("oid");
        if (id == null || id.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        String usuario = cognito ? "cognito:" + id : id;
        if (orden == null || !usuario.equals(orden.getUsuarioId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada.");
        PagoConsultaDTO pago = pagos.consultar(pagoId, jwt);
        validarDatos(orden, pagoId, usuario, pago);
        return pago;
    }

    public void validarDatos(Orden orden, Long pagoId, String usuario, PagoConsultaDTO pago) {
        if (pago == null || !pagoId.equals(pago.id()) || !"APROBADO".equals(pago.resultado())
                || !usuario.equals(pago.usuarioId()) || orden.getId() == null
                || !orden.getId().equals(pago.ordenId()) || orden.getTotal() == null
                || !orden.getTotal().equals(pago.importe()) || orden.getMoneda() == null
                || !orden.getMoneda().equals(pago.moneda())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pago no corresponde a la orden o no esta aprobado.");
        }
    }
}
