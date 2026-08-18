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
 * vehicleId noti profili di batteria diversi, cosi da poter dimostrare a comando
 * tutti i rami di raccomandazione del ChargingOrchestrator senza dover modellare
 * una vera flotta.</p>
 *
 * <ul>
 *   <li><strong>EV-001</strong> (SoC 60%): caso nominale, con un fabbisogno di energia
 *       sufficiente a rendere conveniente il differimento della ricarica.</li>
 *   <li><strong>EV-002</strong> (SoC 20%): caso critico, che innesca la raccomandazione di
 *       ricarica urgente quando abbinato a una colonnina rapida.</li>
 *   <li><strong>EV-003</strong> (SoC 90%): veicolo quasi carico. Il fabbisogno residuo e
 *       cosi ridotto che, pur a parita di differenziale tariffario, il risparmio assoluto
 *       ottenibile differendo la ricarica non giustifica l'indisponibilita del veicolo.
 *       E il profilo che rende osservabile il valore del coordinamento inter-prosumer:
 *       la decisione dipende dal risparmio <em>in euro</em> calcolato dall'Energy Prosumer,
 *       non dal solo prezzo unitario noto all'Orchestrator.</li>
 * </ul>
 */
@Service
public class VehicleService {

    private static final Map<String, VehicleProfile> SIMULATED_FLEET = Map.of(
        "EV-001", new VehicleProfile(50.0, 60.0),
        "EV-002", new VehicleProfile(50.0, 20.0),
        "EV-003", new VehicleProfile(50.0, 90.0)
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
