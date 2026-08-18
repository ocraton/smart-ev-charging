package com.smartcharging.tariffprovider.controller;

import com.smartcharging.tariffprovider.dto.TariffResponse;
import com.smartcharging.tariffprovider.service.TariffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller REST che espone le API del Tariff Provider.
 * <p>Questi endpoint verranno consumati dai Prosumer (es. EnergyProsumer) 
 * transitando internamente o attraverso il Gateway. 
 * L'API è interamente documentata tramite SpringDoc OpenAPI.</p>
 */
@RestController
@RequestMapping("/api/v1/tariffs")
@Tag(name = "Tariff Management", description = "API Provider per la consultazione dei prezzi di mercato dell'energia")
public class TariffController {

    private static final Logger log = LoggerFactory.getLogger(TariffController.class);

    private final TariffService tariffService;

    /**
     * Hostname del container che ospita questa istanza. In Docker coincide con l'ID abbreviato
     * del container, quindi due repliche dello stesso servizio si distinguono a colpo d'occhio
     * nei log.
     */
    private final String instanceId;

    /**
     * Iniezione delle dipendenze basata su costruttore per favorire l'immutabilità
     * e facilitare eventuali unit test.
     *
     * @param tariffService servizio applicativo che produce il listino orario
     * @param instanceId hostname dell'istanza, risolto da Spring dalla variabile d'ambiente
     *                   HOSTNAME impostata da Docker
     */
    public TariffController(TariffService tariffService, @Value("${HOSTNAME:sconosciuto}") String instanceId) {
        this.tariffService = tariffService;
        this.instanceId = instanceId;
    }

    @GetMapping("/daily")
    @Operation(
        summary = "Recupera le tariffe giornaliere",
        description = "Restituisce un array di 24 elementi contenente le fasce di prezzo orarie dell'energia per la giornata in corso."
    )
    public List<TariffResponse> getDailyTariffs(
        @Parameter(description = "Parametro di sola simulazione/demo: forza il calendario a comportarsi come weekend indipendentemente dal giorno reale, per dimostrare a comando lo scenario di sconto notturno profondo")
        @RequestParam(required = false, defaultValue = "false") boolean simulateWeekend
    ) {
        // Traccia esplicitamente quale replica ha servito la richiesta. Questo servizio e il
        // candidato naturale allo scale-out (e interrogato da entrambi i prosumer e coinvolto in
        // tutti i flussi), e il log rende direttamente osservabile il round robin del
        // client-side load balancing: avviando due repliche e seguendo
        // "docker compose logs -f tariff-provider" si vede il traffico alternarsi fra le istanze.
        log.info("Istanza {} serve la richiesta di listino giornaliero (simulateWeekend={})", instanceId, simulateWeekend);
        return tariffService.getDailyTariffs(simulateWeekend);
    }
}