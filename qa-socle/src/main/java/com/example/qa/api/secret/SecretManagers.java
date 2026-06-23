package com.example.qa.api.secret;

/**
 * Factory <b>publique</b> des gestionnaires de secrets : <b>seul</b> point d'accès au
 * {@link SecretManager}, cachant l'implémentation {@code internal} (CyberArk), conformément à D12/D15.
 * Le consommateur obtient un {@code SecretManager} <b>sans jamais nommer l'outil</b> → changer d'outil
 * (CyberArk → autre) = nouvelle impl {@code internal} + <b>une ligne</b> ici, <b>zéro changement</b>
 * côté consommateur.
 *
 * <p>Nom au pluriel suivant l'<b>idiome JDK</b> des factories ({@code Executors} → {@code Executor},
 * {@code Collections} → {@code Collection}). La méthode {@link #get()} reste <b>neutre</b> (pas de
 * {@code cyberArk()}) : l'outil et ses coordonnées (AppID/Safe/Folder) sont de la <b>configuration</b>
 * (serenity.conf, D19), pas un choix du code de test.
 *
 * <p>Usage (le manager se tient une fois, le {@code .get()} n'est payé qu'une seule fois) :
 * <pre>{@code
 * private final SecretManager secrets = SecretManagers.get();
 * ...
 * Secret pwd = secrets.getSecret("db-batch");
 * }</pre>
 *
 * <p>Squelette — signatures arrêtées (étape 6), corps réel à fournir (étape 8).
 */
public final class SecretManagers {

	private SecretManagers() {
	}

	/**
	 * Renvoie le {@link SecretManager} configuré du socle (singleton <b>stateless</b>, D19), adossé à
	 * l'outil de gestion des secrets en place (CyberArk aujourd'hui). Les coordonnées d'infrastructure
	 * sont résolues par l'implémentation depuis la configuration (D19) — le contrat reste neutre.
	 *
	 * @return le gestionnaire de secrets (jamais {@code null} une fois implémenté)
	 */
	public static SecretManager get() {
		return null;
	}
}
