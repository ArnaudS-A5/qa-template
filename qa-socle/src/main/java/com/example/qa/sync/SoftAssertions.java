package com.example.qa.sync;

import java.util.ArrayList;
import java.util.List;

import net.serenitybdd.model.environment.EnvironmentSpecificConfiguration;
import net.thucydides.model.environment.SystemEnvironmentVariables;
import net.thucydides.model.util.EnvironmentVariables;

/**
 * Collecteur d'assertions <b>souples</b> (soft-assert) du socle, par thread de test.
 *
 * <p><b>Deux modes</b>, pilotés par la clé Serenity {@link #SOFT_KEY} (défaut {@link #SOFT_DEFAULT} =
 * <i>hard</i>) :
 * <ul>
 *   <li><b>hard</b> (défaut) : toute {@link AssertionError} est relancée <b>immédiatement</b>
 *       (fail-fast, comportement JUnit habituel) ;</li>
 *   <li><b>soft</b> ({@code qa.assertions.soft=true}) : l'échec est <b>accumulé</b> et l'exécution
 *       <b>continue</b> ; l'agrégat est levé en <b>fin de test</b> par {@link SoftAssertionExtension}.</li>
 * </ul>
 *
 * <p><b>Pourquoi une extension, pas le listener</b> (D22-bis / piège C1) : un
 * {@code TestExecutionListener} (comme {@code TestFailureManager}) <b>observe</b> l'échec mais tourne
 * <b>après</b> le scellé du verdict — il ne peut pas transformer un test vert en rouge. Seule une
 * extension Jupiter ({@code AfterTestExecutionCallback}) « vote » l'échec au bon moment. Ce collecteur
 * ne fait qu'<b>accumuler</b> ; c'est {@link SoftAssertionExtension} qui <b>draine et fait échouer</b>.
 *
 * <p>Entièrement {@code internal} : appelé par les {@code should…} du domaine sync, jamais par le
 * consommateur. État confiné au thread ({@link ThreadLocal}), purgé en fin de test → sûr en parallèle.
 */
public final class SoftAssertions {

	/** Clé activant le mode souple (défaut {@link #SOFT_DEFAULT}). Lue via la config Serenity (D19). */
	public static final String SOFT_KEY = "qa.assertions.soft";

	/** Mode par défaut : {@code false} = hard (fail-fast). */
	public static final boolean SOFT_DEFAULT = false;

	/** Échecs souples accumulés pour le thread de test courant. Purgé par {@link #assertAllAndClear()}. */
	private static final ThreadLocal<List<AssertionError>> COLLECTED = ThreadLocal.withInitial(ArrayList::new);

	private SoftAssertions() {
	}

	/**
	 * Exécute une assertion en respectant le mode courant : en <b>hard</b>, une {@link AssertionError}
	 * est relancée telle quelle ; en <b>soft</b>, elle est collectée et l'exécution continue. Point
	 * d'entrée unique des {@code should…} du socle.
	 */
	public static void check(Runnable assertion) {
		try {
			assertion.run();
		} catch (AssertionError failure) {
			if (isSoftEnabled()) {
				COLLECTED.get().add(failure);
			} else {
				throw failure;
			}
		}
	}

	/**
	 * Draine les échecs souples du thread courant <b>et purge</b> le collecteur. S'il en reste au moins
	 * un, lève un agrégat (le test échoue), chaque échec d'origine joint en {@code suppressed}. Hors mode
	 * soft, la liste est toujours vide → no-op. Appelé par {@link SoftAssertionExtension} en fin de test.
	 */
	static void assertAllAndClear() {
		List<AssertionError> failures = COLLECTED.get();
		COLLECTED.remove();
		if (failures.isEmpty()) {
			return;
		}
		AssertionError aggregate = new AssertionError(buildMessage(failures));
		failures.forEach(aggregate::addSuppressed);
		throw aggregate;
	}

	private static String buildMessage(List<AssertionError> failures) {
		StringBuilder sb = new StringBuilder(failures.size() + " assertion(s) souple(s) en échec :");
		int index = 1;
		for (AssertionError failure : failures) {
			sb.append("\n  ").append(index++).append(". ").append(failure.getMessage());
		}
		return sb.toString();
	}

	private static boolean isSoftEnabled() {
		EnvironmentVariables env = SystemEnvironmentVariables.createEnvironmentVariables();
		return EnvironmentSpecificConfiguration.from(env).getOptionalProperty(SOFT_KEY)
				.map(Boolean::parseBoolean).orElse(SOFT_DEFAULT);
	}
}
