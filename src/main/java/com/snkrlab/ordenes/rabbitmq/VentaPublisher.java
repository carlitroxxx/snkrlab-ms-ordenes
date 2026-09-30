package com.snkrlab.ordenes.rabbitmq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class VentaPublisher {

    private final RabbitTemplate rabbitTemplate;

    public VentaPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(VentaRegistradaMensaje mensaje) {
        try {
            rabbitTemplate.convertAndSend(
                    "",
                    RabbitMQConfig.COLA_VENTAS,
                    mensaje,
                    message -> {
                        message.getMessageProperties().setDeliveryMode(
                                MessageDeliveryMode.PERSISTENT
                        );

                        return message;
                    }
            );
        } catch (AmqpException e) {
            log.error(
                    "La orden {} quedó registrada, pero no se pudo "
                            + "publicar su evento para Reportes.",
                    mensaje.getOrdenId(),
                    e
            );
        }
    }
}