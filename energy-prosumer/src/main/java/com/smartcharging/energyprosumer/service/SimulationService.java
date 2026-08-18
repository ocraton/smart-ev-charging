package com.smartcharging.energyprosumer.service;

import com.smartcharging.energyprosumer.client.ProviderClient;
import com.smartcharging.energyprosumer.client.TariffData;
import com.smartcharging.energyprosumer.client.VehicleStatusData;
import com.smartcharging.energyprosumer.dto.GridSavingsResult;
import com.smartcharging.energyprosumer.dto.SimulationRequest;
import com.smartcharging.energyprosumer.exception.ProviderUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Servizio applicativo per la gestione dello stato delle simulazioni asincrone (FLOW-02).
 *
 * <p>L'implementazione usa una memoria in-process con {@link ConcurrentHashMap} per modellare il
 * ciclo di vita dei ticket senza persistenza esterna, conforme all'approccio didattico del
 * progetto. Il lavoro effettivo (interrogazione di Tariff Provider e Vehicle Provider, e calcolo
 * del risparmio stimato) viene eseguito in background e reso disponibile solo a completamento,
 * cosi il pattern di polling riflette un'elaborazione realmente dipendente da chiamate di rete
 * verso altri servizi, non solo un'attesa artificiale.</p>
 *
 * <p><strong>Nota sulla scalabilita.</strong> Le due mappe rendono questo servizio l'unico
 * componente <em>stateful</em> dell'intera architettura, e quindi l'unico non replicabile: con
 * due istanze un client potrebbe avviare la simulazione su una e fare polling sull'altra,
 * ricevendo un 404. La limitazione e assunta consapevolmente e mostra per contrasto quale
 * proprieta abiliti davvero lo scale-out orizzontale, ossia l'assenza di stato locale. Il
 * percorso sincrono di {@link CostEstimateService}, che vive nello stesso modulo ma non conserva
 * nulla, non soffre di questo vincolo.</p>
 */
@Service
public class SimulationService {

    private static final String DEFAULT_VEHICLE_ID = "EV-001";

    /**
     * Ritardo artificiale prima dell'avvio dell'elaborazione, introdotto per rendere osservabile
     * il ciclo di polling durante la dimostrazione. In un sistema reale la durata deriverebbe
     * dalla mole di dati storici da elaborare.
     */
    private static final long SIMULATION_DELAY_SECONDS = 10L;

    private final ProviderClient providerClient;
    private final Executor simulationExecutor;
    private final Map<String, String> ticketStatusMap = new ConcurrentHashMap<>();
    private final Map<String, GridSavingsResult> ticketResultMap = new ConcurrentHashMap<>();

    /**
     * Inietta le dipendenze immutabili del servizio.
     *
     * @param providerClient adapter condiviso di integrazione verso i provider
     * @param simulationExecutor thread pool dedicato all'elaborazione in background
     */
    public SimulationService(ProviderClient providerClient, @Qualifier("simulationExecutor") Executor simulationExecutor) {
        this.providerClient = providerClient;
        this.simulationExecutor = simulationExecutor;
    }

    /**
     * Avvia una nuova simulazione asincrona e restituisce il ticket di polling.
     *
     * <p>Il metodo e non bloccante: registra lo stato iniziale in {@code PENDING} e pianifica su
     * un executor differito l'interrogazione dei provider e il calcolo del risultato, senza
     * bloccare il thread HTTP chiamante, che viene liberato prima ancora che l'elaborazione
     * cominci.</p>
     *
     * <p>Il ritardo e ottenuto con {@link CompletableFuture#delayedExecutor} e non con
     * {@code Thread.sleep()}. La differenza non e stilistica: {@code sleep} terrebbe occupato un
     * thread del pool per tutta l'attesa, riducendo di uno la capacita di elaborazione concorrente
     * per ogni simulazione in volo, mentre {@code delayedExecutor} accoda il task su uno scheduler
     * e non consuma alcun thread finche non e ora di eseguirlo.</p>
     *
     * @param request payload della simulazione, con veicolo di riferimento opzionale e l'eventuale
     *                parametro di sola simulazione/demo simulateWeekend
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
            CompletableFuture.delayedExecutor(SIMULATION_DELAY_SECONDS, TimeUnit.SECONDS, simulationExecutor)
        );

        return ticketId;
    }

    /**
     * Interroga i provider e pubblica il risultato del ticket.
     *
     * <p>Le due chiamate sono deliberatamente sequenziali: FLOW-02 dimostra il disaccoppiamento
     * temporale fra richiesta ed elaborazione, non la minimizzazione della latenza, che e invece
     * il ruolo dell'orchestrazione parallela di FLOW-01. Parallelizzarle renderebbe i due flussi
     * ridondanti dal punto di vista didattico.</p>
     *
     * <p>Se uno dei due provider non risponde correttamente il ticket viene marcato
     * {@code FAILED} invece di restare bloccato per sempre in uno stato non terminale: senza
     * questo catch l'eccezione andrebbe semplicemente perduta, perche il {@code CompletableFuture}
     * restituito da {@code runAsync} non viene mai osservato da nessuno, e il client continuerebbe
     * a fare polling all'infinito senza alcun errore visibile.</p>
     *
     * @param ticketId identificativo del ticket da aggiornare
     * @param vehicleId veicolo di riferimento della simulazione
     * @param simulateWeekend parametro di sola simulazione/demo inoltrato al Tariff Provider
     */
    private void runSimulation(String ticketId, String vehicleId, boolean simulateWeekend) {
        try {
            TariffData[] dailyTariffs = providerClient.fetchDailyTariffs(simulateWeekend);
            VehicleStatusData vehicleStatus = providerClient.fetchVehicleStatus(vehicleId);

            GridSavingsResult result = computeSavings(vehicleStatus, dailyTariffs);
            ticketResultMap.put(ticketId, result);
            ticketStatusMap.put(ticketId, "COMPLETED");
        } catch (ProviderUnavailableException e) {
            ticketStatusMap.put(ticketId, "FAILED");
        }
    }

    /**
     * Calcola il risparmio ottenibile spostando la ricarica dalla fascia di picco a quella minima.
     *
     * @param vehicleStatus telemetria del veicolo di riferimento
     * @param dailyTariffs listino delle 24 fasce orarie della giornata
     * @return esito della simulazione con energia necessaria e risparmio stimato
     */
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

    /**
     * Arrotonda un importo a due cifre decimali per evitare artefatti della virgola mobile.
     *
     * @param value valore da arrotondare
     * @return valore arrotondato al centesimo
     */
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
}
