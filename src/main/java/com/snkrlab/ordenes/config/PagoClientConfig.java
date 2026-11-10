package com.snkrlab.ordenes.config;

import com.snkrlab.ordenes.client.PagoClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PagoClientConfig {
    @Bean
    public PagoClient pagoClient(@Value("${pagos.base-url}") String baseUrl,
            @Value("${pagos.connect-timeout-ms}") int connectTimeout,
            @Value("${pagos.read-timeout-ms}") int readTimeout) {
        if (connectTimeout <= 0 || readTimeout <= 0) {
            throw new IllegalArgumentException("Los tiempos de espera de pagos deben ser positivos.");
        }
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeout))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(readTimeout));
        return new PagoClient(RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build());
    }
}
