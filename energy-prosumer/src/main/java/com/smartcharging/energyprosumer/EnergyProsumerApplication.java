package com.smartcharging.energyprosumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point del microservizio Energy Prosumer.
 *
 * <p>Il servizio rappresenta il livello Prosumer responsabile dell'orchestrazione
 * energetica e dell'avvio di simulazioni asincrone (FLOW-02) secondo il pattern
 * di polling non bloccante richiesto dalla traccia.</p>
 */
@SpringBootApplication
public class EnergyProsumerApplication {

    /**
     * Avvia il contesto Spring Boot del modulo Energy Prosumer.
     *
     * @param args argomenti passati da riga di comando
     */
    public static void main(String[] args) {
        SpringApplication.run(EnergyProsumerApplication.class, args);
    }
}
