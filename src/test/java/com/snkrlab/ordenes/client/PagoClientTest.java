package com.snkrlab.ordenes.client;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PagoClientTest {
    MockRestServiceServer server;
    PagoClient client;
    final Jwt jwt = Jwt.withTokenValue("token-test").header("alg", "none").claim("oid", "usuario").build();
    final String pago = """
            {"id":1,"ordenId":2,"usuarioId":"usuario","importe":100,"moneda":"CLP","resultado":"APROBADO","intentoId":null}
            """;

    @BeforeEach void configurar() {
        var builder = RestClient.builder().baseUrl("http://pagos.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PagoClient(builder.build());
    }

    @Test void consultaRutaYTransmiteBearer() {
        server.expect(requestTo("http://pagos.test/api/v1/pagos/1"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer token-test"))
                .andRespond(withSuccess(pago, MediaType.APPLICATION_JSON));
        assertEquals("APROBADO", client.consultar(1L, jwt).resultado());
        server.verify();
    }

    @Test void erroresHttpNuncaAprueban() {
        for (HttpStatus status : new HttpStatus[]{HttpStatus.NOT_FOUND, HttpStatus.UNAUTHORIZED,
                HttpStatus.FORBIDDEN, HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.FOUND}) {
            server.reset();
            server.expect(requestTo("http://pagos.test/api/v1/pagos/1")).andRespond(withStatus(status));
            assertEquals(status == HttpStatus.NOT_FOUND ? 404 : 502,
                    assertThrows(ResponseStatusException.class, () -> client.consultar(1L, jwt)).getStatusCode().value());
            server.verify();
        }
    }

    @Test void respuestasInvalidasNuncaAprueban() {
        for (String body : new String[]{"", "{}", "no-json", pago.replace("\"id\":1", "\"id\":99"),
                pago.replace("\"importe\":100", "\"importe\":null")}) {
            server.reset();
            server.expect(requestTo("http://pagos.test/api/v1/pagos/1"))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertEquals(502, assertThrows(ResponseStatusException.class,
                    () -> client.consultar(1L, jwt)).getStatusCode().value());
            server.verify();
        }
    }

    @Test void timeoutNoAprueba() {
        server.expect(requestTo("http://pagos.test/api/v1/pagos/1"))
                .andRespond(withException(new SocketTimeoutException("timeout")));
        assertEquals(502, assertThrows(ResponseStatusException.class,
                () -> client.consultar(1L, jwt)).getStatusCode().value());
        server.verify();
    }
}
