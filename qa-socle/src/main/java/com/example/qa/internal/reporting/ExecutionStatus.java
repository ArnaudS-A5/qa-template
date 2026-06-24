package com.example.qa.internal.reporting;

/**
 * Statut d'exécution d'un cas de test tel que remontré dans l'outil de gestion de tests.
 *
 * <p>Utilisé par {@link TestExecutionReport} (publication finale) et implicitement par
 * {@link ReportingManager#publishStart} (qui envoie {@code IN_PROGRESS} sans passer par cet enum).
 */
public enum ExecutionStatus {

    /** Exécution en cours — envoyé au démarrage via {@link ReportingManager#publishStart}. */
    IN_PROGRESS,

    /** Test terminé avec succès. */
    PASSED,

    /** Test terminé en échec. */
    FAILED
}
