package com.smartcharging.chargingorchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configurazione del thread pool dedicato alle integrazioni parallele del prosumer.
 *
 * <p>La traccia richiede esplicitamente l'uso di un executor separato per evitare che le
 * chiamate concorrenti verso Vehicle Provider, Tariff Provider e Station Provider occupino
 * i thread HTTP del container web. L'executor nominato "prosumerExecutor" viene quindi
 * iniettato nel servizio di orchestrazione e usato come base esplicita per i task lanciati
 * con CompletableFuture.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Registra il thread pool usato per le chiamate parallele verso i provider interni.
     *
     * @return executor configurato con capacita sufficiente per il caso d'uso FLOW-01
     */
    @Bean(name = "prosumerExecutor")
    public Executor prosumerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ProsumerParallel-");
        executor.initialize();
        return executor;
    }
}