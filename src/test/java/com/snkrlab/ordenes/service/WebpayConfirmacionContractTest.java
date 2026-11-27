package com.snkrlab.ordenes.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.snkrlab.ordenes.client.PagoClient;
import com.snkrlab.ordenes.model.Orden;
import com.snkrlab.ordenes.dto.PagoConsultaDTO;
import org.springframework.web.server.ResponseStatusException;
class WebpayConfirmacionContractTest {
    @Test void soloPagoAprobadoCorrespondientePermiteConfirmar() {
        var service=new PagoValidacionService(mock(PagoClient.class),"issuer");
        var orden=new Orden();orden.setId(1L);orden.setTotal(1000);orden.setMoneda("CLP");
        assertDoesNotThrow(()->service.validarDatos(orden,7L,"cliente",new PagoConsultaDTO(7L,1L,"cliente",1000,"CLP","APROBADO")));
        for(String estado:new String[]{"INCIERTO","PENDIENTE","ANULADO","RECHAZADO"})
            assertThrows(ResponseStatusException.class,()->service.validarDatos(orden,7L,"cliente",new PagoConsultaDTO(7L,1L,"cliente",1000,"CLP",estado)));
        assertThrows(ResponseStatusException.class,()->service.validarDatos(orden,7L,"cliente",new PagoConsultaDTO(7L,1L,"otro",1000,"CLP","APROBADO")));
        assertThrows(ResponseStatusException.class,()->service.validarDatos(orden,7L,"cliente",new PagoConsultaDTO(7L,2L,"cliente",1000,"CLP","APROBADO")));
        assertThrows(ResponseStatusException.class,()->service.validarDatos(orden,7L,"cliente",new PagoConsultaDTO(7L,1L,"cliente",1,"CLP","APROBADO")));
    }
}
