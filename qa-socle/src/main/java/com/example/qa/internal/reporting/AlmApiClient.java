package com.example.qa.internal.reporting;

/**
 * Remontée des résultats d'exécution vers ALM (OpenText/HP ALM) via son API REST
 * (version ~18.4 à confirmer avant implémentation).
 *
 * <p><b>Stratégie de mapping (deux modes alternatifs, non cumulatifs — D13 mis à jour) :</b>
 * <ul>
 *   <li><b>Mode annotation</b> — {@code @WithTag("alm.testId:1042")} lue par réflexion
 *       ({@code TestAnnotations}) : suffisant si l'ID seul identifie le cas de test dans la
 *       campagne ALM.</li>
 *   <li><b>Mode fichier</b> — fichier de mapping externe (format à figer à l'étape 8) associant
 *       le nom qualifié de la classe de test à ses coordonnées ALM. Privilégié quand les
 *       coordonnées sont multiples ou fréquemment changeantes (pas de recompilation).</li>
 * </ul>
 * La résolution est interne : le code de test ne manipule jamais de coordonnées ALM brutes.
 *
 * <p><b>Coordonnées ALM (profil défaut) :</b> domain, project, test plan, test set en
 * {@code serenity.conf} (communs à la campagne) ; test instance ID par test (annotation ou
 * fichier). En profil « tout fichier », toutes les coordonnées sont dans le fichier de mapping.
 *
 * <p><b>Authentification :</b> login ALM via {@code qa.alm.login} dans {@code serenity.conf} ;
 * mot de passe récupéré via {@code SecretManager} (CyberArk) — jamais en clair.
 *
 * <p><b>Stateless par publication</b> (D19) : aucune session ALM partagée entre threads.
 *
 * <p>Nommée {@code ApiClient} (et non {@code Client}) pour lever toute ambiguïté :
 * il s'agit d'un consommateur de l'API ALM, pas d'un composant applicatif côté client.
 * Squelette — contenu réel à fournir (étape 8).
 */
public class AlmApiClient implements ReportingManager {

    @Override
    public void publishStart(String almTestId) {
        // Coquille typée (étape 6) : appel REST « In Progress » à implémenter en étape 8.
    }

    @Override
    public void publishEnd(TestExecutionResult result) {
        // Coquille typée (étape 6) : appel REST du statut final à implémenter en étape 8.
    }
}
