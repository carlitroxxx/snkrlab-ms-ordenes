package com.snkrlab.ordenes.rabbitmq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CorreoMensaje {

    private String to;
    private String subject;
    private String body;
}