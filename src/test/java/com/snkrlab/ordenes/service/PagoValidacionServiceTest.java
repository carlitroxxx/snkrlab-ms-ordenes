package com.snkrlab.ordenes.service;

import com.snkrlab.ordenes.client.PagoClient;
import com.snkrlab.ordenes.dto.PagoConsultaDTO;
import com.snkrlab.ordenes.model.Orden;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PagoValidacionServiceTest {
    final PagoClient client = mock(PagoClient.class);
    final PagoValidacionService service = new PagoValidacionService(client, "https://cognito.test");
    final Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").claim("oid", "usuario").build();

    Orden orden() {
        Orden orden = new Orden(); orden.setId(2L); orden.setUsuarioId("usuario");
        orden.setTotal(100); orden.setMoneda("CLP"); orden.setEstado("PENDIENTE_PAGO");
        return orden;
    }

    @Test void aprobadoCoincidenteNoModificaOrden() {
        Orden orden = orden();
        var pago = new PagoConsultaDTO(1L, 2L, "usuario", 100, "CLP", "APROBADO");
        when(client.consultar(1L, jwt)).thenReturn(pago);
        assertSame(pago, service.validar(orden, 1L, jwt));
        assertEquals("PENDIENTE_PAGO", orden.getEstado()); assertNull(orden.getPagoId());
        verify(client).consultar(1L, jwt);
    }

    @Test void rechazaEstadosYDiferencias() {
        for (var pago : new PagoConsultaDTO[]{
                new PagoConsultaDTO(1L, 2L, "usuario", 100, "CLP", "RECHAZADO"),
                new PagoConsultaDTO(1L, 2L, "usuario", 100, "CLP", "BLOQUEADO_FRAUDE"),
                new PagoConsultaDTO(1L, 2L, "usuario", 100, "CLP", "aprobado"),
                new PagoConsultaDTO(1L, 2L, "otro", 100, "CLP", "APROBADO"),
                new PagoConsultaDTO(1L, 3L, "usuario", 100, "CLP", "APROBADO"),
                new PagoConsultaDTO(1L, 2L, "usuario", 101, "CLP", "APROBADO"),
                new PagoConsultaDTO(1L, 2L, "usuario", 100, "USD", "APROBADO"),
                new PagoConsultaDTO(1L, 2L, "usuario", null, "CLP", "APROBADO"), null}) {
            when(client.consultar(1L, jwt)).thenReturn(pago);
            assertEquals(409, assertThrows(ResponseStatusException.class,
                    () -> service.validar(orden(), 1L, jwt)).getStatusCode().value());
        }
    }

    @Test void ordenAjenaNoConsultaPago() {
        Orden orden = orden(); orden.setUsuarioId("otro");
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.validar(orden, 1L, jwt)).getStatusCode().value());
        verifyNoInteractions(client);
    }

    @Test void errorDeConsultaSePropagaSinModificarOrden() {
        Orden orden = orden();
        when(client.consultar(1L, jwt)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY));
        assertThrows(ResponseStatusException.class, () -> service.validar(orden, 1L, jwt));
        assertEquals("PENDIENTE_PAGO", orden.getEstado()); assertNull(orden.getPagoId());
    }

    @Test void cognitoUsaIdentidadConPrefijo() {
        Jwt cognito = Jwt.withTokenValue("test").header("alg", "none")
                .claim("iss", "https://cognito.test").subject("usuario").build();
        Orden orden = orden(); orden.setUsuarioId("cognito:usuario");
        var pago = new PagoConsultaDTO(1L, 2L, "cognito:usuario", 100, "CLP", "APROBADO");
        when(client.consultar(1L, cognito)).thenReturn(pago);
        assertSame(pago, service.validar(orden, 1L, cognito));
    }
}
