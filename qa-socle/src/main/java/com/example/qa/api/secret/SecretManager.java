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
 * explicite à {@code Secret.value()} — typiquement par {@code type(By, Secret)} (commun
 * {@code WebSync}/{@code MobileSync}) pour remplir
 * un champ. C'est la <b>prévention à la source</b> du masquage (étape 7).
 *
 * <p><b>Désignation du secret</b> : les <b>coordonnées d'infrastructure</b> du fournisseur (ex. CyberArk
 * {@code AppID} / {@code Safe} / {@code Folder}) sont de la <b>configuration</b> ({@code serenity.conf},
 * D19), résolues par l'implémentation — elles ne fuitent pas dans le code de test. Le test ne fournit
 * que ce qui <b>identifie</b> le secret voulu : un {@code name} logique (cas courant), ou un couple
 * {@code (safe, object)} quand plusieurs coffres doivent être ciblés depuis le code. Aucun type
 * spécifique au fournisseur n'apparaît dans le contrat (D12) — d'où l'abandon d'un objet requête.
 *
 * <p>Squelette — signatures arrêtées (étape 6), corps réel à fournir (étape 8).
 */
public interface SecretManager {

	/**
	 * Récupère le secret identifié par un <b>nom logique</b> ; les coordonnées d'infrastructure
	 * (AppID/Safe/Folder) viennent de la configuration (D19).
	 *
	 * @param name nom logique du secret (ex. {@code "db-compte-batch"})
	 * @return le secret, encapsulé dans un {@link Secret} (masqué par défaut)
	 */
	Secret getSecret(String name);

	/**
	 * Récupère un secret en ciblant explicitement un <b>coffre</b> et un <b>objet</b> (cas multi-Safe
	 * piloté depuis le code) ; les autres coordonnées (AppID/Folder) restent en configuration (D19).
	 *
	 * @param safe   coffre/Safe à interroger
	 * @param object objet/compte recherché dans le coffre
	 * @return le secret, encapsulé dans un {@link Secret} (masqué par défaut)
	 */
	Secret getSecret(String safe, String object);
}
