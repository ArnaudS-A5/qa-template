package com.example.qa.internal.reporting;

/**
 * Contrat <b>interne</b> de remontée des résultats d'exécution vers un outil de gestion de tests.
 * <b>Aucun type public</b> : le consommateur ne référence jamais ce contrat — la remontée est
 * <b>automatique</b> (D13). L'interface reste le <b>seam de swap</b> : changer d'outil (ALM → autre) =
 * nouvelle impl {@code internal}, zéro impact ailleurs (même principe que {@code SecretManager}, D12).
 *
 * <p><b>Activation native (AUTO)</b> : un listener interne du socle (même mécanisme que la capture
 * d'échec — {@code TestExecutionListener} JUnit) appelle {@link #publishStart(String)} au début et
 * {@link #publishEnd(TestExecutionResult)} à la fin de <b>chaque</b> test. Le consommateur ne fait que
 * poser {@code @WithTag("alm.testId:1042")} et configurer {@code qa.alm.*} — aucune ligne d'appel.
 *
 * <p><b>Résolution de l'ID ALM</b> (depuis {@code @WithTag}, lu par réflexion) :
 * <ul>
 *   <li><b>aucun</b> tag {@code alm.testId} → test <b>non remonté</b> (opt-out silencieux par test) ;</li>
 *   <li><b>un</b> tag → remonté pour cet ID ;</li>
 *   <li><b>plusieurs</b> tags {@code alm.testId} → le <b>premier</b> est retenu (les autres ignorés).</li>
 * </ul>
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
     * <p>Appelé par le listener du socle au début de l'exécution du test (activation native, AUTO).
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
     * <p>Appelé par le listener du socle en fin d'exécution, quel que soit le résultat (AUTO).
     *
     * @param result résultat d'exécution (identifiant ALM + statut final)
     * @throws com.example.qa.api.exception.ReportingException si l'appel ALM échoue
     */
    void publishEnd(TestExecutionResult result);
}
