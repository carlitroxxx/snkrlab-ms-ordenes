package com.snkrlab.ordenes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EnviarComprobanteDTO {

    @NotBlank
    @Email
    @Size(max = 254)
    private String correo;
}