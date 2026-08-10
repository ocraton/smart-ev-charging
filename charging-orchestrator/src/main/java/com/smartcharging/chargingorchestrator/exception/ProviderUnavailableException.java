package com.smartcharging.chargingorchestrator.exception;

/**
 * Segnala che uno o più provider interrogati in parallelo durante FLOW-01
 * non hanno risposto correttamente (timeout, connessione rifiutata, errore HTTP).
 */
public class ProviderUnavailableException extends RuntimeException {

    public ProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
