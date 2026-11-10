package com.snkrlab.ordenes.dto;

import java.util.List;
import java.util.UUID;
import com.snkrlab.ordenes.model.DetalleOrden;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CrearOrdenDTO {
    @NotNull private UUID checkoutId;
    @NotEmpty private List<DetalleOrden> items;
}
