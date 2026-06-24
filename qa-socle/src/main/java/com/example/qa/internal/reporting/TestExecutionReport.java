package com.example.qa.internal.reporting;

/**
 * Rapport neutre d'exécution d'un cas de test, transmis à {@link ReportingManager#publishEnd}.
 *
 * <p>Type valeur immuable, <b>interne</b> (construit par le listener du socle, jamais par le
 * consommateur — reporting AUTO, D13). Conçu extensible : des champs complémentaires (durée, message
 * d'erreur…) peuvent être ajoutés sans rompre {@link ReportingManager#publishEnd}.
 *
 * <p><b>Nom en {@code …Report}</b> (et non {@code …Result}) pour éviter toute collision avec le type
 * homonyme JUnit {@code org.junit.platform.engine.TestExecutionResult}, qui cohabitera dans le listener
 * de reporting AUTO (étape 8). Type {@code internal} → ce renommage n'a aucun impact de compatibilité.
 *
 * <p>Squelette — corps réel à fournir (étape 8).
 */
public final class TestExecutionReport {

    private final String almTestId;
    private final ExecutionStatus status;

    private TestExecutionReport(String almTestId, ExecutionStatus status) {
        this.almTestId = almTestId;
        this.status = status;
    }

    /**
     * Fabrique un rapport pour un cas de test ALM.
     *
     * @param almTestId identifiant du cas de test dans ALM (ex. {@code "1042"}) — résolu
     *                  par {@code AlmApiClient} depuis {@code @WithTag} ou le fichier de mapping
     * @param status    statut final — {@link ExecutionStatus#PASSED} ou {@link ExecutionStatus#FAILED}
     */
    public static TestExecutionReport of(String almTestId, ExecutionStatus status) {
        return new TestExecutionReport(almTestId, status);
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
