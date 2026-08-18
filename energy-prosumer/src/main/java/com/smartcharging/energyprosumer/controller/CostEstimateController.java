package com.smartcharging.energyprosumer.controller;

import com.smartcharging.energyprosumer.dto.CostEstimateResponse;
import com.smartcharging.energyprosumer.service.CostEstimateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST che espone la valutazione economica sincrona dell'Energy Prosumer.
 *
 * <p>A differenza di {@link SimulationController}, che realizza il pattern asincrono di FLOW-02,
 * questo endpoint e volutamente sincrono e a bassa latenza, perche il suo consumatore principale
 * e un altro prosumer: il Charging Orchestrator lo invoca all'interno della propria orchestrazione
 * parallela di FLOW-01 e ne attende l'esito sulla barriera di sincronizzazione. Un pattern a
 * ticket sarebbe qui del tutto controproducente, perche introdurrebbe un secondo giro di polling
 * dentro una richiesta che verso l'utente finale deve restare sincrona.</p>
 *
 * <p>L'endpoint e comunque instradabile anche dall'esterno attraverso l'API Gateway, coerentemente
 * con la regola per cui ogni capacita di un prosumer e raggiungibile dal client solo tramite il
 * punto di ingresso unico.</p>
 */
@RestController
@RequestMapping("/api/v1/energy")
@Tag(
    name = "Energy Cost Estimate",
    description = "API Prosumer per la valutazione economica sincrona della ricarica, usata nel coordinamento inter-prosumer di FLOW-01"
)
public class CostEstimateController {

    private final CostEstimateService costEstimateService;

    /**
     * Inietta il servizio applicativo tramite costruttore.
     *
     * @param costEstimateService servizio che calcola la valutazione economica
     */
    public CostEstimateController(CostEstimateService costEstimateService) {
        this.costEstimateService = costEstimateService;
    }

    /**
     * Restituisce il costo immediato della ricarica e il risparmio ottenibile differendola.
     *
     * @param vehicleId identificativo del veicolo da valutare
     * @param simulateWeekend parametro di sola simulazione/demo, inoltrato al Tariff Provider
     * @return valutazione economica completa della ricarica
     */
    @GetMapping("/cost-estimate")
    @Operation(
        summary = "Valuta il costo della ricarica e il risparmio differibile",
        description = "Interroga Tariff Provider e Vehicle Provider e restituisce costo immediato, costo alla tariffa minima e risparmio potenziale. E il contributo dell'Energy Prosumer all'orchestrazione parallela del Charging Orchestrator."
    )
    @ApiResponse(responseCode = "200", description = "Valutazione economica calcolata con successo")
    @ApiResponse(responseCode = "503", description = "Uno dei provider a valle non ha risposto correttamente")
    public CostEstimateResponse estimateCost(
        @Parameter(description = "Identificativo del veicolo", example = "EV-001")
        @RequestParam String vehicleId,
        @Parameter(description = "Parametro di sola simulazione/demo: forza il Tariff Provider a comportarsi come weekend indipendentemente dal giorno reale")
        @RequestParam(required = false, defaultValue = "false") boolean simulateWeekend
    ) {
        return costEstimateService.estimate(vehicleId, simulateWeekend);
    }
}
