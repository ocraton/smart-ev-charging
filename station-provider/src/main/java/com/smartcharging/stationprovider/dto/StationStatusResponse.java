package com.smartcharging.stationprovider.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * DTO per la risposta dello stato della stazione di ricarica.
 * <p>Per la compatibilita con JAXB/CXF viene modellato come JavaBean classico,
 * con costruttore vuoto e proprieta serializzabili, cosi da evitare errori di
 * bootstrap del contesto SOAP durante la creazione del WSDL.</p>
 * 
 * @param stationId  L'identificativo univoco della colonnina.
 * @param status     Stato operativo (es. AVAILABLE, CHARGING, FAULT).
 * @param maxPowerKw Potenza massima erogabile in kW.
 */
@XmlRootElement(name = "StationStatusResponse", namespace = "http://soap.smartcharging.com/station")
@XmlAccessorType(XmlAccessType.FIELD)
public class StationStatusResponse {

    @XmlElement(required = true)
    private String stationId;

    @XmlElement(required = true)
    private String status;

    @XmlElement(required = true)
    private double maxPowerKw;

    public StationStatusResponse() {
        // Costruttore richiesto da JAXB per la deserializzazione XML.
    }

    public StationStatusResponse(String stationId, String status, double maxPowerKw) {
        this.stationId = stationId;
        this.status = status;
        this.maxPowerKw = maxPowerKw;
    }

    public String getStationId() {
        return stationId;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getMaxPowerKw() {
        return maxPowerKw;
    }

    public void setMaxPowerKw(double maxPowerKw) {
        this.maxPowerKw = maxPowerKw;
    }
}