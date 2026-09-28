package com.snkrlab.ordenes.service;

import com.snkrlab.ordenes.model.DetalleOrden;
import com.snkrlab.ordenes.model.Orden;
import com.snkrlab.ordenes.rabbitmq.CorreoMensaje;
import com.snkrlab.ordenes.rabbitmq.RabbitMQConfig;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class ComprobanteService {

    private final OrdenService ordenService;
    private final RabbitTemplate rabbitTemplate;

    public ComprobanteService(
            OrdenService ordenService,
            RabbitTemplate rabbitTemplate) {

        this.ordenService = ordenService;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void enviarComprobante(
            Long ordenId,
            String usuarioId,
            String correo) {

        // Este método verifica que la orden pertenezca al usuario.
        Orden orden = ordenService.obtenerPorId(ordenId, usuarioId);

        NumberFormat moneda =
                NumberFormat.getCurrencyInstance(
                        Locale.forLanguageTag("es-CL")
                );

        moneda.setMinimumFractionDigits(0);
        moneda.setMaximumFractionDigits(0);

        DateTimeFormatter formatoFecha =
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        StringBuilder contenido = new StringBuilder();

        contenido.append("SNKRLAB\n");
        contenido.append("Comprobante de orden registrada\n\n");

        contenido.append("Orden N.º ")
                .append(orden.getId())
                .append("\n");

        contenido.append("Fecha: ")
                .append(orden.getFecha().format(formatoFecha))
                .append("\n");

        contenido.append("Estado: ")
                .append(orden.getEstado())
                .append("\n\n");

        contenido.append("Productos:\n");

        for (DetalleOrden item : orden.getItems()) {
            contenido.append("\n")
                    .append(item.getNombreProducto())
                    .append("\nCantidad: ")
                    .append(item.getCantidad())
                    .append("\nPrecio unitario: ")
                    .append(moneda.format(item.getPrecioUnitario()))
                    .append("\nSubtotal: ")
                    .append(moneda.format(item.getSubtotal()))
                    .append("\n");
        }

        contenido.append("\nTOTAL: ")
                .append(moneda.format(orden.getTotal()))
                .append("\n\nGracias por comprar en SNKRLAB.");

        CorreoMensaje mensaje = new CorreoMensaje(
                correo.trim(),
                "SNKRLAB - Comprobante de orden #" + orden.getId(),
                contenido.toString()
        );

        try {
            rabbitTemplate.convertAndSend(
                    "",
                    RabbitMQConfig.COLA_CORREOS,
                    mensaje
            );
        } catch (AmqpException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo publicar la solicitud de correo.",
                    e
            );
        }
    }
}