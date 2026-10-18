package com.example.qa.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import net.thucydides.core.webdriver.ThucydidesWebDriverSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

/**
 * Collections {@code count} / {@code getTexts} (BF-SYNC-10) : état courant, <b>sans attente</b>. Chaque
 * recherche doit s'exécuter avec l'implicit wait Serenity neutralisé à 0, y compris la résolution du
 * container d'une vue {@code within} — sinon un élément absent coûte l'implicit wait entier (T43 de
 * {@code qa-test} : ~3 s au lieu de quelques millisecondes).
 */
class CollectionsTest {

	/** Implicit wait du consommateur ({@code webdriver.timeouts.implicitlywait = 3000}). */
	private static final Duration CONSUMER_IMPLICIT_WAIT = Duration.ofSeconds(3);

	private RecordingDriver driver;

	@BeforeEach
	void injectDriver() {
		driver = new RecordingDriver(CONSUMER_IMPLICIT_WAIT);
		ThucydidesWebDriverSupport.useDriver(driver);
	}

	@AfterEach
	void releaseDriver() {
		ThucydidesWebDriverSupport.closeAllDrivers();
	}

	@Test
	@DisplayName("count(absent) rend 0, recherche faite sans implicit wait, puis implicit wait restauré")
	void countAbsentSansImplicitWait() {
		assertEquals(0, new WebSync().count(By.id("absent")));

		assertLookupsWithoutImplicitWait();
		assertEquals(CONSUMER_IMPLICIT_WAIT, driver.getCurrentImplicitTimeout());
	}

	@Test
	@DisplayName("getTexts(absent) rend une liste vide, recherche faite sans implicit wait")
	void getTextsAbsentSansImplicitWait() {
		assertTrue(new WebSync().getTexts(By.id("absent")).isEmpty());

		assertLookupsWithoutImplicitWait();
		assertEquals(CONSUMER_IMPLICIT_WAIT, driver.getCurrentImplicitTimeout());
	}

	@Test
	@DisplayName("within(container absent).count rend 0, résolution du container faite sans implicit wait")
	void withinContainerAbsentSansImplicitWait() {
		assertEquals(0, new WebSync().within(By.id("absent")).count(By.tagName("li")));

		assertLookupsWithoutImplicitWait();
		assertEquals(CONSUMER_IMPLICIT_WAIT, driver.getCurrentImplicitTimeout());
	}

	private void assertLookupsWithoutImplicitWait() {
		assertFalse(driver.implicitWaitSeenByLookups().isEmpty(), "aucune recherche n'a atteint le driver");
		driver.implicitWaitSeenByLookups().forEach(seen -> assertEquals(Duration.ZERO, seen));
	}
}
