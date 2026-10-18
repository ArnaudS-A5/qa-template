package com.example.qa.sync;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.thucydides.core.steps.BaseStepListener;
import net.thucydides.core.steps.StepEventBus;
import net.thucydides.model.domain.TestOutcome;
import net.thucydides.model.domain.TestResult;
import net.thucydides.model.domain.TestStep;
import net.thucydides.model.steps.ExecutedStepDescription;
import net.thucydides.model.steps.StepFailure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Comportement du collecteur soft-assert ({@link SoftAssertions}) en modes hard et soft, et de son drain
 * ({@code assertAllAndClear}). On draine explicitement dans chaque test : le collecteur est ainsi vide
 * quand {@link SoftAssertionExtension} (auto-détectée) passe en fin de test → aucun effet de bord.
 */
class SoftAssertionsTest {

	/** Clé du bus Serenity isolé de ce test. */
	private final String busKey = "SoftAssertionsTest-" + UUID.randomUUID();

	/**
	 * Bus Serenity propre au test, avec son {@code TestOutcome}. Sur le bus installé par Serenity pour le
	 * test JUnit lui-même, {@code SerenityJUnit5Extension} (auto-détectée) relancerait en fin de test les
	 * échecs enregistrés dans le rapport, alors qu'ils sont ici voulus et vérifiés.
	 */
	@BeforeEach
	void busSerenityIsole(@TempDir Path reports) {
		StepEventBus.setCurrentBusToEventBusFor(busKey);
		StepEventBus bus = StepEventBus.getEventBus();
		bus.registerListener(new BaseStepListener(reports.toFile()));
		bus.testSuiteStarted(SoftAssertionsTest.class);
		bus.testStarted("bus isolé");
	}

