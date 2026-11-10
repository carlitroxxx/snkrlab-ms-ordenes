package com.snkrlab.ordenes.service;

import com.snkrlab.ordenes.client.PagoClient;
import com.snkrlab.ordenes.dto.PagoConsultaDTO;
import com.snkrlab.ordenes.model.Orden;
import com.snkrlab.ordenes.repository.OrdenRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfirmacionOrdenServiceTest {
    final OrdenRepository repository = mock(OrdenRepository.class);
    final PagoClient client = mock(PagoClient.class);
    final EntityManager em = mock(EntityManager.class);
    final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    final PagoValidacionService pagos = new PagoValidacionService(client, "cognito");
    final ConfirmacionOrdenService service = new ConfirmacionOrdenService(repository, pagos, em, manager, "cognito");
    final Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("oid", "usuario").build();

    Orden pendiente() {
        Orden o = new Orden(); o.setId(2L); o.setUsuarioId("usuario"); o.setTotal(100);
        o.setMoneda("CLP"); o.setEstado("PENDIENTE_PAGO"); return o;
    }

    void configurar(Orden inicial, Orden bloqueada) {
        when(repository.findByIdAndUsuarioId(2L, "usuario")).thenReturn(Optional.of(inicial));
        when(repository.bloquearPorIdYUsuario(2L, "usuario")).thenReturn(Optional.of(bloqueada));
        when(client.consultar(1L, jwt)).thenReturn(new PagoConsultaDTO(1L, 2L, "usuario", 100, "CLP", "APROBADO"));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test void confirmaYRepiteSinOtroGuardadoNiConsulta() {
        Orden orden = pendiente(); configurar(orden, orden);
        assertSame(orden, service.confirmar(2L, 1L, jwt));
        assertEquals("CONFIRMADA", orden.getEstado()); assertEquals(1L, orden.getPagoId());
        assertSame(orden, service.confirmar(2L, 1L, jwt));
        verify(client, times(1)).consultar(1L, jwt);
        verify(repository, times(1)).saveAndFlush(orden);
        var secuencia = inOrder(client, manager, repository, em);
        secuencia.verify(client).consultar(1L, jwt);
        secuencia.verify(manager).getTransaction(any());
        secuencia.verify(repository).bloquearPorIdYUsuario(2L, "usuario");
        secuencia.verify(em).refresh(orden, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test void competidorConfirmaAntesDelBloqueoMismoPago() {
        Orden inicial = pendiente(); Orden ganadora = pendiente();
        ganadora.setEstado("CONFIRMADA"); ganadora.setPagoId(1L);
        configurar(inicial, ganadora);
        assertSame(ganadora, service.confirmar(2L, 1L, jwt));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void competidorConfirmaConOtroPago() {
        Orden ganadora = pendiente(); ganadora.setEstado("CONFIRMADA"); ganadora.setPagoId(9L);
        configurar(pendiente(), ganadora);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.confirmar(2L, 1L, jwt)).getStatusCode().value());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void datosCambiadosDuranteConsultaImpidenConfirmar() {
        Orden bloqueada = pendiente(); bloqueada.setTotal(101);
        configurar(pendiente(), bloqueada);
        assertThrows(ResponseStatusException.class, () -> service.confirmar(2L, 1L, jwt));
        assertEquals("PENDIENTE_PAGO", bloqueada.getEstado());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void ajenaDevuelve404SinConsultarPago() {
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.confirmar(2L, 1L, jwt)).getStatusCode().value());
        verifyNoInteractions(client, manager);
    }

    @Test void pagosInvalidosNoAbrenTransaccion() {
        Orden orden = pendiente(); configurar(orden, orden);
        for (String resultado : new String[]{"RECHAZADO", "BLOQUEADO_FRAUDE", "DESCONOCIDO"}) {
            when(client.consultar(1L, jwt)).thenReturn(new PagoConsultaDTO(1L, 2L, "usuario", 100, "CLP", resultado));
            assertThrows(ResponseStatusException.class, () -> service.confirmar(2L, 1L, jwt));
        }
        for (HttpStatus error : new HttpStatus[]{HttpStatus.NOT_FOUND, HttpStatus.BAD_GATEWAY}) {
            doThrow(new ResponseStatusException(error)).when(client).consultar(1L, jwt);
            assertThrows(ResponseStatusException.class, () -> service.confirmar(2L, 1L, jwt));
        }
        assertEquals("PENDIENTE_PAGO", orden.getEstado()); assertNull(orden.getPagoId());
        verifyNoInteractions(manager);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void historicaYOtroPagoConfirmadoDevuelven409() {
        Orden orden = pendiente(); configurar(orden, orden);
        orden.setEstado("REGISTRADA");
        assertThrows(ResponseStatusException.class, () -> service.confirmar(2L, 1L, jwt));
        orden.setEstado("CONFIRMADA"); orden.setPagoId(9L);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.confirmar(2L, 1L, jwt)).getStatusCode().value());
        verifyNoInteractions(client, manager);
    }
}
