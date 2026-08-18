package com.smartcharging.energyprosumer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Valutazione economica sincrona della ricarica di un veicolo, prodotta dall'Energy Prosumer.
 *
 * <p>E il contributo che questo prosumer porta al coordinamento parallelo di FLOW-01: mentre il
 * Charging Orchestrator raccoglie i vincoli <em>tecnici</em> della ricarica (stato di carica del
 * veicolo, potenza erogabile dalla colonnina), l'Energy Prosumer risponde alla domanda
 * <em>economica</em> complementare, ossia quanto costa ricaricare adesso e quanto si
 * risparmierebbe attendendo la fascia oraria piu conveniente della giornata.</p>
 *
 * <p>La separazione delle responsabilita e deliberata: nessuno dei due prosumer possiede da solo
 * gli elementi per formulare la raccomandazione finale, ed e per questo che devono lavorare in
 * parallelo e sincronizzarsi prima di rispondere al client.</p>
 *
 * @param vehicleId identificativo del veicolo valutato
 * @param energyNeededKwh energia mancante per portare la batteria al 100 per cento
 * @param currentHour ora del giorno usata come riferimento per il costo immediato
 * @param currentPricePerKwh tariffa applicata nell'ora corrente
 * @param costNowEur costo stimato ricaricando immediatamente
 * @param cheapestHour ora del giorno in cui si applica la tariffa minima
 * @param cheapestPricePerKwh tariffa minima della giornata
 * @param costAtCheapestEur costo stimato ricaricando nella fascia piu economica
 * @param potentialSavingsEur risparmio ottenibile differendo la ricarica alla fascia minima
 */
@Schema(description = "Valutazione economica della ricarica prodotta dall'Energy Prosumer per il coordinamento inter-prosumer")
public record CostEstimateResponse(

    @Schema(description = "Identificativo del veicolo valutato", example = "EV-001")
    String vehicleId,

    @Schema(description = "Energia mancante per completare la ricarica, in kWh", example = "20.0")
    double energyNeededKwh,

    @Schema(description = "Ora del giorno usata come riferimento per il costo immediato", example = "14")
    int currentHour,

    @Schema(description = "Tariffa applicata nell'ora corrente, in euro per kWh", example = "0.35")
    double currentPricePerKwh,

    @Schema(description = "Costo stimato ricaricando immediatamente, in euro", example = "7.0")
    double costNowEur,

    @Schema(description = "Ora del giorno con la tariffa minima", example = "0")
    int cheapestHour,

    @Schema(description = "Tariffa minima della giornata, in euro per kWh", example = "0.15")
    double cheapestPricePerKwh,

    @Schema(description = "Costo stimato ricaricando nella fascia piu economica, in euro", example = "3.0")
    double costAtCheapestEur,

    @Schema(description = "Risparmio ottenibile differendo la ricarica, in euro", example = "4.0")
    double potentialSavingsEur
) {
}
