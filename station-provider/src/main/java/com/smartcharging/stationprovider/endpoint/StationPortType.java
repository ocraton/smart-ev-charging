package com.smartcharging.stationprovider.endpoint;

import com.smartcharging.stationprovider.dto.StationStatusResponse;
import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebService;

/**
 * Interfaccia del Web Service SOAP per la gestione dello stato delle stazioni di ricarica.
 * <p>Definisce il contratto esposto dal StationProvider. L'uso delle annotazioni
 * jakarta.jws.* garantisce la conformità con lo standard Jakarta XML Web Services.
 * Il namespace dichiarato qui deve corrispondere a quello del DTO e dell'implementazione.</p>
 */
@WebService(
    name = "StationPort",
    targetNamespace = "http://soap.smartcharging.com/station"
)
public interface StationPortType {

    /**
     * Recupera lo stato attuale di una stazione di ricarica tramite il suo ID.
     */
    @WebMethod(operationName = "GetStationStatus")
    StationStatusResponse getStationStatus(
        @WebParam(name = "stationId", targetNamespace = "http://soap.smartcharging.com/station")
        String stationId
    );
}