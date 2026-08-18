package com.smartcharging.energyprosumer.service;

import com.smartcharging.energyprosumer.client.ProviderClient;
import com.smartcharging.energyprosumer.client.TariffData;
import com.smartcharging.energyprosumer.client.VehicleStatusData;
import com.smartcharging.energyprosumer.dto.CostEstimateResponse;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Servizio applicativo che produce la valutazione economica sincrona di una ricarica.
 *
 * <p>Questo e il "mestiere" che l'Energy Prosumer svolge in parallelo al Charging Orchestrator
 * durante FLOW-01. Per calcolarlo il prosumer deve consumare due provider distinti, il Tariff
 * Provider e il Vehicle Provider: senza le tariffe non conosce il prezzo, senza la telemetria
 * non conosce la quantita di energia da acquistare. Il risultato viene poi restituito
 * all'Orchestrator, che lo aggrega con i propri dati tecnici prima di rispondere al client.</p>
 *
 * <p>Il servizio e completamente <em>stateless</em>: a differenza di
 * {@link SimulationService}, che custodisce lo stato dei ticket in memoria, questo percorso non
 * conserva nulla fra due invocazioni e sarebbe quindi replicabile orizzontalmente senza alcun
 * accorgimento.</p>
 */
@Service
public class CostEstimateService {

    private final ProviderClient providerClient;

    /**
     * Inietta l'adapter di integrazione verso i provider come dipendenza immutabile.
     *
     * @param providerClient adapter che incapsula le chiamate load-balanced ai provider
     */
    public CostEstimateService(ProviderClient providerClient) {
        this.providerClient = providerClient;
    }

    /**
     * Calcola il costo immediato della ricarica e il risparmio ottenibile differendola.
     *
     * <p>Le due letture verso i provider sono sequenziali e non parallelizzate: si tratta di due
     * accessi rapidi in sola lettura, e il valore aggiunto di questo metodo sta nel calcolo
     * economico, non nella riduzione della latenza. Il parallelismo che la traccia richiede e
     * realizzato un livello piu in alto, fra questo prosumer e il Charging Orchestrator, che
     * eseguono i rispettivi lavori simultaneamente.</p>
     *
     * @param vehicleId identificativo del veicolo da valutare
     * @param simulateWeekend parametro di sola simulazione/demo inoltrato al Tariff Provider
     * @return valutazione economica completa della ricarica
     */
    public CostEstimateResponse estimate(String vehicleId, boolean simulateWeekend) {
        TariffData[] dailyTariffs = providerClient.fetchDailyTariffs(simulateWeekend);
        VehicleStatusData vehicleStatus = providerClient.fetchVehicleStatus(vehicleId);

        int currentHour = LocalTime.now().getHour();

        // Fascia piu economica della giornata. Se il provider restituisse un listino vuoto si
        // ricade su un valore di riferimento prudenziale, cosi la stima resta sempre definita.
        TariffData cheapestSlot = Arrays.stream(dailyTariffs)
            .min(Comparator.comparingDouble(TariffData::pricePerKwh))
            .orElse(new TariffData(currentHour, 0.30d));

        // Tariffa applicata adesso: se l'ora corrente non compare nel listino si assume, in modo
        // conservativo, la tariffa piu cara della giornata.
        double currentPrice = Arrays.stream(dailyTariffs)
            .filter(tariff -> tariff.hour() == currentHour)
            .mapToDouble(TariffData::pricePerKwh)
            .findFirst()
            .orElseGet(() -> Arrays.stream(dailyTariffs)
                .mapToDouble(TariffData::pricePerKwh)
                .max()
                .orElse(cheapestSlot.pricePerKwh()));

        double energyNeededKwh = vehicleStatus == null
            ? 0.0
            : vehicleStatus.batteryCapacityKwh() * (1 - vehicleStatus.currentSoC() / 100.0);

        double costNowEur = energyNeededKwh * currentPrice;
        double costAtCheapestEur = energyNeededKwh * cheapestSlot.pricePerKwh();

        return new CostEstimateResponse(
            vehicleStatus != null ? vehicleStatus.vehicleId() : vehicleId,
            round2(energyNeededKwh),
            currentHour,
            currentPrice,
            round2(costNowEur),
            cheapestSlot.hour(),
            cheapestSlot.pricePerKwh(),
            round2(costAtCheapestEur),
            round2(costNowEur - costAtCheapestEur)
        );
    }

    /**
     * Arrotonda un importo a due cifre decimali, per evitare che l'aritmetica in virgola mobile
     * produca valori come 1.4000000000000004 nelle risposte JSON.
     *
     * @param value valore da arrotondare
     * @return valore arrotondato al centesimo
     */
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
