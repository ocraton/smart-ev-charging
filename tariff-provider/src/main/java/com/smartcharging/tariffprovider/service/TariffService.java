package com.smartcharging.tariffprovider.service;

import com.smartcharging.tariffprovider.dto.TariffResponse;
import org.springframework.stereotype.Service;

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
     * @return Lista di 24 oggetti TariffResponse
     */
    public List<TariffResponse> getDailyTariffs() {
        return IntStream.range(0, 24)
            .mapToObj(hour -> {
                // Simulazione fascia oraria: tra le 8 e le 20 l'energia costa 0.35€, altrimenti 0.15€
                double price = (hour >= 8 && hour <= 20) ? 0.35 : 0.15; 
                return new TariffResponse(hour, price);
            })
            .toList();
    }
}