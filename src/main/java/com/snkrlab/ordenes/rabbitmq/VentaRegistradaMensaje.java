package com.snkrlab.ordenes.rabbitmq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VentaRegistradaMensaje {

    public static VentaRegistradaMensaje desde(com.snkrlab.ordenes.model.Orden orden) {
        List<DetalleVentaMensaje> detalle = orden.getItems().stream()
                .map(item -> new DetalleVentaMensaje(item.getProductoId(), item.getNombreProducto(),
                        item.getCantidad(), item.getPrecioUnitario(), item.getSubtotal())).toList();
        return new VentaRegistradaMensaje(orden.getId(), orden.getFecha(), orden.getTotal(), detalle);
    }

    private Long ordenId;
    private LocalDateTime fecha;
    private Integer total;
    private List<DetalleVentaMensaje> items;
}
