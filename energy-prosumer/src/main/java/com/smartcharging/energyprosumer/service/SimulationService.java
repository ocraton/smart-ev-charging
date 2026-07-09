package com.smartcharging.energyprosumer.service;

import com.smartcharging.energyprosumer.dto.SimulationRequest;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Servizio applicativo per la gestione dello stato delle simulazioni asincrone.
 *
 * <p>L'implementazione usa una memoria in-process con {@link ConcurrentHashMap}
 * per modellare il ciclo di vita dei ticket senza persistenza esterna, conforme
 * all'approccio didattico del progetto. L'elaborazione lunga viene simulata in
 * background tramite {@link CompletableFuture} con completamento differito.</p>
 */
@Service
public class SimulationService {

    private final Map<String, String> ticketStatusMap = new ConcurrentHashMap<>();

    /**
     * Avvia una nuova simulazione asincrona e restituisce il ticket di polling.
     *
     * <p>Il metodo e non bloccante: registra lo stato iniziale in {@code PENDING}
     * e pianifica il completamento dopo 10 secondi su executor differito,
     * senza bloccare il thread HTTP chiamante.</p>
     *
     * @param request payload della simulazione (attualmente vuoto)
     * @return identificativo univoco del ticket
     */
    public String startAsyncSimulation(SimulationRequest request) {
        String ticketId = UUID.randomUUID().toString();
        ticketStatusMap.put(ticketId, "PENDING");

        CompletableFuture.runAsync(
            () -> ticketStatusMap.put(ticketId, "COMPLETED"),
            CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS)
        );

        return ticketId;
    }

    /**
     * Recupera lo stato corrente di una simulazione dal ticket in-memory.
     *
     * @param ticketId identificativo della simulazione
     * @return stato corrente del ticket; {@code null} se il ticket non esiste
     */
    public String getSimulationStatus(String ticketId) {
        return ticketStatusMap.get(ticketId);
    }
}
