package com.smartcharging.energyprosumer.client;

/**
 * Rappresentazione locale del payload REST esposto dal Vehicle Provider.
 *
 * <p>Come {@link TariffData}, modella il contratto di un servizio esterno e non l'API di questo
 * modulo. Vengono mappati solo i campi effettivamente usati dalla logica dell'Energy Prosumer:
 * Jackson ignora silenziosamente le proprieta aggiuntive, quindi il provider puo arricchire la
 * propria risposta senza rompere questo consumatore.</p>
 *
 * @param vehicleId identificativo logico del veicolo
 * @param batteryCapacityKwh capacita nominale della batteria in kWh
 * @param currentSoC stato di carica corrente espresso in percentuale
 */
public record VehicleStatusData(String vehicleId, double batteryCapacityKwh, double currentSoC) {
}
