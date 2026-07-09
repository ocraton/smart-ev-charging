package com.smartcharging.vehicleprovider.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO di risposta che rappresenta lo stato telemetrico di un veicolo elettrico.
 *
 * <p>Il modello e definito come Java Record per garantire immutabilita,
 * semplificare il codice e mantenere un payload REST chiaro e prevedibile.
 * I campi sono documentati con annotazioni OpenAPI per la generazione automatica
 * della specifica Swagger.</p>
 */
@Schema(description = "Stato telemetrico sintetico del veicolo elettrico")
public record VehicleStatusResponse(

    @Schema(description = "Identificativo univoco del veicolo", example = "EV-001")
    String vehicleId,

    @Schema(description = "Capacita totale della batteria in kWh", example = "50.0")
    double batteryCapacityKwh,

    @Schema(description = "State of Charge corrente espresso in percentuale", example = "60.0")
    double currentSoC
) {}
