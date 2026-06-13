package com.example.qa.api.secret;

/**
 * Contrat neutre de récupération de secrets (credentials, mots de passe) au runtime.
 *
 * <p>Les appelants ne dépendent que de cette interface : changer d'outil de gestion
 * des secrets (CyberArk → autre) = nouvelle implémentation, zéro changement chez les
 * appelants (décision D12). Le contrat ne doit exposer aucun type spécifique CyberArk.
 * Squelette vide — signatures réelles à fournir (ex. {@code String getSecret(...)}).
 */
public interface SecretManager {

}
