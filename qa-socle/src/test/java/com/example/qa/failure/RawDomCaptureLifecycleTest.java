package com.example.qa.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.qa.failure.RawDomCaptureListener.CapturedDom;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cycle de vie du {@code ThreadLocal} de capture DOM (PA-11) : une capture <b>non consommée</b> ne doit
 * jamais fuir vers le test suivant exécuté sur le même thread worker.
 *
 * <p>Le chemin nominal (capture au {@code stepFailed} puis consommation par {@code TestFailureManager})
 * exige un vrai navigateur et relève de {@code qa-test}. Ce qui se vérifie ici est l'<b>hygiène du
 * porteur</b> : purge à l'ouverture de chaque test et consommation destructive — testables sans driver
 * grâce au point d'entrée package-private {@code rememberForTests}.
 */
class RawDomCaptureLifecycleTest {

	private static final String DOM_PRECEDENT = "<html><body>test précédent</body></html>";

	private final RawDomCaptureListener listener = new RawDomCaptureListener();

	@BeforeEach
	void ardoiseVierge() {
		RawDomCaptureListener.consumeCapturedDoms();
	}

	@Test
	@DisplayName("Consommation destructive : la 2ᵉ lecture est vide")
	void consommationDestructive() {
		RawDomCaptureListener.rememberForTests("step", DOM_PRECEDENT);

		assertEquals(List.of(new CapturedDom("step", DOM_PRECEDENT)), RawDomCaptureListener.consumeCapturedDoms());
		assertTrue(RawDomCaptureListener.consumeCapturedDoms().isEmpty(), "la capture ne doit être lue qu'une fois");
	}

	@Test
	@DisplayName("Plusieurs steps en échec : une capture par step, dans l'ordre des échecs")
	void uneCaptureParStepDansLOrdre() {
		RawDomCaptureListener.rememberForTests("premier échec", "<html>1</html>");
		RawDomCaptureListener.rememberForTests("second échec", "<html>2</html>");

		assertEquals(List.of(new CapturedDom("premier échec", "<html>1</html>"),
				new CapturedDom("second échec", "<html>2</html>")), RawDomCaptureListener.consumeCapturedDoms());
	}

	@Test
	@DisplayName("Aucune capture : liste vide (le consommateur applique son repli)")
	void sansCaptureListeVide() {
		assertTrue(RawDomCaptureListener.consumeCapturedDoms().isEmpty());
	}

	@Test
	@DisplayName("testStarted purge une capture orpheline : pas de fuite vers le test suivant")
	void testStartedPurgeLOrphelin() {
		// Un test précédent a capté un DOM mais ne l'a jamais consommé (artefacts off, test non FAILED…).
		RawDomCaptureListener.rememberForTests("step", DOM_PRECEDENT);

		listener.testStarted("test suivant sur le même worker");

		assertTrue(RawDomCaptureListener.consumeCapturedDoms().isEmpty(),
				"le DOM du test précédent ne doit pas être visible du test suivant");
	}

	@Test
	@DisplayName("Les trois surcharges de testStarted purgent (Serenity peut appeler l'une ou l'autre)")
	void toutesLesSurchargesPurgent() {
		RawDomCaptureListener.rememberForTests("step", DOM_PRECEDENT);
		listener.testStarted("desc", "id");
		assertTrue(RawDomCaptureListener.consumeCapturedDoms().isEmpty(), "surcharge (description, id)");

		RawDomCaptureListener.rememberForTests("step", DOM_PRECEDENT);
		listener.testStarted("desc", "id", ZonedDateTime.now());
		assertTrue(RawDomCaptureListener.consumeCapturedDoms().isEmpty(), "surcharge (description, id, startTime)");
	}

	@Test
	@DisplayName("testRetried purge : une nouvelle tentative repart sur un DOM neuf")
	void retryPurge() {
		RawDomCaptureListener.rememberForTests("step", DOM_PRECEDENT);

		listener.testRetried();

		assertTrue(RawDomCaptureListener.consumeCapturedDoms().isEmpty());
	}
}
