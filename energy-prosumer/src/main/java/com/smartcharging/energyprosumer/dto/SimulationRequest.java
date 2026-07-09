package com.smartcharging.energyprosumer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload di avvio simulazione per FLOW-02.
 *
 * <p>Il record e volutamente vuoto in questa fase per mantenere il contratto
 * estendibile senza introdurre campi non ancora richiesti dalla traccia.</p>
 */
@Schema(
    name = "SimulationRequest",
    description = "Payload di richiesta per l'avvio della simulazione asincrona dei risparmi di rete"
)
public record SimulationRequest() {
}
