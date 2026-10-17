package com.example.qa;

/**
 * Exception racine du socle QA. Toutes les exceptions levées par l'API publique en héritent, ce qui
 * permet aux consommateurs (et à l'outillage de maintenance, D9) d'attraper d'un seul
 * {@code catch (QaToolkitException e)} toute erreur d'origine socle.
 *
 * <p><b>Unchecked</b> (étend {@link RuntimeException}, décision étape 3) : le code de test ne doit pas
 * être pollué de {@code throws}/{@code try-catch} — un échec interrompt le test, on ne le « récupère »
 * pas. Cohérent avec la stack (Selenium / Serenity / JUnit, toutes unchecked).
 *
 * <p>Racine <b>abstraite</b> (constructeurs {@code protected}) : le consommateur l'<b>attrape</b>, il ne
 * l'instancie ni ne la lève. Toute erreur est levée par le socle via un sous-type de domaine
 * ({@code SyncException}, {@code DataFileException}, {@code SecretException},
 * {@code ReportingException}), {@code final} et à constructeurs package-private (D15, D18).
 */
public abstract class QaToolkitException extends RuntimeException {

    protected QaToolkitException(String message) {
        super(message);
    }

    protected QaToolkitException(String message, Throwable cause) {
        super(message, cause);
    }
}
