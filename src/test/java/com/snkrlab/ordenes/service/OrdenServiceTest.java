package com.snkrlab.ordenes.service;

import com.snkrlab.ordenes.dto.CrearOrdenDTO;
import com.snkrlab.ordenes.model.*;
import com.snkrlab.ordenes.repository.OrdenRepository;
import com.snkrlab.ordenes.rabbitmq.VentaRegistradaMensaje;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class OrdenServiceTest {
    final OrdenRepository repository = mock(OrdenRepository.class);
    final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    final OrdenService service = new OrdenService(repository, eventos, mock(PlatformTransactionManager.class));

    CrearOrdenDTO solicitud() {
        DetalleOrden item = new DetalleOrden();
        item.setProductoId(1L); item.setNombreProducto("Zapatilla");
        item.setCantidad(2); item.setPrecioUnitario(100); item.setSubtotal(999);
        CrearOrdenDTO dto = new CrearOrdenDTO(); dto.setCheckoutId(UUID.randomUUID());
        dto.setItems(List.of(item)); return dto;
    }
    Orden crear(CrearOrdenDTO dto) {
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            Orden orden = inv.getArgument(0); orden.setId(1L); return orden;
        });
        return service.registrarPendiente("usuario", dto);
    }
    @Test void creaPendienteSinVentaYRecalculaTotal() {
        var dto = solicitud(); var orden = crear(dto);
        assertEquals("PENDIENTE_PAGO", orden.getEstado());
        assertEquals(dto.getCheckoutId(), orden.getCheckoutId());
        assertEquals("CLP", orden.getMoneda()); assertNull(orden.getPagoId());
        assertEquals(200, orden.getTotal()); assertEquals(200, orden.getItems().get(0).getSubtotal());
        verifyNoInteractions(eventos);
    }
    @Test void repeticionRecuperaSinInsertarOtraOrden() {
        var dto = solicitud(); var orden = crear(dto);
        when(repository.findByCheckoutId(dto.getCheckoutId())).thenReturn(Optional.of(orden));
        assertSame(orden, service.registrarPendiente("usuario", dto));
        verify(repository, times(1)).saveAndFlush(any()); verifyNoInteractions(eventos);
    }
    @Test void conflictosDeUsuarioYDetalle() {
        var dto = solicitud(); var orden = crear(dto);
        when(repository.findByCheckoutId(dto.getCheckoutId())).thenReturn(Optional.of(orden));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.registrarPendiente("otro", dto)).getStatusCode().value());
        dto.getItems().get(0).setCantidad(3);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.registrarPendiente("usuario", dto)).getStatusCode().value());
    }
    @Test void colisionUniqueRecuperaGanador() {
        var dto = solicitud(); var orden = crear(dto);
        when(repository.findByCheckoutId(dto.getCheckoutId())).thenReturn(Optional.empty(), Optional.of(orden));
        doThrow(new DataIntegrityViolationException("unique")).when(repository).saveAndFlush(any());
        assertSame(orden, service.registrarPendiente("usuario", dto));
        verifyNoInteractions(eventos);
    }
    @Test void consultaCheckoutVerificaPropietario() {
        var dto = solicitud(); var orden = crear(dto);
        when(repository.findByCheckoutIdAndUsuarioId(dto.getCheckoutId(), "usuario")).thenReturn(Optional.of(orden));
        assertSame(orden, service.obtenerPorCheckout(dto.getCheckoutId(), "usuario"));
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.obtenerPorCheckout(dto.getCheckoutId(), "otro")).getStatusCode().value());
    }
    @Test void conservaRegistroHistoricoYPublicacionAnterior() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Orden historica = service.registrar("usuario", solicitud().getItems());
        assertEquals("REGISTRADA", historica.getEstado());
        assertNull(historica.getCheckoutId()); assertNull(historica.getPagoId());
        when(repository.findByIdAndUsuarioId(1L, "usuario")).thenReturn(Optional.of(historica));
        assertSame(historica, service.obtenerPorId(1L, "usuario"));
        verify(eventos).publishEvent(any(VentaRegistradaMensaje.class));
    }
    @Test void checkoutObligatorioYDetalleInvalido() {
        var dto = solicitud(); dto.setCheckoutId(null);
        assertThrows(ResponseStatusException.class, () -> service.registrarPendiente("usuario", dto));
        dto.setCheckoutId(UUID.randomUUID()); dto.getItems().get(0).setCantidad(0);
        assertThrows(ResponseStatusException.class, () -> service.registrarPendiente("usuario", dto));
        verifyNoInteractions(repository, eventos);
    }
}
