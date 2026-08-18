package com.smartcharging.energyprosumer.client;

/**
 * Rappresentazione locale del payload REST esposto dal Tariff Provider.
 *
 * <p>Il record vive nel package {@code client} e non in {@code dto} perche non fa parte del
 * contratto pubblico di questo servizio: e la vista che l'Energy Prosumer ha del contratto
 * <em>altrui</em>. Tenerla separata dai DTO di risposta evita che un'evoluzione del formato
 * del Tariff Provider si propaghi per errore nell'API esposta ai nostri consumatori.</p>
 *
 * @param hour ora della fascia tariffaria, nel formato 0-23
 * @param pricePerKwh costo dell'energia in euro per kWh nella fascia considerata
 */
public record TariffData(int hour, double pricePerKwh) {
}
