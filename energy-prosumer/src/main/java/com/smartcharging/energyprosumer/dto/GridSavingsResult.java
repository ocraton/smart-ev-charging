package com.smartcharging.energyprosumer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Esito della simulazione di risparmio energetico calcolato su dati reali
 * recuperati da TariffProvider e VehicleProvider (FLOW-02).
 *
 * @param vehicleId veicolo di riferimento usato per la simulazione
 * @param energyNeededKwh energia stimata necessaria a completare la ricarica
 * @param peakPricePerKwh prezzo massimo rilevato nella fascia tariffaria giornaliera
 * @param offPeakPricePerKwh prezzo minimo rilevato nella fascia tariffaria giornaliera
 * @param cheapestHour ora del giorno con il prezzo piu basso
 * @param estimatedSavingsEur risparmio stimato spostando la ricarica dalla fascia di picco a quella piu economica
 */
@Schema(description = "Risultato della simulazione di risparmio energetico basata su dati reali dei provider")
public record GridSavingsResult(
    String vehicleId,
    double energyNeededKwh,
    double peakPricePerKwh,
    double offPeakPricePerKwh,
    int cheapestHour,
    double estimatedSavingsEur
) {
}
