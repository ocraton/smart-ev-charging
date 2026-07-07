package com.smartcharging.tariffprovider.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO che rappresenta il costo dell'energia in una determinata fascia oraria.
 * <p>Modellato come Java Record per garantire immutabilità strutturale, in linea
 * con le best practice di sviluppo per i payload REST. Jackson lo serializzerà 
 * automaticamente in JSON.</p>
 */
@Schema(description = "Modello che rappresenta il prezzo dell'energia per una singola ora")
public record TariffResponse(
    
    @Schema(description = "Ora del giorno (formato 0-23)", example = "14")
    int hour,
    
    @Schema(description = "Prezzo al kWh espresso in Euro", example = "0.35")
    double pricePerKwh
) {}