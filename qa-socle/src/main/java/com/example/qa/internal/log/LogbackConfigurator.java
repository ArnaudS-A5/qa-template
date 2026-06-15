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
 * <p><b>Portée</b> : ne concerne que le <i>log live</i>. Les 3 fichiers {@code KO__} restent écrits
 * en Java par {@code TestFailureManager} depuis le buffer masqué (D16-bis), indépendants de toute
 * config Logback.
 *
 * <p>Squelette — implémentation réelle (interface {@code Configurator} + {@code META-INF/services} +
 * {@code logback-socle.xml}) à fournir à l'étape 8.
 */
public final class LogbackConfigurator {

	public LogbackConfigurator() {
	}
}
