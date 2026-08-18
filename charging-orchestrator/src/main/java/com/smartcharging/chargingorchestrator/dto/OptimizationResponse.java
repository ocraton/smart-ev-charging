package com.smartcharging.chargingorchestrator.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO aggregato restituito dal Charging Orchestrator al termine dell'orchestrazione.
 *
 * <p>Il record combina in un unico payload immutabile dati provenienti da quattro servizi
 * eterogenei: due provider REST, un provider SOAP e un secondo prosumer. Le prime proprieta
 * descrivono i vincoli <em>tecnici</em> della ricarica, raccolti dall'Orchestrator stesso presso
 * i provider; quelle economiche sono invece il contributo dell'Energy Prosumer, calcolato in
 * parallelo e aggregato dopo la barriera di sincronizzazione.</p>
 *
 * <p>L'obiettivo e offrire al client una vista sintetica sufficiente a prendere una decisione di
 * ricarica senza esporre i dettagli dei singoli servizi interni, ne tantomeno il fatto che uno
 * dei dati provenga da un servizio SOAP e un altro da un prosumer distinto.</p>
 *
 * @param vehicleId identificativo del veicolo richiesto dal client
 * @param stationId identificativo della stazione richiesta dal client
 * @param batteryCapacity capacita nominale della batteria in kWh
 * @param currentSoC stato di carica corrente del veicolo in percentuale
 * @param maxPower potenza massima disponibile sulla colonnina in kW
 * @param energyNeededKwh energia mancante per completare la ricarica, valutata dall'Energy Prosumer
 * @param estimatedCostNowEur costo stimato ricaricando immediatamente
 * @param estimatedCostAtCheapestEur costo stimato ricaricando nella fascia oraria piu economica
 * @param potentialSavingsEur risparmio ottenibile differendo la ricarica
 * @param cheapestHour ora del giorno in cui si applica la tariffa minima
 * @param recommendation raccomandazione sintetica generata aggregando vincoli tecnici ed economici
 */
@Schema(description = "Risposta aggregata dell'orchestratore: vincoli tecnici raccolti dai provider e valutazione economica prodotta dall'Energy Prosumer")
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

    @Schema(description = "Energia mancante per completare la ricarica, in kWh", example = "20.0")
    double energyNeededKwh,

    @Schema(description = "Costo stimato ricaricando immediatamente, in euro", example = "7.0")
    double estimatedCostNowEur,

    @Schema(description = "Costo stimato ricaricando nella fascia piu economica, in euro", example = "3.0")
    double estimatedCostAtCheapestEur,

    @Schema(description = "Risparmio ottenibile differendo la ricarica, in euro", example = "4.0")
    double potentialSavingsEur,

    @Schema(description = "Ora del giorno con la tariffa minima", example = "0")
    int cheapestHour,

    @Schema(description = "Raccomandazione sintetica calcolata aggregando i contributi dei due prosumer", example = "Ricarica differita alle 00:00 consigliata")
    String recommendation
) {}
