package com.example.qa.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.InvalidElementStateException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.interactions.MoveTargetOutOfBoundsException;

/**
 * Classement des exceptions Selenium vers {@link SyncCondition} : mapping 1:1 et surtout choix de la
 * condition la <b>plus spécifique</b> malgré l'emboîtement d'héritage.
 */
class SyncConditionTest {

	@Test
	@DisplayName("Chaque exception se classe vers sa condition (mapping 1:1)")
	void mappingUnPourUn() {
		assertEquals(SyncCondition.ABSENT, SyncCondition.classify(new NoSuchElementException("x")).orElseThrow());
		assertEquals(SyncCondition.STALE, SyncCondition.classify(new StaleElementReferenceException("x")).orElseThrow());
		assertEquals(SyncCondition.HIDDEN, SyncCondition.classify(new ElementNotInteractableException("x")).orElseThrow());
		assertEquals(SyncCondition.DISABLED, SyncCondition.classify(new InvalidElementStateException("x")).orElseThrow());
		assertEquals(SyncCondition.OUT_OF_BOUNDS, SyncCondition.classify(new MoveTargetOutOfBoundsException("x")).orElseThrow());
		assertEquals(SyncCondition.INTERCEPTED, SyncCondition.classify(new ElementClickInterceptedException("x")).orElseThrow());
	}

	@Test
	@DisplayName("Un clic intercepté → INTERCEPTED (le plus spécifique), jamais HIDDEN/DISABLED parents")
	void interceptedGagneSurLesParents() {
		// ElementClickInterceptedException ⊂ ElementNotInteractableException ⊂ InvalidElementStateException
		SyncCondition classified = SyncCondition.classify(new ElementClickInterceptedException("overlay")).orElseThrow();
		assertEquals(SyncCondition.INTERCEPTED, classified);
	}

	@Test
	@DisplayName("Un « not interactable » (sans interception) → HIDDEN, pas le parent DISABLED")
	void notInteractableGagneSurDisabled() {
		SyncCondition classified = SyncCondition.classify(new ElementNotInteractableException("hidden")).orElseThrow();
		assertEquals(SyncCondition.HIDDEN, classified);
	}

	@Test
	@DisplayName("Une exception hors synchro → Optional vide (non transitoire)")
	void exceptionInconnueNonClassee() {
		Optional<SyncCondition> classified = SyncCondition.classify(new IllegalStateException("bug"));
		assertTrue(classified.isEmpty());
	}

	@Test
	@DisplayName("toException reconstruit l'exception Selenium typée avec le message")
	void materialiseLExceptionLatente() {
		var exception = SyncCondition.DISABLED.toException("présent mais désactivé sur By.id: valider");
		assertInstanceOf(InvalidElementStateException.class, exception);
		assertTrue(exception.getMessage().contains("désactivé"));
	}
}
