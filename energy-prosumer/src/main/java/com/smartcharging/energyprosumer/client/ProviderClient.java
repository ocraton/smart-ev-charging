package com.smartcharging.energyprosumer.client;

import com.smartcharging.energyprosumer.exception.ProviderUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Adapter di integrazione verso i due provider REST consumati dall'Energy Prosumer.
 *
 * <p>Questo componente esiste perche entrambi i casi d'uso del prosumer hanno bisogno degli
 * stessi due dati di base: la simulazione asincrona di FLOW-02 e la stima di costo sincrona
 * usata nel coordinamento inter-prosumer di FLOW-01. Centralizzare qui le chiamate evita di
 * duplicare gli URL logici e la gestione degli errori in due service distinti, e concentra in
 * un solo punto la conoscenza dei contratti altrui.</p>
 *
 * <p>Gli indirizzi usati sono <em>nomi logici</em> ({@code http://tariff-provider/...}) e non
 * host fisici: la {@link RestTemplate} iniettata e annotata con {@code @LoadBalanced}, quindi
 * Spring Cloud LoadBalancer risolve il service-id sulla cache locale del registro Eureka e
 * seleziona un'istanza fra quelle disponibili. E questo che rende trasparente lo scale-out dei
 * provider senza toccare una riga di configurazione di questo modulo.</p>
 */
@Component
public class ProviderClient {

    private final RestTemplate restTemplate;

    /**
     * Inietta il client HTTP load-balanced come dipendenza immutabile.
     *
     * @param restTemplate client HTTP integrato con Spring Cloud LoadBalancer
     */
    public ProviderClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Recupera dal Tariff Provider le 24 fasce tariffarie della giornata corrente.
     *
     * @param simulateWeekend parametro di sola simulazione/demo, inoltrato al provider per
     *                        forzare a comando lo scenario di sconto notturno del weekend
     * @return array delle fasce orarie; mai {@code null}, al piu un array vuoto
     * @throws ProviderUnavailableException se il provider non risponde correttamente
     */
    public TariffData[] fetchDailyTariffs(boolean simulateWeekend) {
        try {
            TariffData[] tariffs = restTemplate.getForObject(
                "http://tariff-provider/api/v1/tariffs/daily?simulateWeekend={simulateWeekend}",
                TariffData[].class,
                simulateWeekend
            );
            return tariffs == null ? new TariffData[0] : tariffs;
        } catch (RestClientException e) {
            throw new ProviderUnavailableException("Tariff Provider non raggiungibile o in errore", e);
        }
    }

    /**
     * Recupera dal Vehicle Provider lo stato telemetrico del veicolo richiesto.
     *
     * @param vehicleId identificativo logico del veicolo
     * @return stato telemetrico con capacita batteria e stato di carica corrente
     * @throws ProviderUnavailableException se il provider non risponde correttamente
     */
    public VehicleStatusData fetchVehicleStatus(String vehicleId) {
        try {
            return restTemplate.getForObject(
                "http://vehicle-provider/api/v1/vehicles/{vehicleId}/status",
                VehicleStatusData.class,
                vehicleId
            );
        } catch (RestClientException e) {
            throw new ProviderUnavailableException("Vehicle Provider non raggiungibile o in errore", e);
        }
    }
}
