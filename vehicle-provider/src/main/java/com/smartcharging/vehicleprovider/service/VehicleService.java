package com.smartcharging.vehicleprovider.service;

import com.smartcharging.vehicleprovider.dto.VehicleStatusResponse;
import org.springframework.stereotype.Service;

/**
 * Livello di servizio per il recupero della telemetria veicolo.
 *
 * <p>In questa fase del progetto la sorgente dati e simulata in-memory,
 * in conformita con i vincoli di traccia che non richiedono persistenza reale.
 * Il servizio restituisce una risposta coerente e deterministica a partire
 * dal vehicleId richiesto dal client.</p>
 */
@Service
public class VehicleService {

    /**
     * Restituisce lo stato del veicolo richiesto usando valori simulati.
     *
     * @param vehicleId identificativo del veicolo elettrico
     * @return stato telemetrico con capacita batteria e SoC corrente
     */
    public VehicleStatusResponse getVehicleStatus(String vehicleId) {
        return new VehicleStatusResponse(vehicleId, 50.0, 60.0);
    }
}
