package com.example.qa.api.secret;

/**
 * Contrat <b>neutre</b> de récupération de secrets (credentials, mots de passe) au runtime.
 *
 * <p>Les appelants ne dépendent que de cette interface : changer d'outil de gestion des secrets
 * (CyberArk → autre) = nouvelle implémentation, zéro changement chez les appelants (D12). Le contrat
 * n'expose <b>aucun</b> type spécifique au fournisseur.
 *
 * <p><b>Retourne un {@link Secret}</b> (et non un {@code String} nu) : la valeur est ainsi <b>masquée
 * par défaut</b> partout (logs, {@code TestStep}, dumps), et n'est déballée en clair que par un appel
 * explicite à {@code Secret.value()} — typiquement par {@code WebSync.type(By, Secret)} pour remplir
 * un champ. C'est la <b>prévention à la source</b> du masquage (étape 7).
 *
 * <p>Squelette — signatures arrêtées (étape 6), corps réel à fournir (étape 8).
 */
public interface SecretManager {

	/**
	 * Récupère un secret auprès du fournisseur configuré.
	 *
	 * @param request requête <b>neutre</b> décrivant le secret à récupérer (les paramètres spécifiques
	 *                au fournisseur, ex. CyberArk AppID/Safe/Folder/Object, restent internes à l'impl —
	 *                cf. D12) ; type à étoffer lors du traitement du composant {@code secret}
	 * @return le secret, encapsulé dans un {@link Secret} (masqué par défaut)
	 */
	Secret getSecret(SecretRequest request);
}
