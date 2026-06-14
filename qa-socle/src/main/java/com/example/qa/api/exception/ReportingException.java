package com.example.qa.api.exception;

/**
 * Erreur de remontée des résultats vers l'outil de gestion de tests levée par les implémentations de
 * {@code ReportingManager} (ex. {@code AlmApiClient}) : appel API ALM en échec, mapping de cas de
 * test introuvable, réponse illisible. L'exception bas-niveau (HTTP, parsing) est conservée en
 * {@code cause}.
 */
public class ReportingException extends QaToolkitException {

    public ReportingException(String message) {
        super(message);
    }

    public ReportingException(String message, Throwable cause) {
        super(message, cause);
    }
}
