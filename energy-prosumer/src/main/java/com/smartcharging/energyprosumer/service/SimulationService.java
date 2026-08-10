package com.smartcharging.energyprosumer.service;

import com.smartcharging.energyprosumer.dto.GridSavingsResult;
import com.smartcharging.energyprosumer.dto.SimulationRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Servizio applicativo per la gestione dello stato delle simulazioni asincrone.
 *
 * <p>L'implementazione usa una memoria in-process con {@link ConcurrentHashMap}
 * per modellare il ciclo di vita dei ticket senza persistenza esterna, conforme
 * all'approccio didattico del progetto. Il lavoro effettivo (interrogazione di
 * TariffProvider e VehicleProvider, e calcolo del risparmio stimato) viene
 * eseguito in background e reso disponibile solo a completamento, cosi il
 * pattern di polling FLOW-02 riflette un'elaborazione realmente dipendente
 * da chiamate di rete verso altri provider, non solo un'attesa artificiale.</p>
 */
@Service
public class SimulationService {

    private static final String DEFAULT_VEHICLE_ID = "EV-001";

    private final RestTemplate restTemplate;
    private final Map<String, String> ticketStatusMap = new ConcurrentHashMap<>();
    private final Map<String, GridSavingsResult> ticketResultMap = new ConcurrentHashMap<>();

    public SimulationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Avvia una nuova simulazione asincrona e restituisce il ticket di polling.
     *
     * <p>Il metodo e non bloccante: registra lo stato iniziale in {@code PENDING}
     * e pianifica su un executor differito l'interrogazione dei provider e il
     * calcolo del risultato, senza bloccare il thread HTTP chiamante.</p>
     *
     * @param request payload della simulazione, con veicolo di riferimento opzionale e
     *                 l'eventuale parametro di sola simulazione/demo simulateWeekend
     * @return identificativo univoco del ticket
     */
    public String startAsyncSimulation(SimulationRequest request) {
        String ticketId = UUID.randomUUID().toString();
        String vehicleId = request.vehicleId() != null && !request.vehicleId().isBlank()
            ? request.vehicleId()
            : DEFAULT_VEHICLE_ID;
        boolean simulateWeekend = request.simulateWeekend();

        ticketStatusMap.put(ticketId, "PENDING");

        CompletableFuture.runAsync(
            () -> runSimulation(ticketId, vehicleId, simulateWeekend),
            CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS)
        );

        return ticketId;
    }

    /**
     * Interroga TariffProvider e VehicleProvider e pubblica il risultato del ticket.
     *
     * <p>Le due chiamate sono sequenziali (non e richiesto un aggregatore parallelo
     * qui, quello e il ruolo del ChargingOrchestrator): l'obiettivo di FLOW-02 e
     * dimostrare il pattern di polling per un'elaborazione la cui durata dipende
     * da servizi esterni, non minimizzarne la latenza con CompletableFuture paralleli.</p>
     */
    private void runSimulation(String ticketId, String vehicleId, boolean simulateWeekend) {
        TariffData[] dailyTariffs = restTemplate.getForObject(
            "http://tariff-provider/api/v1/tariffs/daily?simulateWeekend={simulateWeekend}",
            TariffData[].class,
            simulateWeekend
        );

        VehicleStatusData vehicleStatus = restTemplate.getForObject(
            "http://vehicle-provider/api/v1/vehicles/{vehicleId}/status",
            VehicleStatusData.class,
            vehicleId
        );

        GridSavingsResult result = computeSavings(vehicleStatus, dailyTariffs);
        ticketResultMap.put(ticketId, result);
        ticketStatusMap.put(ticketId, "COMPLETED");
    }

    private GridSavingsResult computeSavings(VehicleStatusData vehicleStatus, TariffData[] dailyTariffs) {
        TariffData[] tariffs = dailyTariffs == null ? new TariffData[0] : dailyTariffs;

        double peakPrice = Arrays.stream(tariffs)
            .mapToDouble(TariffData::pricePerKwh)
            .max()
            .orElse(0.35);

        TariffData cheapestSlot = Arrays.stream(tariffs)
            .min(Comparator.comparingDouble(TariffData::pricePerKwh))
            .orElse(new TariffData(2, 0.15));

        double energyNeededKwh = vehicleStatus == null
            ? 0.0
            : vehicleStatus.batteryCapacityKwh() * (1 - vehicleStatus.currentSoC() / 100.0);

        double estimatedSavingsEur = energyNeededKwh * (peakPrice - cheapestSlot.pricePerKwh());

        return new GridSavingsResult(
            vehicleStatus != null ? vehicleStatus.vehicleId() : null,
            round2(energyNeededKwh),
            peakPrice,
            cheapestSlot.pricePerKwh(),
            cheapestSlot.hour(),
            round2(estimatedSavingsEur)
        );
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Recupera lo stato corrente di una simulazione dal ticket in-memory.
     *
     * @param ticketId identificativo della simulazione
     * @return stato corrente del ticket; {@code null} se il ticket non esiste
     */
    public String getSimulationStatus(String ticketId) {
        return ticketStatusMap.get(ticketId);
    }

    /**
     * Recupera il risultato calcolato di una simulazione completata.
     *
     * @param ticketId identificativo della simulazione
     * @return risultato disponibile solo a completamento avvenuto; {@code null} altrimenti
     */
    public GridSavingsResult getSimulationResult(String ticketId) {
        return ticketResultMap.get(ticketId);
    }

    /**
     * Rappresentazione locale del payload REST del Tariff Provider.
     */
    private record TariffData(int hour, double pricePerKwh) {
    }

    /**
     * Rappresentazione locale del payload REST del Vehicle Provider.
     */
    private record VehicleStatusData(String vehicleId, double batteryCapacityKwh, double currentSoC) {
    }
}
