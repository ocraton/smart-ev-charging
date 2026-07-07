package com.smartcharging.stationprovider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principale di bootstrap per lo Station Provider.
 * <p>Agisce come nodo foglia (Provider) nell'architettura Micro-SOA.
 * Rispetta il vincolo della topologia ibrida gestendo l'hardware
 * (colonnine di ricarica) tramite protocollo legacy SOAP (Apache CXF).</p>
 */
@SpringBootApplication
public class StationProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(StationProviderApplication.class, args);
    }
}