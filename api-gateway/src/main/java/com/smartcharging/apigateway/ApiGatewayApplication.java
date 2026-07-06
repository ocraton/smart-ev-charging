package com.smartcharging.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principale di bootstrap per l'API Gateway.
 * * <p>Questo modulo agisce come unico punto di ingresso (Single Entry Point) per 
 * tutti i client esterni, nascondendo la topologia interna dell'architettura Micro-SOA.
 * Risolve il vincolo d'esame intercettando il traffico e re-instradandolo dinamicamente 
 * verso i Prosumer o i Provider appropriati.</p>
 * * <p>Grazie all'integrazione con Spring Cloud LoadBalancer e Netflix Eureka Client, 
 * le route configurate in application.yml utilizzano il prefisso "lb://" per 
 * bilanciare il carico su istanze multiple dei servizi di backend (es. charging-orchestrator)
 * in modo completamente trasparente al chiamante.</p>
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}