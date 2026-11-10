package com.snkrlab.ordenes.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConfirmarOrdenDTO(@NotNull @Positive Long pagoId) {}
