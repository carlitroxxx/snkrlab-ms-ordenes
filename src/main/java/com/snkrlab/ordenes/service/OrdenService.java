package com.snkrlab.ordenes.service;

import com.snkrlab.ordenes.model.DetalleOrden;
import com.snkrlab.ordenes.model.Orden;
import com.snkrlab.ordenes.rabbitmq.DetalleVentaMensaje;
import com.snkrlab.ordenes.rabbitmq.VentaRegistradaMensaje;
import com.snkrlab.ordenes.repository.OrdenRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import com.snkrlab.ordenes.dto.CrearOrdenDTO;
import com.snkrlab.ordenes.model.EstadoOrden;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrdenService {

    private final OrdenRepository ordenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transaccion;

    public OrdenService(
            OrdenRepository ordenRepository,
            ApplicationEventPublisher eventPublisher, PlatformTransactionManager transactionManager) {

        this.ordenRepository = ordenRepository;
        this.eventPublisher = eventPublisher;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public Orden registrar(
            String usuarioId,
            List<DetalleOrden> items) {

        Orden orden = preparar(usuarioId, items);
        orden.setEstado(EstadoOrden.REGISTRADA);
        Orden guardada = ordenRepository.save(orden);
        List<DetalleVentaMensaje> detalleMensaje = guardada.getItems().stream()
                .map(item -> new DetalleVentaMensaje(item.getProductoId(), item.getNombreProducto(),
                        item.getCantidad(), item.getPrecioUnitario(), item.getSubtotal())).toList();
        eventPublisher.publishEvent(new VentaRegistradaMensaje(guardada.getId(), guardada.getFecha(),
                guardada.getTotal(), detalleMensaje));
        return guardada;
    }

    private Orden preparar(String usuarioId, List<DetalleOrden> items) {

        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La orden debe contener productos."
            );
        }

        Orden orden = new Orden();
        orden.setUsuarioId(usuarioId);
        orden.setFecha(LocalDateTime.now());
        orden.setEstado("REGISTRADA");

        int total = 0;

        for (DetalleOrden item : items) {
            if (item == null
                    || item.getProductoId() == null
                    || item.getProductoId() <= 0
                    || item.getNombreProducto() == null
                    || item.getNombreProducto().isBlank()
                    || item.getCantidad() == null
                    || item.getCantidad() <= 0
                    || item.getPrecioUnitario() == null
                    || item.getPrecioUnitario() < 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El detalle contiene datos inválidos."
                );
            }

            int subtotal;

            try {
                subtotal = Math.multiplyExact(
                        item.getCantidad(),
                        item.getPrecioUnitario()
                );

                total = Math.addExact(total, subtotal);
            } catch (ArithmeticException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El total supera el rango permitido."
                );
            }

            DetalleOrden detalle = new DetalleOrden();
            detalle.setProductoId(item.getProductoId());
            detalle.setNombreProducto(item.getNombreProducto());
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(item.getPrecioUnitario());
            detalle.setSubtotal(subtotal);

            orden.getItems().add(detalle);
        }

        orden.setTotal(total);

        return orden;
    }

    public Orden registrarPendiente(String usuarioId, CrearOrdenDTO solicitud) {
        if (usuarioId == null || usuarioId.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (solicitud == null || solicitud.getCheckoutId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "checkoutId es obligatorio.");
        Orden candidata = preparar(usuarioId, solicitud.getItems());
        candidata.setCheckoutId(solicitud.getCheckoutId());
        candidata.setEstado(EstadoOrden.PENDIENTE_PAGO);
        candidata.setMoneda("CLP");
        try {
            return transaccion.execute(status -> ordenRepository.findByCheckoutId(solicitud.getCheckoutId())
                    .map(existente -> recuperar(existente, candidata))
                    .orElseGet(() -> ordenRepository.saveAndFlush(candidata)));
        } catch (DataIntegrityViolationException ex) {
            // Consultar después del rollback, en una transacción nueva.
            return transaccion.execute(status -> ordenRepository.findByCheckoutId(solicitud.getCheckoutId())
                    .map(existente -> recuperar(existente, candidata))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "No se pudo reservar el checkout. Reintente con el mismo UUID.")));
        } catch (TransientDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout concurrente. Reintente con el mismo UUID.");
        }
    }

    private Orden recuperar(Orden existente, Orden candidata) {
        if (!Objects.equals(existente.getUsuarioId(), candidata.getUsuarioId())
                || !Objects.equals(existente.getItems(), candidata.getItems()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "checkoutId asociado a otros datos.");
        return existente;
    }

    @Transactional(readOnly = true)
    public Orden obtenerPorCheckout(UUID checkoutId, String usuarioId) {
        Orden orden = ordenRepository.findByCheckoutIdAndUsuarioId(checkoutId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada."));
        if (!EstadoOrden.PENDIENTE_PAGO.equals(orden.getEstado()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La orden ya no está pendiente de pago.");
        return orden;
    }

    public List<Orden> obtenerPorUsuario(String usuarioId) {
        return ordenRepository.findByUsuarioIdOrderByFechaDesc(
                usuarioId
        );
    }

    public Orden obtenerPorId(Long id, String usuarioId) {
        return ordenRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Orden no encontrada."
                ));
    }
}
