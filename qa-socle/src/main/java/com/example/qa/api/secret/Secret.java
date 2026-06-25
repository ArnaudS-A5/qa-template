package com.example.qa.api.secret;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Valeur sensible (mot de passe, token...) qui se <b>masque elle-même par défaut</b>.
 *
 * <p><b>Principe — prévention à la source</b> (contrat « valeur sensible » de D12, étape 7) : la
 * valeur en clair n'est accessible que via {@link #value()}, appelé <b>explicitement</b> (typiquement
 * par {@code type(By, Secret)} — commun à {@code WebSync}/{@code MobileSync} — pour remplir un champ).
 * Partout ailleurs — logs, {@code TestStep}
 * Serenity, dumps, concaténation accidentelle — c'est le rendu <b>masqué</b> qui apparaît, car
 * {@link #toString()} ne révèle jamais le clair. Un secret loggué par erreur ne fuit donc pas.
 *
 * <p><b>Rendu masqué</b> : {@code masked()} combine les <b>2 premiers caractères</b> du clair, <b>une
 * étoile par caractère masqué</b> (soit {@code longueur − 2} étoiles → la <b>longueur du secret est
 * volontairement révélée</b>), et les <b>16 premiers hexa du SHA-256</b> — assez pour <b>diagnostiquer /
 * comparer</b> deux secrets sans permettre de reconstituer la valeur. Ex. (secret de 8 caractères)
 * {@code "Bo****** (sha256:0a1b2c3d4e5f6a7b)"}. Format <b>acté en D12</b> (contrat de sortie versionné) ;
 * révéler 2 caractères <b>et la longueur</b> est un compromis <b>assumé</b> (périmètre de test, secrets
 * CyberArk en rotation → fuite de longueur sans impact opérationnel).
 *
 * <p>Type immuable, {@code final}. Masquage <b>implémenté</b> (étape 7) ; format conforme à D12.
 */
public final class Secret {

	/** Nombre de caractères de tête laissés en clair dans le rendu masqué (compromis assumé, D12). */
	private static final int VISIBLE_PREFIX = 2;

	/** Octets de tête du SHA-256 exposés dans le préfixe (8 octets = 16 caractères hexa). */
	private static final int SHA256_PREFIX_BYTES = 8;

	private final String value;

	private Secret(String value) {
		this.value = value;
	}

	/** Fabrique un {@code Secret} à partir d'une valeur en clair (jamais {@code null}). */
	public static Secret of(String value) {
		return new Secret(Objects.requireNonNull(value, "La valeur d'un Secret ne peut pas être null"));
	}

	/**
	 * Valeur <b>en clair</b> — à n'appeler que pour l'usage réel (ex. saisie dans un champ). Tout appel
	 * est un point sensible (aucun masquage ici).
	 */
	public String value() {
		return value;
	}

	/**
	 * Rendu masqué pour diagnostic : 2 premiers caractères + une étoile par caractère masqué (la longueur
	 * réelle est donc révélée) + 16 hexa de SHA-256. Ex. (8 caractères) {@code "Bo****** (sha256:0a1b…)"}.
	 * Pour un secret de 2 caractères ou moins, seuls les caractères réellement présents sont révélés et il
	 * n'y a aucune étoile.
	 */
	public String masked() {
		int visibleCount = Math.min(VISIBLE_PREFIX, value.length());
		String visible = value.substring(0, visibleCount);
		String stars = "*".repeat(value.length() - visibleCount);
		return visible + stars + " (sha256:" + sha256Prefix() + ")";
	}

	/** Préfixe SHA-256 (16 premiers hexa) du clair, pour comparer deux secrets sans les révéler. */
	public String sha256Prefix() {
		return HexFormat.of().formatHex(sha256(value), 0, SHA256_PREFIX_BYTES);
	}

	/** Renvoie toujours le rendu {@link #masked()} : aucun clair ne fuit par {@code toString()}. */
	@Override
	public String toString() {
		return masked();
	}

	/**
	 * SHA-256 de la valeur (encodée en UTF-8). SHA-256 fait partie des algorithmes dont la présence est
	 * <b>garantie</b> sur toute implémentation de la plateforme Java ; l'exception « impossible » est donc
	 * traduite en {@link IllegalStateException} — <b>sans</b> jamais inclure la valeur sensible.
	 */
	private static byte[] sha256(String input) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 indisponible sur cette JVM", e);
		}
	}
}
