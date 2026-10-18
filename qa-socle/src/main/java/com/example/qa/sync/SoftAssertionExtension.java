package com.example.qa.sync;

import net.thucydides.core.steps.StepEventBus;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Extension JUnit Jupiter qui <b>scelle</b> le verdict du soft-assert : juste après le corps du test et
 * <b>avant</b> que le moteur fige le résultat, elle draine {@link SoftAssertions} et, s'il reste des
 * échecs souples accumulés, <b>lève l'agrégat</b> (le test échoue). C'est le seul point où l'échec peut
 * encore être « voté » — un {@code TestExecutionListener} tournerait trop tard (D22-bis / piège C1).
 *
 * <p><b>Activation native</b> : déclarée dans
 * {@code META-INF/services/org.junit.jupiter.api.extension.Extension} et activée par
 * {@code junit.jupiter.extensions.autodetection.enabled=true} (Parent POM, hérité par les 17 projets) —
 * <b>aucune annotation ni {@code @ExtendWith} côté test</b>. Hors mode soft, le drain est un no-op
 * (collecteur vide).
 *
 * <p><b>Échec rattaché au test</b> : avant de lever l'agrégat, l'extension marque le {@code TestOutcome}
 * Serenity en échec. Sans cela, quand aucune étape n'est rouge (échecs soft tous hors {@code @Step}),
 * {@code SerenityTestExecutionListener} impute l'échec du test à la <b>dernière étape</b>, qui a pu
 * réussir (comportement Serenity 4.2.22, PA-15). Best-effort : sans Serenity, l'agrégat est levé seul.
 */
public final class SoftAssertionExtension implements AfterTestExecutionCallback {

	@Override
	public void afterTestExecution(ExtensionContext context) {
		try {
			SoftAssertions.assertAllAndClear();
		} catch (AssertionError aggregate) {
			markTestFailed(aggregate);
			throw aggregate;
		}
	}

	/** Marque le test courant en échec dans Serenity, sans l'imputer à une étape. */
	private static void markTestFailed(AssertionError aggregate) {
		try {
			StepEventBus bus = StepEventBus.getEventBus();
			if (bus.isBaseStepListenerRegistered()) {
				bus.getBaseStepListener().getCurrentTestOutcome().testFailedWith(aggregate);
			}
		} catch (RuntimeException reportingIssue) {
			// best-effort : l'agrégat levé suffit à faire échouer le test
		}
	}
}
