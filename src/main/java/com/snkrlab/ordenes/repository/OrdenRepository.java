package com.snkrlab.ordenes.repository;

import com.snkrlab.ordenes.model.Orden;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrdenRepository extends JpaRepository<Orden, Long> {

    List<Orden> findByUsuarioIdOrderByFechaDesc(String usuarioId);

    Optional<Orden> findByIdAndUsuarioId(Long id, String usuarioId);
}