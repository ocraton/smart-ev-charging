package com.smartcharging.energyprosumer.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configurazione dei client HTTP sincroni usati dal Prosumer.
 *
 * <p>Il bean RestTemplate e annotato con {@code @LoadBalanced} per risolvere
 * i nomi logici dei servizi tramite Eureka e applicare il bilanciamento lato client,
 * evitando endpoint hardcoded e consentendo scalabilita orizzontale dei provider.</p>
 *
 * <p>I timeout espliciti evitano che un provider irraggiungibile o bloccato mantenga
 * occupato indefinitamente il thread in background che esegue runSimulation.</p>
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Crea il RestTemplate load-balanced utilizzabile dai client interni, con timeout limitati.
     *
     * @param builder builder auto-configurato da Spring Boot, usato per impostare i timeout
     * @return istanza RestTemplate integrata con Spring Cloud LoadBalancer
     */
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(3))
            .setReadTimeout(Duration.ofSeconds(5))
            .build();
    }
}
