package com.smartcharging.energyprosumer.exception;

/**
 * Segnala che uno dei provider interrogati dall'Energy Prosumer non ha risposto correttamente
 * (timeout di connessione o di lettura, connessione rifiutata, errore HTTP a valle).
 *
 * <p>L'eccezione ha due usi distinti nel modulo, corrispondenti ai due percorsi di consumo dei
 * provider. Nella stima di costo sincrona viene lasciata propagare fino al
 * {@code @RestControllerAdvice}, che la traduce in un HTTP 503 verso il Charging Orchestrator.
 * Nella simulazione asincrona viene invece intercettata dal task in background, che porta il
 * ticket nello stato terminale {@code FAILED}: li non esiste alcuna risposta HTTP in corso su
 * cui riversare l'errore, quindi l'unico modo per renderlo visibile al client e registrarlo
 * nello stato del ticket.</p>
 */
public class ProviderUnavailableException extends RuntimeException {

    /**
     * @param message descrizione dell'integrazione fallita
     * @param cause eccezione originale sollevata dal client HTTP
     */
    public ProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
