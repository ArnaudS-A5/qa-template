package com.example.qa.sync;

import java.util.ArrayList;
import java.util.List;

import net.serenitybdd.model.environment.EnvironmentSpecificConfiguration;
import net.thucydides.core.steps.StepEventBus;
import net.thucydides.model.environment.SystemEnvironmentVariables;
import net.thucydides.model.steps.ExecutedStepDescription;
import net.thucydides.model.steps.StepFailure;
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
 * <p>Package-private (D15) : appelé par les {@code should…} du domaine sync, jamais par le
 * consommateur. État confiné au thread ({@link ThreadLocal}), purgé en fin de test → sûr en parallèle.
 */
final class SoftAssertions {

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
	 *
	 * <p><b>Signalement à Serenity (mode soft)</b> : si une {@code @Step} est en cours, l'échec est
	 * aussi rapporté comme une <b>sous-étape de vérification</b> en échec, titrée par le message de
	 * l'assertion (le procédé de {@code Serenity.reportThat}, sans relancer). L'étape du test devient
	 * rouge par sa sous-étape, avec message et capture ; les étapes suivantes s'exécutent normalement.
	 * Trois réglages du bus Serenity 4.2.22 sont tenus pendant ce signalement (PA-15) :
	 * <ul>
	 *   <li>le mode soft du bus est activé le temps du {@code stepFailed} : sans lui,
	 *       {@code BaseStepListener} n'enregistre message et capture que pour le <b>premier</b> échec du
	 *       test ;</li>
	 *   <li>le drapeau « une étape a échoué » est rétabli à sa valeur antérieure : levé, il fait
	 *       <b>sauter</b> les étapes suivantes et <b>suspend</b> les appels WebDriver ;</li>
	 *   <li>un état antérieur est conservé tel quel : un vrai échec dur survenu plus tôt garde son
	 *       effet.</li>
	 * </ul>
	 * Hors étape (corps du test, PageObject appelé hors {@code @Step}), l'échec est seulement collecté.
	 * Best-effort : un incident de signalement ne fait jamais échouer le test, l'échec étant déjà
	 * collecté.
	 */
	public static void check(Runnable assertion) {
		try {
			assertion.run();
		} catch (AssertionError failure) {
			if (!isSoftEnabled()) {
				throw failure;
			}
			COLLECTED.get().add(failure);
			try {
				StepEventBus bus = StepEventBus.getEventBus();
				if (bus.areStepsRunning()) {
					boolean softBefore = bus.softAssertsActive();
					boolean failedBefore = bus.aStepInTheCurrentTestHasFailed();
					ExecutedStepDescription check = ExecutedStepDescription.withTitle(failure.getMessage());
					bus.stepStarted(check);
					bus.enableSoftAsserts(); // message + capture enregistrés à chaque échec
					try {
						bus.stepFailed(new StepFailure(check, failure));
					} finally {
						if (!softBefore) {
							bus.disableSoftAsserts();
						}
						if (!failedBefore) {
							bus.clearStepFailures(); // pas de saut, pas de WebDriver suspendu
						}
					}
				}
			} catch (RuntimeException reportingIssue) {
				// signalement best-effort : l'échec est collecté, il fera échouer le test en fin de test
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
