package com.smartcharging.vehicleprovider.controller;

import com.smartcharging.vehicleprovider.dto.VehicleStatusResponse;
import com.smartcharging.vehicleprovider.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST del Vehicle Provider.
 *
 * <p>Espone endpoint di sola lettura per consultare i dati telemetrici del veicolo.
 * L'interfaccia e documentata con SpringDoc OpenAPI per facilitare il consumo
 * da parte dei servizi Prosumer e la validazione durante i test di integrazione.</p>
 */
@RestController
@RequestMapping("/api/v1/vehicles")
@Tag(name = "Vehicle Management", description = "API Provider per la telemetria dei veicoli elettrici")
public class VehicleController {

    private final VehicleService vehicleService;

    /**
     * Iniezione tramite costruttore per mantenere la dipendenza immutabile e testabile.
     *
     * @param vehicleService servizio applicativo per la telemetria veicolo
     */
    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    /**
     * Recupera lo stato telemetrico corrente del veicolo identificato dal path variable.
     *
     * @param vehicleId identificativo logico del veicolo
     * @return payload con capacita batteria e SoC corrente
     */
    @GetMapping("/{vehicleId}/status")
    @Operation(
        summary = "Recupera lo stato del veicolo",
        description = "Restituisce capacita batteria e percentuale di carica corrente (SoC) del veicolo richiesto."
    )
    @ApiResponse(responseCode = "200", description = "Stato veicolo recuperato con successo")
    @ApiResponse(responseCode = "404", description = "Veicolo non trovato")
    public VehicleStatusResponse getVehicleStatus(@PathVariable String vehicleId) {
        return vehicleService.getVehicleStatus(vehicleId);
    }
}
