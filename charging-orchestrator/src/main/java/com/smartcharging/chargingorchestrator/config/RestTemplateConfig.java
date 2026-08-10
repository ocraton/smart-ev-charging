package com.smartcharging.chargingorchestrator.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configurazione del client HTTP sincrono utilizzato dal prosumer per contattare i provider.
 *
 * <p>Il bean RestTemplate viene marcato con @LoadBalanced per consentire la risoluzione dei
 * serviceId registrati su Eureka, come "vehicle-provider" o "tariff-provider", senza dover
 * hardcodare host o porte. Questo mantiene il servizio aderente ai principi cloud-native e
 * alle regole del progetto sulla service discovery.</p>
 *
 * <p>I timeout espliciti su connessione e lettura evitano che un provider a valle irraggiungibile
 * o bloccato mantenga occupato indefinitamente un thread del prosumerExecutor: allo scadere,
 * la chiamata fallisce con un'eccezione gestita da OptimizationService invece di restare sospesa.</p>
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Espone un RestTemplate integrato con Spring Cloud LoadBalancer e con timeout limitati.
     *
     * @param builder builder auto-configurato da Spring Boot, usato per impostare i timeout
     * @return client HTTP riusabile per le chiamate intra-cluster via Eureka
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