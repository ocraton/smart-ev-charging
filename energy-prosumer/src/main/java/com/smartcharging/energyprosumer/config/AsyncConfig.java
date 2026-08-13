package com.smartcharging.energyprosumer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configurazione del thread pool dedicato all'elaborazione in background delle simulazioni.
 *
 * <p>Questo bean, passato esplicitamente come terzo argomento a
 * {@code delayedExecutor}, allinea l'architettura di questo prosumer a quella di
 * ChargingOrchestrator (che usa un "prosumerExecutor" dedicato per lo stesso motivo).</p>
 */
@Configuration
public class AsyncConfig {

    /**
     * Registra il thread pool usato per l'elaborazione differita delle simulazioni FLOW-02.
     *
     * @return executor dedicato, separato dal pool condiviso della JVM
     */
    @Bean(name = "simulationExecutor")
    public Executor simulationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("SimulationAsync-");
        executor.initialize();
        return executor;
    }
}
