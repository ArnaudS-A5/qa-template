package com.example.qa.api.reporting;

/**
 * Résultat neutre d'exécution d'un cas de test, transmis à {@link ReportingManager#publish}.
 *
 * <p>Type valeur immuable. Conçu extensible : des champs complémentaires (durée, message
 * d'erreur…) seront ajoutés avant le gel de l'API sans rompre la signature de
 * {@link ReportingManager#publish}.
 *
 * <p>Squelette — corps réel à fournir (étape 8).
 */
public final class TestExecutionResult {

    private final String almTestId;
    private final ExecutionStatus status;

    private TestExecutionResult(String almTestId, ExecutionStatus status) {
        this.almTestId = almTestId;
        this.status = status;
    }

    /**
     * Fabrique un résultat pour un cas de test ALM.
     *
     * @param almTestId identifiant du cas de test dans ALM (ex. {@code "1042"}) — résolu
     *                  par {@code AlmApiClient} depuis {@code @WithTag} ou le fichier de mapping
     * @param status    statut final — {@link ExecutionStatus#PASSED} ou {@link ExecutionStatus#FAILED}
     */
    public static TestExecutionResult of(String almTestId, ExecutionStatus status) {
        return new TestExecutionResult(almTestId, status);
    }

    /** Identifiant du cas de test dans ALM. */
    public String almTestId() {
        return almTestId;
    }

    /** Statut final du cas de test. */
    public ExecutionStatus status() {
        return status;
    }
}
