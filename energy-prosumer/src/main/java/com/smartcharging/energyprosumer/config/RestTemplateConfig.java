package com.smartcharging.energyprosumer.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configurazione dei client HTTP sincroni usati dal Prosumer.
 *
 * <p>Il bean RestTemplate e annotato con {@code @LoadBalanced} per risolvere
 * i nomi logici dei servizi tramite Eureka e applicare il bilanciamento lato client,
 * evitando endpoint hardcoded e consentendo scalabilita orizzontale dei provider.</p>
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Crea il RestTemplate load-balanced utilizzabile dai client interni.
     *
     * @return istanza RestTemplate integrata con Spring Cloud LoadBalancer
     */
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
