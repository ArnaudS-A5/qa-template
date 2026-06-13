package com.example.qa.api.reporting;

/**
 * Contrat neutre de remontée des résultats d'exécution vers un outil de gestion de tests.
 *
 * <p>Les appelants ne dépendent que de cette interface : changer d'outil (ALM → autre)
 * = nouvelle implémentation, zéro changement chez les appelants (même principe que
 * {@code SecretManager} / décision D12).
 * Squelette vide — signatures réelles à fournir (ex. publication d'un résultat de test).
 */
public interface ReportingManager {

}
