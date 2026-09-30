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

@Service
public class OrdenService {

    private final OrdenRepository ordenRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrdenService(
            OrdenRepository ordenRepository,
            ApplicationEventPublisher eventPublisher) {

        this.ordenRepository = ordenRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Orden registrar(
            String usuarioId,
            List<DetalleOrden> items) {

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

        Orden guardada = ordenRepository.save(orden);

        List<DetalleVentaMensaje> detalleMensaje =
                guardada.getItems().stream()
                        .map(item -> new DetalleVentaMensaje(
                                item.getProductoId(),
                                item.getNombreProducto(),
                                item.getCantidad(),
                                item.getPrecioUnitario(),
                                item.getSubtotal()
                        ))
                        .toList();

        eventPublisher.publishEvent(
                new VentaRegistradaMensaje(
                        guardada.getId(),
                        guardada.getFecha(),
                        guardada.getTotal(),
                        detalleMensaje
                )
        );

        return guardada;
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