package com.example.qa.internal.log;

/**
 * Configuration Logback <b>par défaut</b> du socle pour le <i>log live</i> (console/fichier pendant le
 * run). Mécanisme interne <b>auto-activé</b> par la seule présence du jar (D16-bis) : <b>aucun type
 * public, aucune référence côté consommateur</b>.
 *
 * <p><b>Activation</b> : cette classe implémentera {@code ch.qos.logback.classic.spi.Configurator} et
 * sera déclarée dans {@code META-INF/services/ch.qos.logback.classic.spi.Configurator} (interne au
 * jar). Elle charge {@code logback-socle.xml} (resource du jar) qui cible le namespace stable
 * {@code "qa"} (pas le base package, D17).
 *
 * <p><b>Ordre de résolution Logback</b> : {@code logback-test.xml} → {@code logback.xml} → <b>ce
 * Configurator</b> → défaut nu. Le default du socle ne s'applique donc que si le projet n'a aucun
 * {@code logback.xml} local ; un {@code logback.xml} local le surcharge (opt-out explicite), le
 * supprimer fait retomber sur ce default (jamais sur rien).
 *
 * <p><b>Log d'action de synchro</b> : il n'y a <b>pas</b> de façade de log maison. {@code WebSync} /
 * {@code MobileSync} loggent leurs actions via un {@code org.slf4j.Logger} natif obtenu sous le
 * <b>namespace stable {@link #LOG_NAMESPACE}</b> (« qa », indépendant du base package, D17). Le niveau
 * se règle par la clé {@link #LEVEL_KEY} (défaut {@code WARN}, discret pour ne pas polluer Serenity).
 *
 * <p><b>Défaut socle surchargeable par projet</b> : le {@code logback-socle.xml} fixe ces valeurs par
 * défaut <b>sans empêcher la surcharge</b> — un projet ayant un besoin spécifique pose un
 * {@code logback.xml} local (prioritaire dans l'ordre de résolution ci-dessus) et ajuste le niveau du
 * logger « qa » à sa convenance. Valeurs par défaut au socle, finesse de réglage par projet.
 *
 * <p><b>Portée</b> : ne concerne que le <i>log live</i>. Les 3 fichiers {@code KO__} restent écrits
 * en Java par {@code TestFailureManager} depuis les {@code TestStep} Serenity (masqués), indépendants
 * de toute config Logback (D16-bis).
 *
 * <p>Squelette — implémentation réelle (interface {@code Configurator} + {@code META-INF/services} +
 * {@code logback-socle.xml}) à fournir à l'étape 8.
 */
public final class LogbackConfigurator {

	/** Namespace de log stable du socle (« qa »), indépendant du base package (D17). */
	public static final String LOG_NAMESPACE = "qa";

	/**
	 * Clé de configuration réglant le niveau des logs d'action de synchro (logger « qa »). Défaut
	 * {@code WARN} (discret, ne pollue pas le rapport Serenity). Surchargeable par projet via un
	 * {@code logback.xml} local. Valeurs : {@code TRACE}/{@code DEBUG}/{@code INFO}/{@code WARN}/{@code ERROR}.
	 */
	public static final String LEVEL_KEY = "qa.logger.level";

	public LogbackConfigurator() {
	}
}
