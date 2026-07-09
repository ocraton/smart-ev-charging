package com.smartcharging.energyprosumer.controller;

import com.smartcharging.energyprosumer.dto.SimulationRequest;
import com.smartcharging.energyprosumer.dto.SimulationResponse;
import com.smartcharging.energyprosumer.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST per il pattern di polling asincrono FLOW-02.
 *
 * <p>Espone un endpoint di avvio che risponde immediatamente con HTTP 202
 * e un endpoint di stato interrogabile periodicamente dal client fino al
 * completamento della simulazione.</p>
 */
@RestController
@RequestMapping("/api/simulations")
@Tag(name = "Simulation", description = "API per simulazioni asincrone dei risparmi energetici")
public class SimulationController {

    private final SimulationService simulationService;

    /**
     * Iniezione del servizio applicativo tramite costruttore.
     *
     * @param simulationService servizio responsabile dello stato dei ticket
     */
    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    /**
     * Avvia la simulazione in background e restituisce ticket immediatamente.
     *
     * @param request payload di richiesta simulazione
     * @return risposta HTTP 202 con ticketId e stato iniziale PENDING
     */
    @PostMapping("/grid-savings")
    @Operation(
        summary = "Avvia simulazione risparmio rete",
        description = "Innesca il processo asincrono e restituisce subito un ticket di polling senza bloccare la richiesta client."
    )
    @ApiResponse(responseCode = "202", description = "Simulazione accettata e ticket generato")
    public ResponseEntity<SimulationResponse> startGridSavingsSimulation(@RequestBody SimulationRequest request) {
        String ticketId = simulationService.startAsyncSimulation(request);
        SimulationResponse response = new SimulationResponse(ticketId, "PENDING", null);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Restituisce lo stato corrente della simulazione richiesta dal ticket.
     *
     * @param ticketId identificativo assegnato all'avvio della simulazione
     * @return HTTP 200 con stato RUNNING o COMPLETED
     */
    @GetMapping("/status/{ticketId}")
    @Operation(
        summary = "Controlla stato simulazione",
        description = "Ritorna RUNNING fino al completamento del job asincrono, poi COMPLETED con risultato sintetico."
    )
    @ApiResponse(responseCode = "200", description = "Stato simulazione recuperato")
    public ResponseEntity<SimulationResponse> getSimulationStatus(@PathVariable String ticketId) {
        String currentStatus = simulationService.getSimulationStatus(ticketId);

        if ("COMPLETED".equals(currentStatus)) {
            SimulationResponse completed = new SimulationResponse(ticketId, "COMPLETED", "Simulation finished successfully.");
            return ResponseEntity.ok(completed);
        }

        SimulationResponse running = new SimulationResponse(ticketId, "RUNNING", null);
        return ResponseEntity.ok(running);
    }
}