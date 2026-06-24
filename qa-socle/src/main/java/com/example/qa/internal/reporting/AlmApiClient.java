package com.example.qa.internal.reporting;

/**
 * Remontée des résultats d'exécution vers ALM (OpenText/HP ALM) via son API REST
 * (version cible <b>24</b> — implémentation basée sur la doc technique v24, D13 ; assez récente
 * pour rester compatible v25/26).
 *
 * <p><b>Stratégie de mapping (deux modes alternatifs, non cumulatifs — D13 mis à jour) :</b>
 * <ul>
 *   <li><b>Mode annotation</b> — {@code @WithTag("alm.testId:1042")} lue par réflexion
 *       ({@code TestAnnotations}) : suffisant si l'ID seul identifie le cas de test dans la
 *       campagne ALM.</li>
 *   <li><b>Mode fichier</b> — fichier de mapping externe à <b>deux colonnes</b> : l'adresse
 *       complète du test côté Serenity en face de l'adresse complète de l'instance de test côté
 *       ALM (une ligne = une correspondance). Le fonctionnement est arrêté (D13) ; seul le
 *       <b>nom des colonnes</b> reste à figer à l'étape 8. Privilégié quand les coordonnées sont
 *       multiples ou fréquemment changeantes (pas de recompilation).</li>
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
    public void publishEnd(TestExecutionReport result) {
        // Coquille typée (étape 6) : appel REST du statut final à implémenter en étape 8.
    }
}
