package com.smartcharging.tariffprovider.controller;

import com.smartcharging.tariffprovider.dto.TariffResponse;
import com.smartcharging.tariffprovider.service.TariffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    private final TariffService tariffService;

    /**
     * Iniezione delle dipendenze basata su costruttore per favorire l'immutabilità
     * e facilitare eventuali unit test.
     */
    public TariffController(TariffService tariffService) {
        this.tariffService = tariffService;
    }

    @GetMapping("/daily")
    @Operation(
        summary = "Recupera le tariffe giornaliere", 
        description = "Restituisce un array di 24 elementi contenente le fasce di prezzo orarie dell'energia per la giornata in corso."
    )
    public List<TariffResponse> getDailyTariffs() {
        return tariffService.getDailyTariffs();
    }
}