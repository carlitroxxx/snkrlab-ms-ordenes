package com.snkrlab.ordenes.client;

import com.snkrlab.ordenes.dto.PagoConsultaDTO;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

public class PagoClient {
    private final RestClient http;

    public PagoClient(RestClient http) { this.http = http; }

    public PagoConsultaDTO consultar(Long pagoId, Jwt jwt) {
        if (pagoId == null || pagoId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (jwt == null || jwt.getTokenValue().isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        try {
            PagoConsultaDTO pago = http.get().uri("/api/v1/pagos/{id}", pagoId)
                    .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        if (response.getStatusCode().value() == 404)
                            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago no encontrado.");
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo consultar el pago.");
                    }).body(PagoConsultaDTO.class);
            if (pago == null || !pagoId.equals(pago.id()) || pago.ordenId() == null
                    || pago.usuarioId() == null || pago.usuarioId().isBlank() || pago.importe() == null
                    || pago.moneda() == null || pago.moneda().isBlank()
                    || pago.resultado() == null || pago.resultado().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Respuesta de pago incompleta o invalida.");
            }
            return pago;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo consultar el pago.");
        }
    }
}
