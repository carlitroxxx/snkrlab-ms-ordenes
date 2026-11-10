package com.snkrlab.ordenes.service;

import com.snkrlab.ordenes.model.Orden;
import com.snkrlab.ordenes.rabbitmq.CorreoMensaje;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ComprobanteServiceTest {
    final OrdenService ordenes = mock(OrdenService.class);
    final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    final ComprobanteService service = new ComprobanteService(ordenes, rabbit);
    @Test void pendienteNoGeneraComprobante() {
        Orden orden = new Orden(); orden.setEstado("PENDIENTE_PAGO");
        when(ordenes.obtenerPorId(1L, "usuario")).thenReturn(orden);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.enviarComprobante(1L, "usuario", "cliente@example.com")).getStatusCode().value());
        verifyNoInteractions(rabbit);
    }
    @Test void historicaSinCamposNuevosConservaComprobante() {
        Orden orden = new Orden(); orden.setId(1L); orden.setEstado("REGISTRADA");
        orden.setFecha(LocalDateTime.now()); orden.setTotal(100);
        when(ordenes.obtenerPorId(1L, "usuario")).thenReturn(orden);
        service.enviarComprobante(1L, "usuario", "cliente@example.com");
        verify(rabbit).convertAndSend(eq(""), eq("notificaciones.email.queue"), any(CorreoMensaje.class));
    }
    @Test void propietarioIncorrectoNoPublica() {
        when(ordenes.obtenerPorId(1L, "otro")).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        assertThrows(ResponseStatusException.class, () -> service.enviarComprobante(1L, "otro", "cliente@example.com"));
        verifyNoInteractions(rabbit);
    }
}
