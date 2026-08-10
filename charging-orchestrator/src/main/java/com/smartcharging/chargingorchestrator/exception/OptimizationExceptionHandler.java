package com.smartcharging.chargingorchestrator.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Gestore centralizzato degli errori del prosumer: traduce i fallimenti dei provider a valle
 * in una risposta HTTP pulita (503) invece di lasciar propagare un 500 con lo stack trace grezzo.
 * Rimane volutamente semplice (un solo tipo di eccezione gestita) per restare coerente con lo
 * scopo didattico del progetto, senza introdurre librerie di resilienza esterne.
 */
@RestControllerAdvice
public class OptimizationExceptionHandler {

    @ExceptionHandler(ProviderUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleProviderUnavailable(ProviderUnavailableException ex) {
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorResponse(ex.getMessage()));
    }

    private record ErrorResponse(String error) {
    }
}
