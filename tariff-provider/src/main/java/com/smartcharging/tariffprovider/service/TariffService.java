package com.smartcharging.tariffprovider.service;

import com.smartcharging.tariffprovider.dto.TariffResponse;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Livello di servizio per il recupero delle tariffe energetiche.
 * <p>In conformità con i requisiti del progetto, questo Provider simula
 * l'accesso a un datastore esterno tramite una struttura dati in-memory.
 * La logica di business restituisce tariffe differenziate (Fascia Alta / Fascia Bassa)
 * per permettere al Charging Orchestrator di calcolare un piano di ricarica ottimizzato.</p>
 */
@Service
public class TariffService {

    /**
     * Simula il recupero dei prezzi dell'energia per l'intera giornata.
     *
     * <p>Nei giorni feriali non esiste una vera fascia economica (minimo 0.28€/kWh),
     * mentre nel weekend è disponibile uno sconto notturno profondo (0.15€/kWh): questo
     * rende raggiungibile, nel ChargingOrchestrator, sia il ramo "ricarica notturna/immediata"
     * sia il ramo di default "ricarica distribuita", a seconda del giorno.</p>
     *
     * @param simulateWeekend parametro esclusivamente di simulazione/demo: se {@code true},
     *                        forza il calendario a comportarsi come se fosse weekend
     *                        indipendentemente dal giorno reale, per poter dimostrare a comando
     *                        entrambi gli scenari senza dover aspettare un vero sabato/domenica
     * @return Lista di 24 oggetti TariffResponse
     */
    public List<TariffResponse> getDailyTariffs(boolean simulateWeekend) {
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        boolean isWeekend = simulateWeekend || today == DayOfWeek.SATURDAY || today == DayOfWeek.SUNDAY;

        return IntStream.range(0, 24)
            .mapToObj(hour -> {
                double price;
                if (hour >= 8 && hour <= 20) {
                    // Fascia diurna, invariata sia nei feriali che nel weekend
                    price = 0.35;
                } else if (isWeekend) {
                    // Weekend: domanda di rete più bassa, sconto notturno profondo
                    price = 0.15;
                } else {
                    // Giorni feriali: nessuno sconto profondo, solo una fascia intermedia
                    price = 0.28;
                }
                return new TariffResponse(hour, price);
            })
            .toList();
    }
}