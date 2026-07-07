package com.smartcharging.tariffprovider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principale di bootstrap per il microservizio Tariff Provider.
 * 
 * <p>Questo modulo agisce come Service Provider puro (livello basso).
 * Espone API REST documentate tramite OpenAPI per fornire le proiezioni
 * dei costi orari dell'energia elettrica.</p>
 * 
 * <p>Includendo le dipendenze di Eureka Client nel pom.xml, il servizio
 * si registra automaticamente al Service Registry all'avvio, rendendosi
 * reperibile all'Energy Prosumer (tramite l'API Gateway o interazioni interne).</p>
 */
@SpringBootApplication
public class TariffProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(TariffProviderApplication.class, args);
    }
}