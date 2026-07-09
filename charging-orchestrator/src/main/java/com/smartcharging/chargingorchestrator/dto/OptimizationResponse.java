package com.smartcharging.chargingorchestrator.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO aggregato restituito dal Charging Orchestrator al termine dell'orchestrazione.
 *
 * <p>Il record combina dati provenienti da provider REST e SOAP in un unico payload
 * immutabile, semplice da serializzare e coerente con le linee guida del progetto.
 * L'obiettivo e offrire al client una vista sintetica sufficiente per prendere una
 * decisione di ricarica senza esporre i dettagli dei singoli servizi interni.</p>
 *
 * @param vehicleId identificativo del veicolo richiesto dal client
 * @param stationId identificativo della stazione richiesta dal client
 * @param batteryCapacity capacita nominale della batteria in kWh
 * @param currentSoC stato di carica corrente del veicolo in percentuale
 * @param maxPower potenza massima disponibile sulla colonnina in kW
 * @param recommendation suggerimento sintetico generato a partire dai dati aggregati
 */
@Schema(description = "Risposta aggregata dell'orchestratore per il piano di ricarica")
public record OptimizationResponse(

    @Schema(description = "Identificativo del veicolo", example = "EV-001")
    String vehicleId,

    @Schema(description = "Identificativo della stazione", example = "STATION-FAST-01")
    String stationId,

    @Schema(description = "Capacita totale della batteria in kWh", example = "50.0")
    double batteryCapacity,

    @Schema(description = "State of Charge corrente espresso in percentuale", example = "60.0")
    double currentSoC,

    @Schema(description = "Potenza massima erogabile dalla stazione in kW", example = "150.0")
    double maxPower,

    @Schema(description = "Raccomandazione sintetica calcolata dal prosumer", example = "Ricarica notturna consigliata")
    String recommendation
) {}