package com.smartcharging.vehicleprovider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principale di bootstrap per il microservizio Vehicle Provider.
 *
 * <p>Questo modulo espone API REST per fornire telemetria essenziale dei veicoli
 * elettrici, come capacita della batteria e stato di carica corrente (SoC).
 * Includendo le dipendenze di Eureka Client nel pom.xml, il servizio si registra
 * automaticamente al Service Registry all'avvio per essere risolto via discovery.</p>
 */
@SpringBootApplication
public class VehicleProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(VehicleProviderApplication.class, args);
    }
}
