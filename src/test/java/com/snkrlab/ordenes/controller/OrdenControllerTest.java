package com.snkrlab.ordenes.controller;

import com.snkrlab.ordenes.model.Orden;
import com.snkrlab.ordenes.repository.OrdenRepository;
import com.snkrlab.ordenes.rabbitmq.VentaRegistradaMensaje;
import com.snkrlab.ordenes.service.ComprobanteService;
import com.snkrlab.ordenes.service.OrdenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrdenControllerTest {
    final OrdenRepository repository = mock(OrdenRepository.class);
    final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    final OrdenService service = new OrdenService(repository, eventos, mock(PlatformTransactionManager.class));
    final UUID checkoutId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    final String items = """
            [{"productoId":1,"nombreProducto":"Zapatilla","cantidad":2,"precioUnitario":100,"subtotal":999}]
            """;
    MockMvc mvc;

    String pendiente() {
        return "{\"checkoutId\":\"" + checkoutId + "\",\"items\":" + items + "}";
    }

    @BeforeEach void configurar() {
        OrdenController controller = new OrdenController(service, new ComprobanteService(service, rabbit));
        ReflectionTestUtils.setField(controller, "claveInterna", "clave-test");
        ReflectionTestUtils.setField(controller, "cognitoIssuer", "https://cognito.test");
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none")
                .claim("iss", "https://issuer.test").claim("oid", "usuario").build();
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == Jwt.class;
                    }
                    @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory binderFactory) {
                        return jwt;
                    }
                }).build();
    }

    @Test void contratoHistoricoRecibeListaDevuelveIdYPublicaVenta() throws Exception {
        when(repository.save(any())).thenAnswer(inv -> {
            Orden orden = inv.getArgument(0);
            org.junit.jupiter.api.Assertions.assertEquals("REGISTRADA", orden.getEstado());
            org.junit.jupiter.api.Assertions.assertNull(orden.getCheckoutId());
            orden.setId(42L);
            return orden;
        });
        mvc.perform(post("/api/v1/ordenes").header("X-Internal-Key", "clave-test")
                        .contentType(MediaType.APPLICATION_JSON).content(items))
                .andExpect(status().isCreated()).andExpect(content().string("42"));
        verify(repository).save(any());
        verify(repository, never()).saveAndFlush(any());
        verify(eventos).publishEvent(any(VentaRegistradaMensaje.class));
    }

    @Test void pendienteEsIdempotenteNoPublicaVentaNiPermiteComprobante() throws Exception {
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            Orden orden = inv.getArgument(0);
            orden.setId(43L);
            when(repository.findByCheckoutId(checkoutId)).thenReturn(Optional.of(orden));
            when(repository.findByIdAndUsuarioId(43L, "usuario")).thenReturn(Optional.of(orden));
            return orden;
        });
        for (int intento = 0; intento < 2; intento++) {
            mvc.perform(post("/api/v1/ordenes/pendientes").header("X-Internal-Key", "clave-test")
                            .contentType(MediaType.APPLICATION_JSON).content(pendiente()))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(43))
                    .andExpect(jsonPath("$.checkoutId").value(checkoutId.toString()))
                    .andExpect(jsonPath("$.estado").value("PENDIENTE_PAGO"))
                    .andExpect(jsonPath("$.total").value(200));
        }
        mvc.perform(post("/api/v1/ordenes/43/comprobante").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"cliente@example.com\"}"))
                .andExpect(status().isConflict());
        verify(repository, times(1)).saveAndFlush(any());
        verify(repository, never()).save(any());
        verifyNoInteractions(eventos, rabbit);
    }

    @Test void contratosRechazanFormatoDelOtroEndpoint() throws Exception {
        mvc.perform(post("/api/v1/ordenes").header("X-Internal-Key", "clave-test")
                        .contentType(MediaType.APPLICATION_JSON).content(pendiente()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/ordenes/pendientes").header("X-Internal-Key", "clave-test")
                        .contentType(MediaType.APPLICATION_JSON).content(items))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository, eventos, rabbit);
    }

    @Test void ambosEndpointsExigenClaveInternaValida() throws Exception {
        mvc.perform(post("/api/v1/ordenes").header("X-Internal-Key", "incorrecta")
                        .contentType(MediaType.APPLICATION_JSON).content(items))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/ordenes/pendientes").header("X-Internal-Key", "incorrecta")
                        .contentType(MediaType.APPLICATION_JSON).content(pendiente()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository, eventos, rabbit);
    }
}
