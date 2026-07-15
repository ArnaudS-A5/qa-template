package com.example.qa.internal.sync;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.qa.api.sync.SyncTimeout;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

/**
 * Mécanique de {@code tryAssert} (le branchement des {@code should…} sur le soft-assert), <b>sans
 * navigateur</b> : la condition est simulée ({@code tryAssert} ne résout pas le locator lui-même, il ne
 * l'utilise que dans le message). Timeouts raccourcis par config pour un test rapide. Le mode <b>soft</b>
 * (collecte via {@code SoftAssertions.check}) est couvert par {@code SoftAssertionsTest}.
 */
class TryAssertTest {

	private static final By LOCATOR = By.id("valider");

	private AbstractSyncManager sync() {
		return new AbstractSyncManager(SyncTimeout.MEDIUM) {
			@Override
			public AbstractSyncManager within(By container) {
				return null;
			}
		};
	}

	@BeforeEach
	void fastTimeouts() {
		System.setProperty("qa.sync.timeout.medium", "60ms");
		System.setProperty("qa.sync.pollingInterval", "10ms");
	}

	@AfterEach
	void clear() {
		System.clearProperty("qa.sync.timeout.medium");
		System.clearProperty("qa.sync.pollingInterval");
	}

	@Test
	@DisplayName("Condition vraie : aucune AssertionError")
	void conditionVraie() {
		assertDoesNotThrow(() -> sync().tryAssert(LOCATOR, () -> true, "devrait"));
	}

	@Test
	@DisplayName("Condition qui devient vraie avant le timeout : passe")
	void conditionDevientVraie() {
		AtomicInteger tries = new AtomicInteger();
		assertDoesNotThrow(() -> sync().tryAssert(LOCATOR, () -> tries.incrementAndGet() >= 3, "devrait"));
	}

	@Test
	@DisplayName("Mode hard (défaut) : condition fausse → AssertionError au timeout (message + locator)")
	void hardConditionFausse() {
		AbstractSyncManager sync = sync();
		AssertionError error = assertThrows(AssertionError.class,
				() -> sync.tryAssert(LOCATOR, () -> false, "devrait être visible"));
		assertTrue(error.getMessage().contains("devrait être visible"), error.getMessage());
		assertTrue(error.getMessage().contains("valider"), error.getMessage());
	}
}
