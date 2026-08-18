package com.smartcharging.energyprosumer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Gestore centralizzato degli errori dell'Energy Prosumer.
 *
 * <p>Traduce il fallimento di un provider a valle in un HTTP 503 Service Unavailable con un
 * corpo JSON pulito, invece di lasciar emergere un 500 con lo stack trace grezzo. La semantica
 * e corretta: il servizio non e rotto, e temporaneamente incapace di servire la richiesta
 * perche una sua dipendenza non risponde.</p>
 *
 * <p>Questa scelta e speculare a quella del Charging Orchestrator, ed e proprio la simmetria
 * a renderla utile: quando l'Orchestrator invoca la stima di costo di questo prosumer durante
 * il coordinamento parallelo di FLOW-01, riceve un 503 e non un 500, e puo quindi trattare il
 * prosumer esattamente come tratta gli altri partecipanti dell'orchestrazione.</p>
 */
@RestControllerAdvice
public class EnergyExceptionHandler {

    /**
     * Converte il fallimento di un provider in una risposta HTTP 503.
     *
     * @param ex eccezione sollevata dall'adapter di integrazione
     * @return risposta 503 con la descrizione dell'integrazione fallita
     */
    @ExceptionHandler(ProviderUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleProviderUnavailable(ProviderUnavailableException ex) {
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorResponse(ex.getMessage()));
    }

    /**
     * Corpo minimale della risposta di errore.
     *
     * @param error descrizione leggibile del problema riscontrato
     */
    private record ErrorResponse(String error) {
    }
}
