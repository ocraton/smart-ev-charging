package com.smartcharging.stationprovider.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * DTO per la risposta dello stato della stazione di ricarica.
 * <p>Modellato come Java Record per garantire immutabilità strutturale.
 * Grazie a JAXB 4.0 (incluso in Jakarta EE 10 e Spring Boot 3.x), è possibile
 * serializzare direttamente i record in XML senza dover creare classi mutabili
 * con costruttori vuoti e metodi setter.</p>
 * 
 * @param stationId  L'identificativo univoco della colonnina.
 * @param status     Stato operativo (es. AVAILABLE, CHARGING, FAULT).
 * @param maxPowerKw Potenza massima erogabile in kW.
 */
@XmlRootElement(name = "StationStatusResponse", namespace = "http://soap.smartcharging.com/station")
@XmlAccessorType(XmlAccessType.FIELD)
public record StationStatusResponse(
    @XmlElement(required = true) String stationId,
    @XmlElement(required = true) String status,
    @XmlElement(required = true) double maxPowerKw
) {}