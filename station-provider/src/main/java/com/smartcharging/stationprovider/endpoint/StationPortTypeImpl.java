package com.smartcharging.stationprovider.endpoint;

import com.smartcharging.stationprovider.dto.StationStatusResponse;
import jakarta.jws.WebService;
import org.springframework.stereotype.Service;

/**
 * Implementazione del Web Service SOAP per lo StationProvider.
 * <p>Fornisce la logica di business per gestire lo stato fisico delle colonnine.
 * I dati sono simulati in-memory per rispettare i vincoli architetturali del progetto
 * (nessun database esterno complesso).</p>
 */
@Service
@WebService(
    serviceName = "StationService",
    portName = "StationPort",
    targetNamespace = "http://soap.smartcharging.com/station",
    endpointInterface = "com.smartcharging.stationprovider.endpoint.StationPortType"
)
public class StationPortTypeImpl implements StationPortType {

    @Override
    public StationStatusResponse getStationStatus(String stationId) {
        // Simulazione logica hardware: se l'ID contiene "FAST", la potenza è 150kW, altrimenti 22kW.
        double power = (stationId != null && stationId.contains("FAST")) ? 150.0 : 22.0;
        
        // Simuliamo che l'infrastruttura sia sempre disponibile per il test FLOW-01
        return new StationStatusResponse(stationId, "AVAILABLE", power);
    }
}