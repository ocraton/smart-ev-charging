package com.smartcharging.chargingorchestrator.service;

import com.smartcharging.chargingorchestrator.dto.OptimizationResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servizio applicativo che realizza l'orchestrazione parallela verso i tre provider interni.
 *
 * <p>Il metodo optimizeCharging implementa il cuore del FLOW-01: lancia in parallelo due
 * chiamate REST e una chiamata SOAP, attende il completamento di tutte con una barriera
 * di sincronizzazione esplicita e aggrega i risultati in una risposta unificata. Il servizio
 * resta stateless e usa esclusivamente dati di integrazione a breve vita, risultando adatto
 * al bilanciamento orizzontale richiesto dall'architettura.</p>
 */
@Service
public class OptimizationService {

    private static final Pattern MAX_POWER_PATTERN = Pattern.compile("<[^>]*maxPowerKw[^>]*>([^<]+)</[^>]*maxPowerKw>");

    private final RestTemplate restTemplate;
    private final Executor prosumerExecutor;

    /**
     * Costruisce il servizio con dipendenze immutabili iniettate da Spring.
     *
     * @param restTemplate client HTTP load-balanced per i provider REST e SOAP
     * @param prosumerExecutor executor dedicato per l'esecuzione concorrente dei task
     */
    public OptimizationService(RestTemplate restTemplate, @Qualifier("prosumerExecutor") Executor prosumerExecutor) {
        this.restTemplate = restTemplate;
        this.prosumerExecutor = prosumerExecutor;
    }

    /**
     * Esegue l'orchestrazione parallela dei provider e costruisce una raccomandazione sintetica.
     *
     * <p>Le tre integrazioni partono contemporaneamente tramite CompletableFuture.supplyAsync()
     * e condividono un executor esplicito per rispettare i vincoli di traccia. La chiamata SOAP
     * viene inviata come XML statico per mantenere il focus didattico sull'orchestrazione
     * asincrona piuttosto che sulla modellazione completa del contratto WSDL lato client.</p>
     *
     * @param vehicleId identificativo del veicolo richiesto
     * @param stationId identificativo della stazione richiesta
     * @return risposta aggregata con dati tecnici e suggerimento finale
     */
    public OptimizationResponse optimizeCharging(String vehicleId, String stationId) {
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
                "http://tariff-provider/api/v1/tariffs/daily",
                TariffData[].class
            ),
            prosumerExecutor
        );

        CompletableFuture<StationStatusData> stationFuture = CompletableFuture.supplyAsync(
            () -> fetchStationStatus(stationId),
            prosumerExecutor
        );

        CompletableFuture.allOf(vehicleFuture, tariffFuture, stationFuture).join();

        VehicleStatusData vehicleStatus = vehicleFuture.join();
        TariffData[] dailyTariffs = tariffFuture.join();
        StationStatusData stationStatus = stationFuture.join();

        return new OptimizationResponse(
            vehicleStatus.vehicleId(),
            stationStatus.stationId(),
            vehicleStatus.batteryCapacityKwh(),
            vehicleStatus.currentSoC(),
            stationStatus.maxPowerKw(),
            buildRecommendation(vehicleStatus, stationStatus, dailyTariffs)
        );
    }

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

    private double extractMaxPower(String soapBody, String stationId) {
        if (soapBody != null) {
            Matcher matcher = MAX_POWER_PATTERN.matcher(soapBody);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
        }

        return stationId != null && stationId.contains("FAST") ? 150.0 : 22.0;
    }

    private String buildRecommendation(
        VehicleStatusData vehicleStatus,
        StationStatusData stationStatus,
        TariffData[] dailyTariffs
    ) {
        double cheapestTariff = Arrays.stream(dailyTariffs == null ? new TariffData[0] : dailyTariffs)
            .min(Comparator.comparingDouble(TariffData::pricePerKwh))
            .map(TariffData::pricePerKwh)
            .orElse(0.30d);

        if (vehicleStatus.currentSoC() < 30.0 && stationStatus.maxPowerKw() >= 50.0) {
            return "Ricarica immediata ad alta potenza consigliata";
        }

        if (cheapestTariff <= 0.25d) {
            return "Ricarica notturna consigliata";
        }

        return "Ricarica distribuita nelle fasce a costo intermedio";
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
}