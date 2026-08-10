package com.smartcharging.energyprosumer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload di avvio simulazione per FLOW-02.
 *
 * <p>Il campo vehicleId e opzionale: se assente viene usato un veicolo di
 * riferimento di default, cosi il contratto resta compatibile con client
 * che non lo valorizzano ancora.</p>
 */
@Schema(
    name = "SimulationRequest",
    description = "Payload di richiesta per l'avvio della simulazione asincrona dei risparmi di rete"
)
public record SimulationRequest(
    @Schema(description = "Veicolo di riferimento per la simulazione", example = "EV-001")
    String vehicleId,

    @Schema(description = "Parametro di sola simulazione/demo: forza il Tariff Provider a comportarsi come weekend indipendentemente dal giorno reale", example = "false")
    boolean simulateWeekend
) {
}
