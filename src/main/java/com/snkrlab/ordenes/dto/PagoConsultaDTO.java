package com.snkrlab.ordenes.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PagoConsultaDTO(Long id, Long ordenId, String usuarioId,
        Integer importe, String moneda, String resultado) {}
