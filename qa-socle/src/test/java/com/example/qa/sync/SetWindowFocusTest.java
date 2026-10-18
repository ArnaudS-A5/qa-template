package com.example.qa.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import net.thucydides.core.webdriver.ThucydidesWebDriverSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

/**
 * {@code setWindowFocus(By)} : le {@code By} est une condition de présence. Le {@code setWindowFocus()} de
 * Serenity ne touche jamais l'élément ; sans résolution forcée, un élément absent rendait un faux succès
 * (T76 de {@code qa-test}).
 */
class SetWindowFocusTest {

	private RecordingDriver driver;

	@BeforeEach
	void injectDriver() {
		System.setProperty("qa.sync.timeout.short", "300ms"); // test rapide : expiration en 300 ms
		driver = new RecordingDriver(Duration.ofSeconds(3));
		ThucydidesWebDriverSupport.useDriver(driver);
	}

	@AfterEach
	void releaseDriver() {
		ThucydidesWebDriverSupport.closeAllDrivers();
		System.clearProperty("qa.sync.timeout.short");
	}

	@Test
	@DisplayName("Élément présent : window.focus() exécuté une fois")
	void presentDonneLeFocusALaFenetre() {
		driver.withElement(By.id("cible"));

		new WebSync(SyncTimeout.SHORT).setWindowFocus(By.id("cible"));

		assertEquals(List.of("window.focus()"), driver.executedScripts());
	}

	@Test
	@DisplayName("Élément absent : SyncException [ABSENT] au timeout, window.focus() jamais exécuté")
	void absentLeveSyncExceptionAbsent() {
		WebSync web = new WebSync(SyncTimeout.SHORT);

		SyncException failure = assertThrows(SyncException.class, () -> web.setWindowFocus(By.id("absent")));

		assertTrue(failure.getMessage().contains("[ABSENT]"), failure.getMessage());
		assertTrue(driver.executedScripts().isEmpty(), "window.focus() exécuté malgré l'absence");
	}
}
