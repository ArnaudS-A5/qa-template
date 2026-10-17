package com.example.qa.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.thucydides.core.webdriver.exceptions.ElementShouldBeEnabledException;
import net.thucydides.core.webdriver.exceptions.ElementShouldBeInvisibleException;
import net.thucydides.core.webdriver.exceptions.ElementShouldBePresentException;
import net.thucydides.core.webdriver.exceptions.ElementShouldBeVisibleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.InvalidElementStateException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.interactions.MoveTargetOutOfBoundsException;

/**
 * Classification des exceptions par {@link SyncCondition#classify} : mapping 1:1 Selenium, respect de
 * la <b>spécificité</b> dans les hiérarchies emboîtées, et traduction des exceptions <b>Serenity</b>
 * (lot C8) sans absorber {@code TimeoutException} en bloc.
 */
class SyncConditionClassifyTest {

	@Test
	@DisplayName("Mapping 1:1 des exceptions Selenium vers leur condition")
	void mappingSelenium() {
		assertEquals(Optional.of(SyncCondition.ABSENT), SyncCondition.classify(new NoSuchElementException("x")));
		assertEquals(Optional.of(SyncCondition.STALE), SyncCondition.classify(new StaleElementReferenceException("x")));
		assertEquals(Optional.of(SyncCondition.OUT_OF_BOUNDS),
				SyncCondition.classify(new MoveTargetOutOfBoundsException("x")));
	}

	@Test
	@DisplayName("Hiérarchie emboîtée : la condition la PLUS SPÉCIFIQUE l'emporte")
	void conditionLaPlusSpecifique() {
		// ElementClickIntercepted ⊂ ElementNotInteractable ⊂ InvalidElementState
		assertEquals(Optional.of(SyncCondition.INTERCEPTED),
				SyncCondition.classify(new ElementClickInterceptedException("x")));
		assertEquals(Optional.of(SyncCondition.HIDDEN),
				SyncCondition.classify(new ElementNotInteractableException("x")));
		assertEquals(Optional.of(SyncCondition.DISABLED),
				SyncCondition.classify(new InvalidElementStateException("x")));
	}

	@Test
	@DisplayName("Serenity : ElementShouldBePresent → ABSENT (hérite de TimeoutException, sinon non classée)")
	void serenityAbsent() {
		ElementShouldBePresentException absent = new ElementShouldBePresentException("absent", new RuntimeException());

		assertTrue(absent instanceof TimeoutException, "prérequis : hérite bien de TimeoutException");
		assertEquals(Optional.of(SyncCondition.ABSENT), SyncCondition.classify(absent));
	}

	@Test
	@DisplayName("Serenity : ElementShouldBeEnabled → DISABLED (hérite de TimeoutException, sinon non classée)")
	void serenityDisabled() {
		ElementShouldBeEnabledException disabled = new ElementShouldBeEnabledException("désactivé");

		assertTrue(disabled instanceof TimeoutException, "prérequis : hérite bien de TimeoutException");
		assertEquals(Optional.of(SyncCondition.DISABLED), SyncCondition.classify(disabled));
	}

	@Test
	@DisplayName("Serenity : ElementShouldBeVisible → HIDDEN par héritage (non listée explicitement)")
	void serenityHiddenParHeritage() {
		ElementShouldBeVisibleException hidden = new ElementShouldBeVisibleException("invisible", new RuntimeException());

		assertTrue(hidden instanceof ElementNotInteractableException, "prérequis : hérite d'ElementNotInteractable");
		assertEquals(Optional.of(SyncCondition.HIDDEN), SyncCondition.classify(hidden));
	}

	@Test
	@DisplayName("TimeoutException nu N'EST PAS absorbé (sinon on masquerait de vraies erreurs)")
	void timeoutNuNonClasse() {
		assertEquals(Optional.empty(), SyncCondition.classify(new TimeoutException("timeout générique")));
	}

	@Test
	@DisplayName("Attente négative Serenity non classée : rien ne bloque l'interaction")
	void attenteNegativeNonClassee() {
		assertEquals(Optional.empty(),
				SyncCondition.classify(new ElementShouldBeInvisibleException("toujours visible", new RuntimeException())));
	}

	@Test
	@DisplayName("Exception hors synchro : non classée → remontée telle quelle par le moteur")
	void horsSynchroNonClasse() {
		assertEquals(Optional.empty(), SyncCondition.classify(new IllegalArgumentException("bug de test")));
		assertEquals(Optional.empty(), SyncCondition.classify(new WebDriverException("panne driver")));
	}
}
