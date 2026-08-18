package com.smartcharging.chargingorchestrator.controller;

import com.smartcharging.chargingorchestrator.dto.OptimizationResponse;
import com.smartcharging.chargingorchestrator.service.OptimizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST del Charging Orchestrator.
 *
 * <p>Espone verso il Gateway un endpoint di lettura che aggrega informazioni provenienti dai tre
 * provider interni e dall'Energy Prosumer, restituendo una risposta già arricchita. L'endpoint
 * resta sincrono dal punto di vista del client, mentre la concorrenza necessaria viene gestita
 * all'interno del service applicativo tramite CompletableFuture ed executor dedicato.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Charging Optimization", description = "API Prosumer per l'orchestrazione dei piani di ricarica")
public class OptimizationController {

    private final OptimizationService optimizationService;

    /**
     * Inietta il servizio di orchestrazione come dipendenza immutabile del controller.
     *
     * @param optimizationService servizio applicativo che coordina i provider interni
     */
    public OptimizationController(OptimizationService optimizationService) {
        this.optimizationService = optimizationService;
    }

    /**
     * Aggrega in parallelo i dati tecnici dei provider e la valutazione economica dell'altro prosumer.
     *
     * @param vehicleId identificativo del veicolo da ottimizzare
     * @param stationId identificativo della stazione di ricarica target
     * @param simulateWeekend parametro di sola simulazione/demo, inoltrato al Tariff Provider e
     *                        all'Energy Prosumer
     * @return payload aggregato con raccomandazione di ricarica
     */
    @GetMapping("/optimize")
    @Operation(
        summary = "Calcola una raccomandazione di ricarica",
        description = "Avvia quattro integrazioni parallele verso Vehicle Provider, Tariff Provider, Station Provider (SOAP) ed Energy Prosumer, le sincronizza su un'unica barriera e aggrega i risultati in una singola risposta REST. Realizza il coordinamento inter-prosumer del sistema."
    )
    @ApiResponse(responseCode = "200", description = "Ottimizzazione completata con successo")
    @ApiResponse(responseCode = "503", description = "Uno o piu servizi a valle non hanno risposto correttamente")
    public OptimizationResponse optimizeCharging(
        @Parameter(description = "Identificativo del veicolo", example = "EV-001")
        @RequestParam String vehicleId,
        @Parameter(description = "Identificativo della stazione", example = "STATION-FAST-01")
        @RequestParam String stationId,
        @Parameter(description = "Parametro di sola simulazione/demo: forza il Tariff Provider a comportarsi come weekend indipendentemente dal giorno reale")
        @RequestParam(required = false, defaultValue = "false") boolean simulateWeekend
    ) {
        return optimizationService.optimizeCharging(vehicleId, stationId, simulateWeekend);
    }
}