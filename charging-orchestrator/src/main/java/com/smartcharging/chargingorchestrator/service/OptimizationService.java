package com.smartcharging.chargingorchestrator.service;

import com.smartcharging.chargingorchestrator.dto.OptimizationResponse;
import com.smartcharging.chargingorchestrator.exception.ProviderUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servizio applicativo che realizza l'orchestrazione parallela di FLOW-01.
 *
 * <p>Il metodo {@link #optimizeCharging} lancia contemporaneamente <strong>quattro</strong>
 * integrazioni e le sincronizza su un'unica barriera esplicita prima di aggregare i risultati:</p>
 * <ol>
 *   <li>due letture REST verso Vehicle Provider e Tariff Provider;</li>
 *   <li>una chiamata SOAP verso Station Provider;</li>
 *   <li>una chiamata REST verso l'<strong>Energy Prosumer</strong>, il secondo prosumer del
 *       sistema, che nel frattempo svolge il proprio lavoro consultando a sua volta i provider.</li>
 * </ol>
 *
 * <p>La quarta integrazione e architetturalmente la piu significativa: realizza il coordinamento
 * <em>inter-prosumer</em> richiesto dalla traccia. I due prosumer eseguono compiti distinti e
 * complementari, in parallelo e senza attendersi a vicenda — l'Orchestrator raccoglie i vincoli
 * tecnici della ricarica, l'Energy Prosumer ne valuta la convenienza economica — e si
 * sincronizzano solo sulla barriera {@code allOf(...).join()}, dove i rispettivi contributi
 * vengono fusi in un'unica raccomandazione. Nessuno dei due, da solo, dispone degli elementi
 * necessari a formularla.</p>
 *
 * <p>Il servizio resta <em>stateless</em> e usa esclusivamente dati di integrazione a breve vita,
 * risultando quindi replicabile orizzontalmente senza alcun accorgimento.</p>
 */
@Service
public class OptimizationService {

    private static final Pattern MAX_POWER_PATTERN = Pattern.compile("<[^>]*maxPowerKw[^>]*>([^<]+)</[^>]*maxPowerKw>");

    /**
     * Soglia di convenienza, in euro, sotto la quale differire la ricarica non vale la penalizzazione
     * in termini di disponibilita del veicolo. Rende esplicito nel codice un criterio che altrimenti
     * resterebbe implicito in un confronto numerico.
     */
    private static final double MIN_WORTHWHILE_SAVINGS_EUR = 1.0d;

    /**
     * Stato di carica sotto il quale la ricarica e considerata tecnicamente urgente, a prescindere
     * da ogni valutazione economica.
     */
    private static final double CRITICAL_SOC_PERCENT = 30.0d;

    /**
     * Potenza minima, in kW, perche una colonnina sia considerata di ricarica rapida.
     */
    private static final double FAST_CHARGE_POWER_KW = 50.0d;

    private final RestTemplate restTemplate;
    private final Executor prosumerExecutor;

    /**
     * Costruisce il servizio con dipendenze immutabili iniettate da Spring.
     *
     * @param restTemplate client HTTP load-balanced per provider e prosumer
     * @param prosumerExecutor executor dedicato per l'esecuzione concorrente dei task
     */
    public OptimizationService(RestTemplate restTemplate, @Qualifier("prosumerExecutor") Executor prosumerExecutor) {
        this.restTemplate = restTemplate;
        this.prosumerExecutor = prosumerExecutor;
    }

    /**
     * Esegue l'orchestrazione parallela e costruisce la raccomandazione di ricarica.
     *
     * <p>Le quattro integrazioni partono contemporaneamente tramite
     * {@code CompletableFuture.supplyAsync()} e condividono un executor esplicito, separato dai
     * thread HTTP di Tomcat. La latenza percepita dal client e quindi pari alla piu lenta delle
     * quattro chiamate e non alla loro somma: e questo che rende accettabile mantenere sincrono,
     * verso l'utente, un flusso che internamente coinvolge cinque servizi.</p>
     *
     * <p>La chiamata SOAP viene inviata come XML costruito a mano per mantenere il focus didattico
     * sull'orchestrazione parallela piuttosto che sulla generazione del client WSDL.</p>
     *
     * @param vehicleId identificativo del veicolo richiesto
     * @param stationId identificativo della stazione richiesta
     * @param simulateWeekend parametro di sola simulazione/demo, inoltrato sia al Tariff Provider
     *                        sia all'Energy Prosumer per forzare a comando lo scenario weekend
     * @return risposta aggregata con dati tecnici, valutazione economica e suggerimento finale
     */
    public OptimizationResponse optimizeCharging(String vehicleId, String stationId, boolean simulateWeekend) {
        CompletableFuture<VehicleStatusData> vehicleFuture = CompletableFuture.supplyAsync(
            () -> restTemplate.getForObject(
                "http://vehicle-provider/api/v1/vehicles/{vehicleId}/status",
                VehicleStatusData.class,
                vehicleId
            ),
            prosumerExecutor
        );

        CompletableFuture<TariffData[]> tariffFuture = CompletableFuture.supplyAsync(
            () -> restTemplate.getForObject(
                "http://tariff-provider/api/v1/tariffs/daily?simulateWeekend={simulateWeekend}",
                TariffData[].class,
                simulateWeekend
            ),
            prosumerExecutor
        );

        CompletableFuture<StationStatusData> stationFuture = CompletableFuture.supplyAsync(
            () -> fetchStationStatus(stationId),
            prosumerExecutor
        );

        // Coordinamento inter-prosumer: mentre questo servizio interroga i provider, l'Energy
        // Prosumer svolge in parallelo la propria valutazione economica consultando a sua volta
        // Tariff Provider e Vehicle Provider. Anche questo indirizzo e un nome logico risolto via
        // Eureka: i due prosumer si conoscono per service-id, mai per indirizzo fisico.
        CompletableFuture<CostEstimateData> costFuture = CompletableFuture.supplyAsync(
            () -> restTemplate.getForObject(
                "http://energy-prosumer/api/v1/energy/cost-estimate?vehicleId={vehicleId}&simulateWeekend={simulateWeekend}",
                CostEstimateData.class,
                vehicleId,
                simulateWeekend
            ),
            prosumerExecutor
        );

        // BARRIERA DI SINCRONIZZAZIONE: e il punto in cui i due prosumer si coordinano. Nessun
        // risultato viene letto prima che tutti e quattro i task siano completati.
        try {
            CompletableFuture.allOf(vehicleFuture, tariffFuture, stationFuture, costFuture).join();
        } catch (CompletionException e) {
            throw new ProviderUnavailableException(
                "Uno o piu servizi non hanno risposto correttamente durante l'orchestrazione FLOW-01",
                e
            );
        }

        VehicleStatusData vehicleStatus = vehicleFuture.join();
        TariffData[] dailyTariffs = tariffFuture.join();
        StationStatusData stationStatus = stationFuture.join();
        CostEstimateData costEstimate = costFuture.join();

        return new OptimizationResponse(
            vehicleStatus.vehicleId(),
            stationStatus.stationId(),
            vehicleStatus.batteryCapacityKwh(),
            vehicleStatus.currentSoC(),
            stationStatus.maxPowerKw(),
            costEstimate.energyNeededKwh(),
            costEstimate.costNowEur(),
            costEstimate.costAtCheapestEur(),
            costEstimate.potentialSavingsEur(),
            costEstimate.cheapestHour(),
            buildRecommendation(vehicleStatus, stationStatus, dailyTariffs, costEstimate)
        );
    }

    /**
     * Interroga lo Station Provider tramite una chiamata SOAP e ne estrae la potenza massima.
     *
     * @param stationId identificativo della colonnina da interrogare
     * @return rappresentazione locale dello stato della stazione
     */
    private StationStatusData fetchStationStatus(String stationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        headers.add(HttpHeaders.ACCEPT, MediaType.TEXT_XML_VALUE);

        String soapEnvelope = """
            <soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:sta=\"http://soap.smartcharging.com/station\">
               <soapenv:Header/>
               <soapenv:Body>
                  <sta:GetStationStatus>
                            <sta:stationId>%s</sta:stationId>
                  </sta:GetStationStatus>
               </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(stationId);

        ResponseEntity<String> response = restTemplate.exchange(
            "http://station-provider/ws/station",
            HttpMethod.POST,
            new HttpEntity<>(soapEnvelope, headers),
            String.class
        );

        return new StationStatusData(stationId, extractMaxPower(response.getBody(), stationId));
    }

    /**
     * Estrae la potenza massima dall'inviluppo SOAP di risposta.
     *
     * @param soapBody corpo XML restituito dallo Station Provider
     * @param stationId identificativo usato per il valore di ripiego
     * @return potenza massima in kW
     */
    private double extractMaxPower(String soapBody, String stationId) {
        if (soapBody != null) {
            Matcher matcher = MAX_POWER_PATTERN.matcher(soapBody);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
        }

        return stationId != null && stationId.contains("FAST") ? 150.0 : 22.0;
    }

    /**
     * Aggrega i contributi dei due prosumer in un'unica raccomandazione testuale.
     *
     * <p>E qui che il coordinamento inter-prosumer produce il proprio valore: la decisione non e
     * derivabile ne dai soli dati tecnici raccolti dall'Orchestrator, ne dalla sola valutazione
     * economica prodotta dall'Energy Prosumer. La logica valuta quattro condizioni in cascata,
     * ordinate per priorita decrescente.</p>
     *
     * @param vehicleStatus telemetria del veicolo, dal Vehicle Provider
     * @param stationStatus stato della colonnina, dallo Station Provider via SOAP
     * @param dailyTariffs listino orario, dal Tariff Provider
     * @param costEstimate valutazione economica, dall'Energy Prosumer
     * @return raccomandazione sintetica destinata al client
     */
    private String buildRecommendation(
        VehicleStatusData vehicleStatus,
        StationStatusData stationStatus,
        TariffData[] dailyTariffs,
        CostEstimateData costEstimate
    ) {
        TariffData[] tariffs = dailyTariffs == null ? new TariffData[0] : dailyTariffs;

        // 1. L'urgenza tecnica prevale su ogni considerazione di costo: un veicolo quasi scarico
        //    su una colonnina rapida va ricaricato subito. Il contributo economico dell'altro
        //    prosumer non cambia la decisione, ma ne quantifica il prezzo, rendendola trasparente.
        if (vehicleStatus.currentSoC() < CRITICAL_SOC_PERCENT && stationStatus.maxPowerKw() >= FAST_CHARGE_POWER_KW) {
            return "Ricarica immediata ad alta potenza consigliata: stato di carica critico. Costo stimato %.2f EUR, rinunciando a un risparmio potenziale di %.2f EUR"
                .formatted(costEstimate.costNowEur(), costEstimate.potentialSavingsEur());
        }

        // 2. Se l'ora corrente ricade gia nella fascia piu conveniente della giornata, attendere
        //    non porterebbe alcun beneficio. Questa condizione si valuta sul listino completo,
        //    che solo l'Orchestrator possiede.
        int currentHour = LocalTime.now().getHour();
        double cheapestPrice = Arrays.stream(tariffs)
            .min(Comparator.comparingDouble(TariffData::pricePerKwh))
            .map(TariffData::pricePerKwh)
            .orElse(costEstimate.cheapestPricePerKwh());

        boolean currentlyInCheapestSlot = Arrays.stream(tariffs)
            .anyMatch(tariff -> tariff.hour() == currentHour && tariff.pricePerKwh() <= cheapestPrice);

        if (currentlyInCheapestSlot) {
            return "Sei gia nella fascia oraria piu economica: ricarica immediata consigliata. Costo stimato %.2f EUR"
                .formatted(costEstimate.costNowEur());
        }

        // 3. Differimento conveniente: la decisione dipende interamente dalla valutazione
        //    dell'Energy Prosumer, perche il criterio e il risparmio in euro e non il prezzo unitario.
        if (costEstimate.potentialSavingsEur() >= MIN_WORTHWHILE_SAVINGS_EUR) {
            return "Ricarica differita alle %02d:00 consigliata: risparmio stimato di %.2f EUR (%.2f EUR invece di %.2f EUR)"
                .formatted(
                    costEstimate.cheapestHour(),
                    costEstimate.potentialSavingsEur(),
                    costEstimate.costAtCheapestEur(),
                    costEstimate.costNowEur()
                );
        }

        // 4. Il differimento non ripaga l'indisponibilita del veicolo.
        return "Ricarica distribuita nelle fasce a costo intermedio: differire farebbe risparmiare solo %.2f EUR"
            .formatted(costEstimate.potentialSavingsEur());
    }

    /**
     * Rappresentazione locale del payload REST del Vehicle Provider.
     *
     * @param vehicleId identificativo del veicolo
     * @param batteryCapacityKwh capacita batteria in kWh
     * @param currentSoC livello di carica corrente in percentuale
     */
    private record VehicleStatusData(String vehicleId, double batteryCapacityKwh, double currentSoC) {
    }

    /**
     * Rappresentazione locale del payload REST del Tariff Provider.
     *
     * @param hour ora della fascia tariffaria
     * @param pricePerKwh costo per kWh della fascia considerata
     */
    private record TariffData(int hour, double pricePerKwh) {
    }

    /**
     * Rappresentazione locale semplificata del risultato SOAP della stazione.
     *
     * @param stationId identificativo della colonnina
     * @param maxPowerKw potenza massima estraibile dalla risposta SOAP
     */
    private record StationStatusData(String stationId, double maxPowerKw) {
    }

    /**
     * Rappresentazione locale della valutazione economica prodotta dall'Energy Prosumer.
     *
     * <p>Mappa il sottoinsieme di campi effettivamente usato dalla logica di aggregazione: Spring
     * Boot disattiva {@code FAIL_ON_UNKNOWN_PROPERTIES}, quindi l'altro prosumer puo arricchire la
     * propria risposta senza rompere questo consumatore.</p>
     *
     * @param vehicleId identificativo del veicolo valutato
     * @param energyNeededKwh energia mancante per completare la ricarica
     * @param costNowEur costo stimato ricaricando immediatamente
     * @param cheapestHour ora del giorno con la tariffa minima
     * @param cheapestPricePerKwh tariffa minima della giornata
     * @param costAtCheapestEur costo stimato ricaricando nella fascia minima
     * @param potentialSavingsEur risparmio ottenibile differendo la ricarica
     */
    private record CostEstimateData(
        String vehicleId,
        double energyNeededKwh,
        double costNowEur,
        int cheapestHour,
        double cheapestPricePerKwh,
        double costAtCheapestEur,
        double potentialSavingsEur
    ) {
    }
}
