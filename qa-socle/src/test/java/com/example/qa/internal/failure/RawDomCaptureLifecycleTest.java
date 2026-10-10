package com.example.qa.internal.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZonedDateTime;
import java.util.Optional;
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
		RawDomCaptureListener.consumeCapturedDom();
	}

	@Test
	@DisplayName("Consommation destructive : la 2ᵉ lecture est vide")
	void consommationDestructive() {
		RawDomCaptureListener.rememberForTests(DOM_PRECEDENT);

		assertEquals(Optional.of(DOM_PRECEDENT), RawDomCaptureListener.consumeCapturedDom());
		assertTrue(RawDomCaptureListener.consumeCapturedDom().isEmpty(), "la capture ne doit être lue qu'une fois");
	}

	@Test
	@DisplayName("Aucune capture : Optional vide (le consommateur applique son repli)")
	void sansCaptureOptionalVide() {
		assertEquals(Optional.empty(), RawDomCaptureListener.consumeCapturedDom());
	}

	@Test
	@DisplayName("testStarted purge une capture orpheline : pas de fuite vers le test suivant")
	void testStartedPurgeLOrphelin() {
		// Un test précédent a capté un DOM mais ne l'a jamais consommé (artefacts off, test non FAILED…).
		RawDomCaptureListener.rememberForTests(DOM_PRECEDENT);

		listener.testStarted("test suivant sur le même worker");

		assertTrue(RawDomCaptureListener.consumeCapturedDom().isEmpty(),
				"le DOM du test précédent ne doit pas être visible du test suivant");
	}

	@Test
	@DisplayName("Les trois surcharges de testStarted purgent (Serenity peut appeler l'une ou l'autre)")
	void toutesLesSurchargesPurgent() {
		RawDomCaptureListener.rememberForTests(DOM_PRECEDENT);
		listener.testStarted("desc", "id");
		assertTrue(RawDomCaptureListener.consumeCapturedDom().isEmpty(), "surcharge (description, id)");

		RawDomCaptureListener.rememberForTests(DOM_PRECEDENT);
		listener.testStarted("desc", "id", ZonedDateTime.now());
		assertTrue(RawDomCaptureListener.consumeCapturedDom().isEmpty(), "surcharge (description, id, startTime)");
	}

	@Test
	@DisplayName("testRetried purge : une nouvelle tentative repart sur un DOM neuf")
	void retryPurge() {
		RawDomCaptureListener.rememberForTests(DOM_PRECEDENT);

		listener.testRetried();

		assertTrue(RawDomCaptureListener.consumeCapturedDom().isEmpty());
	}
}
