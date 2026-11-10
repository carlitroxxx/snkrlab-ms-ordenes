package com.snkrlab.ordenes.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ordenes", uniqueConstraints = @UniqueConstraint(name = "uk_ordenes_checkout_id", columnNames = "checkout_id"))
@Data
@NoArgsConstructor
public class Orden {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "checkout_id", length = 36, updatable = false)
    private UUID checkoutId;

    private Long pagoId;

    @Column(length = 3)
    private String moneda;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String usuarioId;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(nullable = false)
    private String estado;

    @Column(nullable = false)
    private Integer total;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "orden_detalles",
            joinColumns = @JoinColumn(name = "orden_id")
    )
    @OrderColumn(name = "posicion")
    private List<DetalleOrden> items = new ArrayList<>();
}
