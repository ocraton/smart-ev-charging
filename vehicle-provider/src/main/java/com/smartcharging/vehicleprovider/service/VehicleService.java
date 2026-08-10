package com.smartcharging.vehicleprovider.service;

import com.smartcharging.vehicleprovider.dto.VehicleStatusResponse;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Livello di servizio per il recupero della telemetria veicolo.
 *
 * <p>In questa fase del progetto la sorgente dati e simulata in-memory,
 * in conformita con i vincoli di traccia che non richiedono persistenza reale.
 * Il servizio restituisce una risposta coerente e deterministica a partire
 * dal vehicleId richiesto dal client.</p>
 *
 * <p>Il catalogo qui sotto e esclusivamente di simulazione/demo: associa a pochi
 * vehicleId noti profili di batteria diversi (in particolare un SoC basso su
 * EV-002), cosi da poter dimostrare a comando anche lo scenario di ricarica
 * urgente in ChargingOrchestrator senza dover modellare una vera flotta.</p>
 */
@Service
public class VehicleService {

    private static final Map<String, VehicleProfile> SIMULATED_FLEET = Map.of(
        "EV-001", new VehicleProfile(50.0, 60.0),
        "EV-002", new VehicleProfile(50.0, 20.0)
    );

    private static final VehicleProfile DEFAULT_PROFILE = new VehicleProfile(50.0, 60.0);

    /**
     * Restituisce lo stato del veicolo richiesto usando valori simulati.
     *
     * @param vehicleId identificativo del veicolo elettrico
     * @return stato telemetrico con capacita batteria e SoC corrente
     */
    public VehicleStatusResponse getVehicleStatus(String vehicleId) {
        VehicleProfile profile = SIMULATED_FLEET.getOrDefault(vehicleId, DEFAULT_PROFILE);
        return new VehicleStatusResponse(vehicleId, profile.batteryCapacityKwh(), profile.currentSoC());
    }

    /**
     * Profilo di batteria simulato associato a un vehicleId noto.
     */
    private record VehicleProfile(double batteryCapacityKwh, double currentSoC) {
    }
}
