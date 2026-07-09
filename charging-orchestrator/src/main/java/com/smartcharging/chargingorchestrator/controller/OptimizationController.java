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
 * <p>Espone verso il Gateway un endpoint di lettura che aggrega informazioni provenienti dai
 * provider interni e restituisce una risposta già arricchita. L'endpoint resta sincrono dal
 * punto di vista del client, mentre la concorrenza necessaria viene gestita all'interno del
 * service applicativo tramite CompletableFuture ed executor dedicato.</p>
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
     * Aggrega in parallelo dati del veicolo, delle tariffe e della stazione di ricarica.
     *
     * @param vehicleId identificativo del veicolo da ottimizzare
     * @param stationId identificativo della stazione di ricarica target
     * @return payload aggregato con raccomandazione di ricarica
     */
    @GetMapping("/optimize")
    @Operation(
        summary = "Calcola una raccomandazione di ricarica",
        description = "Avvia tre integrazioni parallele verso Vehicle Provider, Tariff Provider e Station Provider, quindi aggrega i risultati in una singola risposta REST."
    )
    @ApiResponse(responseCode = "200", description = "Ottimizzazione completata con successo")
    public OptimizationResponse optimizeCharging(
        @Parameter(description = "Identificativo del veicolo", example = "EV-001")
        @RequestParam String vehicleId,
        @Parameter(description = "Identificativo della stazione", example = "STATION-FAST-01")
        @RequestParam String stationId
    ) {
        return optimizationService.optimizeCharging(vehicleId, stationId);
    }
}