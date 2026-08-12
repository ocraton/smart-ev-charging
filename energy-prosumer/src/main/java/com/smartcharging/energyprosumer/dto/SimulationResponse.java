package com.smartcharging.energyprosumer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * DTO di risposta del pattern di polling asincrono.
 *
 * <p>Il ticketId consente al client di interrogare periodicamente lo stato,
 * mentre il campo result viene popolato solo a completamento dell'elaborazione.</p>
 *
 * @param ticketId identificativo univoco della simulazione asincrona
 * @param status stato della simulazione (PENDING, RUNNING o COMPLETED)
 * @param result contenuto del risultato finale; nullo finche l'elaborazione non termina
 * @param legend descrizione sintetica dei campi di result, valorizzata solo quando COMPLETED
 *               (solo di supporto alla lettura della risposta grezza, dato che il JSON non
 *               supporta commenti veri e propri)
 */
@Schema(
    name = "SimulationResponse",
    description = "Risposta del workflow asincrono con ticket di polling e stato di avanzamento"
)
public record SimulationResponse(
    @Schema(description = "Identificativo univoco del ticket di simulazione", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
    String ticketId,
    @Schema(description = "Stato corrente della simulazione", example = "PENDING")
    String status,
    @Schema(description = "Risultato finale della simulazione, valorizzato solo quando COMPLETED")
    Object result,
    @Schema(description = "Legenda dei campi di result, di sola lettura/demo, valorizzata solo quando COMPLETED")
    Map<String, String> legend
) {
}
