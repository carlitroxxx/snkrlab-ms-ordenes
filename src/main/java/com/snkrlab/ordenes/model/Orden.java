package com.snkrlab.ordenes.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ordenes")
@Data
@NoArgsConstructor
public class Orden {

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