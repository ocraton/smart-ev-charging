package com.smartcharging.chargingorchestrator.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configurazione del client HTTP sincrono utilizzato dal prosumer per contattare i provider.
 *
 * <p>Il bean RestTemplate viene marcato con @LoadBalanced per consentire la risoluzione dei
 * serviceId registrati su Eureka, come "vehicle-provider" o "tariff-provider", senza dover
 * hardcodare host o porte. Questo mantiene il servizio aderente ai principi cloud-native e
 * alle regole del progetto sulla service discovery.</p>
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Espone un RestTemplate integrato con Spring Cloud LoadBalancer.
     *
     * @return client HTTP riusabile per le chiamate intra-cluster via Eureka
     */
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}