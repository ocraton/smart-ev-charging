package com.smartcharging.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Classe principale di bootstrap per il microservizio Eureka Server.
 * * <p>Questo modulo funge da Service Registry per l'intera architettura Micro-SOA.
 * L'annotazione {@code @EnableEurekaServer} istruisce Spring Boot ad attivare 
 * l'auto-configurazione di Netflix Eureka, esponendo le API REST necessarie affinché 
 * gli altri microservizi (Prosumer, Provider e API Gateway) possano registrarsi 
 * e risolvere dinamicamente i rispettivi indirizzi IP a runtime.</p>
 * * <p>Questa configurazione è essenziale per soddisfare il vincolo della traccia
 * d'esame relativo al Load Balancing e alla tolleranza ai guasti: senza un registro 
 * centrale, i servizi dovrebbero fare affidamento su URL statici hardcodati, 
 * perdendo la capacità di scalare orizzontalmente su istanze multiple.</p>
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
