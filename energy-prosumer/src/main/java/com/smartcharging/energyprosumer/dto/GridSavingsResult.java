package com.smartcharging.energyprosumer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /**
     * Descrizione sintetica di ciascun campo di questo risultato, pensata per essere allegata
     * come "legenda" nella risposta JSON (dato che il formato JSON non supporta commenti veri
     * e propri) così chi ispeziona la risposta grezza capisce subito il significato dei valori.
     *
     * <p>Usa LinkedHashMap (non Map.of, che non garantisce l'ordine) così l'ordine di
     * serializzazione della legenda rispecchia quello dei campi del record qui sopra.</p>
     */
    public static final Map<String, String> FIELD_LEGEND;

    static {
        Map<String, String> legend = new LinkedHashMap<>();
        legend.put("vehicleId", "Veicolo per cui è stata calcolata la simulazione");
        legend.put("energyNeededKwh", "Energia (kWh) mancante per portare la batteria al 100%");
        legend.put("peakPricePerKwh", "Tariffa più cara della giornata (fascia di punta)");
        legend.put("offPeakPricePerKwh", "Tariffa più economica trovata tra le 24 fasce orarie");
        legend.put("cheapestHour", "Ora del giorno (0-23) in cui si applica la tariffa minima");
        legend.put("estimatedSavingsEur", "Risparmio stimato caricando l'energia necessaria alla tariffa minima invece che a quella di picco");
        FIELD_LEGEND = Collections.unmodifiableMap(legend);
    }
}
