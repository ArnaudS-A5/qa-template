package com.example.qa.api.secret;

/**
 * Valeur sensible (mot de passe, token...) qui se <b>masque elle-même par défaut</b>.
 *
 * <p><b>Principe — prévention à la source</b> (contrat « valeur sensible » de D12, étape 7) : la
 * valeur en clair n'est accessible que via {@link #value()}, appelé <b>explicitement</b> (typiquement
 * par {@code WebSync.type(By, Secret)} pour remplir un champ). Partout ailleurs — logs, {@code TestStep}
 * Serenity, dumps, concaténation accidentelle — c'est le rendu <b>masqué</b> qui apparaît, car
 * {@link #toString()} ne révèle jamais le clair. Un secret loggué par erreur ne fuit donc pas.
 *
 * <p><b>Rendu masqué</b> : {@code masked()} combine les <b>2 premiers caractères</b> du clair, un
 * masque fixe, et les <b>16 premiers hexa du SHA-256</b> — assez pour <b>diagnostiquer / comparer</b>
 * deux secrets sans permettre de reconstituer la valeur. Ex. {@code "Bo******** (sha256:0a1b2c3d4e5f6a7b)"}.
 *
 * <p>Type immuable, {@code final}. Squelette — corps réel à fournir (étape 8).
 */
public final class Secret {

	private final String value;

	private Secret(String value) {
		this.value = value;
	}

	/** Fabrique un {@code Secret} à partir d'une valeur en clair. */
	public static Secret of(String value) {
		return new Secret(value);
	}

	/**
	 * Valeur <b>en clair</b> — à n'appeler que pour l'usage réel (ex. saisie dans un champ). Tout appel
	 * est un point sensible (aucun masquage ici).
	 */
	public String value() {
		return value;
	}

	/**
	 * Rendu masqué pour diagnostic : 2 premiers caractères + masque + 16 hexa de SHA-256.
	 * Ex. {@code "Bo******** (sha256:0a1b2c3d4e5f6a7b)"}.
	 */
	public String masked() {
		return null;
	}

	/** Préfixe SHA-256 (16 premiers hexa) du clair, pour comparer deux secrets sans les révéler. */
	public String sha256Prefix() {
		return null;
	}

	/** Renvoie toujours le rendu {@link #masked()} : aucun clair ne fuit par {@code toString()}. */
	@Override
	public String toString() {
		return masked();
	}
}
