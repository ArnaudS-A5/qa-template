package com.example.qa.sync;

import java.time.Duration;

/**
 * Profil de timeout de synchronisation, choisi par le consommateur à la construction d'un
 * {@code WebSync}/{@code MobileSync} selon la <b>lenteur attendue de la zone</b> — jamais une durée en
 * dur dans le code de la page. Chaque profil porte une clé de configuration {@code qa.sync.timeout.*}
 * dont la valeur est <b>pilotable</b> (globalement, par environnement, ou par {@code -D} en campagne),
 * sans toucher aux PageObjects.
 *
 * <p>Plusieurs profils peuvent coexister sur une même page (un {@code WebSync} par grain de synchro),
 * tous adossés au même driver du thread courant — le profil est le seul état propre à l'instance.
 *
 * <pre>{@code
 * private final WebSync sync          = new WebSync();                  // MEDIUM (défaut)
 * private final WebSync syncSignature = new WebSync(SyncTimeout.EXTRA_LONG); // module lent
 * }</pre>
 *
 * <p>Les durées ci-dessous sont les <b>défauts</b> (surchargés par la clé correspondante). La résolution
 * effective (lecture de la config) est faite en interne par le moteur de synchro.
 */
public enum SyncTimeout {

	/** Éléments rapides. Défaut 5 s — clé {@code qa.sync.timeout.short}. */
	SHORT("qa.sync.timeout.short", Duration.ofSeconds(5)),

	/** Défaut standard ({@code new WebSync()}). 15 s — clé {@code qa.sync.timeout.medium}. */
	MEDIUM("qa.sync.timeout.medium", Duration.ofSeconds(15)),

	/** Zones lentes (gros modules). Défaut 60 s — clé {@code qa.sync.timeout.long}. */
	LONG("qa.sync.timeout.long", Duration.ofSeconds(60)),

	/** Modules très lourds (ex. signature de contrats). Défaut 120 s — clé {@code qa.sync.timeout.extraLong}. */
	EXTRA_LONG("qa.sync.timeout.extraLong", Duration.ofSeconds(120));

	private final String key;
	private final Duration defaultValue;

	SyncTimeout(String key, Duration defaultValue) {
		this.key = key;
		this.defaultValue = defaultValue;
	}

	/** Clé de configuration {@code qa.sync.timeout.*} qui surcharge le défaut de ce profil. */
	public String key() {
		return key;
	}

	/** Durée par défaut du profil (utilisée si la clé n'est pas configurée). */
	public Duration defaultValue() {
		return defaultValue;
	}
}
