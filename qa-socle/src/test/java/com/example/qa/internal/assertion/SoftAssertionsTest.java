package com.example.qa.internal.assertion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comportement du collecteur soft-assert ({@link SoftAssertions}) en modes hard et soft, et de son drain
 * ({@code assertAllAndClear}). On draine explicitement dans chaque test : le collecteur est ainsi vide
 * quand {@link SoftAssertionExtension} (auto-détectée) passe en fin de test → aucun effet de bord.
 */
class SoftAssertionsTest {

	@AfterEach
	void purge() {
		System.clearProperty(SoftAssertions.SOFT_KEY);
		SoftAssertions.assertAllAndClear(); // filet : vide tout résidu pour le thread réutilisé
	}

	@Test
	@DisplayName("Mode hard (défaut) : l'assertion en échec est relancée immédiatement")
	void modeHardRelanceImmediatement() {
		AssertionError e = assertThrows(AssertionError.class,
				() -> SoftAssertions.check(() -> {
					throw new AssertionError("boom");
				}));
		assertEquals("boom", e.getMessage());
	}

	@Test
	@DisplayName("Mode soft : les échecs sont accumulés puis agrégés au drain (chacun en suppressed)")
	void modeSoftAccumuleEtAgrege() {
		System.setProperty(SoftAssertions.SOFT_KEY, "true");

		SoftAssertions.check(() -> {
			throw new AssertionError("A");
		});
		SoftAssertions.check(() -> {
			throw new AssertionError("B");
		});
		SoftAssertions.check(() -> { /* succès : rien à collecter */ });

		AssertionError aggregate = assertThrows(AssertionError.class, SoftAssertions::assertAllAndClear);
		assertTrue(aggregate.getMessage().contains("A"), aggregate.getMessage());
		assertTrue(aggregate.getMessage().contains("B"), aggregate.getMessage());
		assertEquals(2, aggregate.getSuppressed().length);
	}

	@Test
	@DisplayName("Mode soft sans échec : le drain ne lève rien")
	void modeSoftSansEchec() {
		System.setProperty(SoftAssertions.SOFT_KEY, "true");

		SoftAssertions.check(() -> { /* passe */ });

		assertDoesNotThrow(SoftAssertions::assertAllAndClear);
	}

	@Test
	@DisplayName("Le drain purge : un second drain ne relève pas les mêmes échecs")
	void drainPurgeLeCollecteur() {
		System.setProperty(SoftAssertions.SOFT_KEY, "true");
		SoftAssertions.check(() -> {
			throw new AssertionError("une fois");
		});

		assertThrows(AssertionError.class, SoftAssertions::assertAllAndClear);
		assertDoesNotThrow(SoftAssertions::assertAllAndClear); // collecteur déjà vidé
	}
}
