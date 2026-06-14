package com.example.qa.api.exception;

/**
 * Exception racine du socle QA. Toutes les exceptions levées par l'API publique en héritent, ce qui
 * permet aux consommateurs (et à l'outillage de maintenance, D9) d'attraper d'un seul
 * {@code catch (QaToolkitException e)} toute erreur d'origine socle.
 *
 * <p><b>Unchecked</b> (étend {@link RuntimeException}, décision étape 3) : le code de test ne doit pas
 * être pollué de {@code throws}/{@code try-catch} — un échec interrompt le test, on ne le « récupère »
 * pas. Cohérent avec la stack (Selenium / Serenity / JUnit, toutes unchecked).
 *
 * <p>Racine <b>concrète</b> : peut être levée directement pour une erreur transverse ne relevant
 * d'aucun domaine ; sinon, préférer une sous-classe ({@code SyncException}, {@code DataFileException},
 * {@code SecretException}, {@code ReportingException}).
 */
public class QaToolkitException extends RuntimeException {

    public QaToolkitException(String message) {
        super(message);
    }

    public QaToolkitException(String message, Throwable cause) {
        super(message, cause);
    }
}
