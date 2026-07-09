package com.smartcharging.chargingorchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe di bootstrap del microservizio Charging Orchestrator.
 *
 * <p>Questo prosumer espone un endpoint REST che aggrega in parallelo dati REST e SOAP
 * provenienti dai provider interni della piattaforma Smart EV-Charging. La classe resta
 * volutamente minimale per delegare tutta la logica di integrazione ai package dedicati,
 * mantenendo il punto di ingresso del servizio semplice, testabile e coerente con gli
 * standard Spring Boot 3.x richiesti dalla traccia.</p>
 */
@SpringBootApplication
public class ChargingOrchestratorApplication {

    /**
     * Avvia il contesto Spring Boot del Charging Orchestrator.
     *
     * @param args argomenti eventualmente passati alla JVM in fase di bootstrap
     */
    public static void main(String[] args) {
        SpringApplication.run(ChargingOrchestratorApplication.class, args);
    }
}