	@AfterEach
	void purge() {
		System.clearProperty(SoftAssertions.SOFT_KEY);
		StepEventBus.forceClearEventBusFor(busKey);
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

	// ============================================================================================
	// Signalement à Serenity, sur le bus isolé : les étapes sont pilotées comme le fait
	// StepInterceptor (stepStarted / stepFinished).
	// ============================================================================================

	@Test
	@DisplayName("Soft dans une étape : sous-étape rouge avec le message, étape suivante exécutée et verte")
	void softDansUneEtapeCreeUneSousEtapeEnEchec() {
		System.setProperty(SoftAssertions.SOFT_KEY, "true");
		StepEventBus bus = StepEventBus.getEventBus();

		bus.stepStarted(ExecutedStepDescription.withTitle("Vérifier que #cache est visible"));
		SoftAssertions.check(() -> {
			throw new AssertionError("L'élément devrait être visible : By.id: cache");
		});
		bus.stepFinished();
		bus.stepStarted(ExecutedStepDescription.withTitle("Vérifier que #visible est visible"));
		bus.stepFinished();

		TestStep failing = step("Vérifier que #cache est visible");
		assertEquals(TestResult.FAILURE, failing.getResult());
		assertEquals(1, failing.getChildren().size());
		TestStep check = failing.getChildren().get(0);
		assertEquals("L'élément devrait être visible : By.id: cache", check.getDescription());
		assertEquals(TestResult.FAILURE, check.getResult());
		assertTrue(check.getErrorMessage().contains("By.id: cache"), check.getErrorMessage());
		assertEquals(TestResult.SUCCESS, step("Vérifier que #visible est visible").getResult());
		assertFalse(bus.aStepInTheCurrentTestHasFailed(), "le drapeau ferait sauter les étapes suivantes");
		assertFalse(bus.webdriverCallsAreSuspended());
		assertFalse(bus.softAssertsActive(), "le mode soft du bus doit être rétabli");
		assertThrows(AssertionError.class, SoftAssertions::assertAllAndClear);
	}

	@Test
	@DisplayName("Soft : chaque échec a son message, pas seulement le premier du test")
	void chaqueEchecSoftASonMessage() {
		System.setProperty(SoftAssertions.SOFT_KEY, "true");
		StepEventBus bus = StepEventBus.getEventBus();

		for (String name : List.of("premier", "second")) {
			bus.stepStarted(ExecutedStepDescription.withTitle("Étape " + name));
			SoftAssertions.check(() -> {
				throw new AssertionError("échec " + name);
			});
			bus.stepFinished();
		}

		assertTrue(step("échec premier").getErrorMessage().endsWith("échec premier"));
		assertTrue(step("échec second").getErrorMessage().endsWith("échec second"));
		assertThrows(AssertionError.class, SoftAssertions::assertAllAndClear);
	}

	@Test
	@DisplayName("Soft : un échec dur antérieur garde son effet (drapeau du bus non effacé)")
	void echecDurAnterieurConserve() {
		System.setProperty(SoftAssertions.SOFT_KEY, "true");
		StepEventBus bus = StepEventBus.getEventBus();
		ExecutedStepDescription hard = ExecutedStepDescription.withTitle("Échec dur");
		bus.stepStarted(hard);
		bus.stepFailed(new StepFailure(hard, new AssertionError("dur")));

		bus.stepStarted(ExecutedStepDescription.withTitle("Étape suivante"));
		SoftAssertions.check(() -> {
			throw new AssertionError("soft");
		});
		bus.stepFinished();

		assertTrue(bus.aStepInTheCurrentTestHasFailed());
		assertThrows(AssertionError.class, SoftAssertions::assertAllAndClear);
	}

	@Test
	@DisplayName("Soft hors étape : collecte seule, aucune étape ajoutée au rapport")
	void softHorsEtapeCollecteSeulement() {
		System.setProperty(SoftAssertions.SOFT_KEY, "true");
		int stepsBefore = outcome().getFlattenedTestSteps().size();

		SoftAssertions.check(() -> {
			throw new AssertionError("hors étape");
		});

		assertEquals(stepsBefore, outcome().getFlattenedTestSteps().size());
		assertThrows(AssertionError.class, SoftAssertions::assertAllAndClear);
	}

	@Test
	@DisplayName("Mode hard dans une étape : relancé tel quel, aucune sous-étape créée")
	void hardDansUneEtapeNeCreeRien() {
		StepEventBus bus = StepEventBus.getEventBus();
		bus.stepStarted(ExecutedStepDescription.withTitle("Étape en mode dur"));

		assertThrows(AssertionError.class, () -> SoftAssertions.check(() -> {
			throw new AssertionError("dur");
		}));

		bus.stepFinished();
		assertTrue(step("Étape en mode dur").getChildren().isEmpty());
	}

	@Test
	@DisplayName("Extension : test marqué en échec avant de lever, aucune étape imputée")
	void extensionMarqueLeTestSansImputerDEtape() {
		System.setProperty(SoftAssertions.SOFT_KEY, "true");
		StepEventBus bus = StepEventBus.getEventBus();
		bus.stepStarted(ExecutedStepDescription.withTitle("Dernière étape, réussie"));
		bus.stepFinished();
		SoftAssertions.check(() -> {
			throw new AssertionError("hors étape");
		});

		AssertionError aggregate = assertThrows(AssertionError.class,
				() -> new SoftAssertionExtension().afterTestExecution(null));

		assertTrue(aggregate.getMessage().contains("hors étape"), aggregate.getMessage());
		assertEquals(TestResult.FAILURE, outcome().getResult());
		assertEquals(TestResult.SUCCESS, step("Dernière étape, réussie").getResult());
	}

	private static TestOutcome outcome() {
		return StepEventBus.getEventBus().getBaseStepListener().getCurrentTestOutcome();
	}

	private static TestStep step(String description) {
		return outcome().getFlattenedTestSteps().stream()
				.filter(step -> description.equals(step.getDescription()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("étape absente du rapport : " + description));
	}
}
