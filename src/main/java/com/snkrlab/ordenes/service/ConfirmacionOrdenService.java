package com.snkrlab.ordenes.service;

import com.snkrlab.ordenes.model.*;
import com.snkrlab.ordenes.repository.OrdenRepository;
import com.snkrlab.ordenes.rabbitmq.VentaRegistradaMensaje;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConfirmacionOrdenService implements ApplicationEventPublisherAware {
    private ApplicationEventPublisher eventPublisher;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    private final OrdenRepository repository;
    private final PagoValidacionService pagos;
    private final EntityManager entityManager;
    private final TransactionTemplate transaccion;
    private final String cognitoIssuer;

    public ConfirmacionOrdenService(OrdenRepository repository, PagoValidacionService pagos,
            EntityManager entityManager, PlatformTransactionManager manager,
            @Value("${app.seguridad.cognito-issuer}") String cognitoIssuer) {
        this.repository = repository; this.pagos = pagos; this.entityManager = entityManager;
        this.cognitoIssuer = cognitoIssuer;
        transaccion = new TransactionTemplate(manager);
        transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Orden confirmar(Long id, Long pagoId, Jwt jwt) {
        if (pagoId == null || pagoId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        boolean cognito = cognitoIssuer.equals(jwt.getClaimAsString("iss"));
        String sujeto = cognito ? jwt.getSubject() : jwt.getClaimAsString("oid");
        if (sujeto == null || sujeto.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        String usuario = cognito ? "cognito:" + sujeto : sujeto;
        Orden inicial = repository.findByIdAndUsuarioId(id, usuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (yaConfirmada(inicial, pagoId)) return inicial;
        var pago = pagos.validar(inicial, pagoId, jwt);
        try {
            return transaccion.execute(status -> {
                Orden orden = repository.bloquearPorIdYUsuario(id, usuario)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                // Evitar datos anteriores del contexto de persistencia de la solicitud (OSIV).
                entityManager.refresh(orden, LockModeType.PESSIMISTIC_WRITE);
                if (!usuario.equals(orden.getUsuarioId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                if (yaConfirmada(orden, pagoId)) return orden;
                pagos.validarDatos(orden, pagoId, usuario, pago);
                orden.setPagoId(pagoId);
                orden.setEstado(EstadoOrden.CONFIRMADA);
                Orden guardada = repository.saveAndFlush(orden);
                eventPublisher.publishEvent(VentaRegistradaMensaje.desde(guardada));
                return guardada;
            });
        } catch (DataIntegrityViolationException | TransientDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicto al confirmar la orden. Reintente con el mismo pago.");
        }
    }

    private boolean yaConfirmada(Orden orden, Long pagoId) {
        if (EstadoOrden.CONFIRMADA.equals(orden.getEstado()) && pagoId.equals(orden.getPagoId())) return true;
        if (!EstadoOrden.PENDIENTE_PAGO.equals(orden.getEstado()) || orden.getPagoId() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La orden no permite esta confirmacion.");
        return false;
    }
}
