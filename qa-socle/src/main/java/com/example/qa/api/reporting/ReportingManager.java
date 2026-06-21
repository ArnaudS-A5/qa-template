package com.example.qa.api.reporting;

/**
 * Contrat neutre de remontée des résultats d'exécution vers un outil de gestion de tests.
 *
 * <p>Les appelants ne dépendent que de cette interface : changer d'outil (ALM → autre)
 * = nouvelle implémentation, zéro changement chez les appelants (même principe que
 * {@code SecretManager} / décision D12).
 *
 * <p><b>Cycle de publication par test (deux appels) :</b>
 * <ol>
 *   <li>{@link #publishStart(String)} — en début d'exécution → statut « In Progress » dans ALM ;</li>
 *   <li>{@link #publishEnd(TestExecutionResult)} — en fin d'exécution → statut final (Passed/Failed).</li>
 * </ol>
 *
 * <p><b>Stateless par publication</b> (D19) : aucune session inter-threads — chaque appel est
 * autonome, garantissant la sûreté en exécution parallèle Serenity.
 *
 * <p><b>Clé de maintenance</b> : {@code qa.reporting.enabled} (défaut {@code true}) — mettre à
 * {@code false} dans {@code serenity.conf} pour désactiver la remontée sans toucher au code.
 *
 * <p>Squelette — signatures arrêtées (étape 6), corps réels à fournir (étape 8).
 */
public interface ReportingManager {

    /**
     * Signale le démarrage d'un cas de test dans ALM (statut « In Progress »).
     *
     * <p>À appeler au début de l'exécution du test, avant la première action.
     *
     * @param almTestId identifiant du cas de test dans ALM (résolu par {@code AlmApiClient}
     *                  depuis {@code @WithTag} ou le fichier de mapping — jamais fourni en dur
     *                  par le code de test)
     * @throws com.example.qa.api.exception.ReportingException si l'appel ALM échoue
     */
    void publishStart(String almTestId);

    /**
     * Pousse le résultat final d'un cas de test dans ALM (Passed / Failed).
     *
     * <p>À appeler en fin d'exécution, quel que soit le résultat.
     *
     * @param result résultat d'exécution (identifiant ALM + statut final)
     * @throws com.example.qa.api.exception.ReportingException si l'appel ALM échoue
     */
    void publishEnd(TestExecutionResult result);
}
