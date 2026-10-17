package com.example.qa.sync;

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
 */
public final class SoftAssertionExtension implements AfterTestExecutionCallback {

	@Override
	public void afterTestExecution(ExtensionContext context) {
		SoftAssertions.assertAllAndClear();
	}
}
