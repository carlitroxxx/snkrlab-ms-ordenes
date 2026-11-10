package com.snkrlab.ordenes.repository;

import com.snkrlab.ordenes.model.Orden;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrdenRepository extends JpaRepository<Orden, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Orden o where o.id = :id and o.usuarioId = :usuario")
    Optional<Orden> bloquearPorIdYUsuario(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("usuario") String usuario);

    Optional<Orden> findByCheckoutId(java.util.UUID checkoutId);
    Optional<Orden> findByCheckoutIdAndUsuarioId(java.util.UUID checkoutId, String usuarioId);

    List<Orden> findByUsuarioIdOrderByFechaDesc(String usuarioId);

    Optional<Orden> findByIdAndUsuarioId(Long id, String usuarioId);
}
