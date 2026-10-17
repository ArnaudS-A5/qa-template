package com.example.qa.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

/**
 * Résolution des profils de timeout depuis la config (partie « pure » de 2a, testable sans navigateur) :
 * défauts des profils, surcharge par clé {@code qa.sync.*}, et parsing des durées.
 */
class SyncTimeoutResolutionTest {

	/** Instance minimale d'{@link AbstractSyncManager} pour un profil donné (within non utilisé ici). */
	private static AbstractSyncManager syncWith(SyncTimeout profile) {
		return new AbstractSyncManager(profile) {
			@Override
			public AbstractSyncManager within(By container) {
				return null;
			}
		};
	}

	@AfterEach
	void clearProps() {
		System.clearProperty("qa.sync.timeout.medium");
		System.clearProperty("qa.sync.timeout.long");
		System.clearProperty("qa.sync.pollingInterval");
		System.clearProperty("qa.sync.pageLoadTimeout");
	}

	@Test
	@DisplayName("Sans config, chaque profil rend son défaut (5/15/60/120 s)")
	void defautsDesProfils() {
		assertEquals(Duration.ofSeconds(5), syncWith(SyncTimeout.SHORT).resolveTimeout());
		assertEquals(Duration.ofSeconds(15), syncWith(SyncTimeout.MEDIUM).resolveTimeout());
		assertEquals(Duration.ofSeconds(60), syncWith(SyncTimeout.LONG).resolveTimeout());
		assertEquals(Duration.ofSeconds(120), syncWith(SyncTimeout.EXTRA_LONG).resolveTimeout());
	}

	@Test
	@DisplayName("La clé qa.sync.timeout.<profil> surcharge le défaut")
	void surchargeParCle() {
		System.setProperty("qa.sync.timeout.long", "30s");
		assertEquals(Duration.ofSeconds(30), syncWith(SyncTimeout.LONG).resolveTimeout());
		// un autre profil n'est pas affecté
		assertEquals(Duration.ofSeconds(15), syncWith(SyncTimeout.MEDIUM).resolveTimeout());
	}

	@Test
	@DisplayName("Le polling a un défaut de 250 ms, surchargeable par clé")
	void pollingParDefautEtSurcharge() {
		assertEquals(AbstractSyncManager.POLLING_INTERVAL_DEFAULT, AbstractSyncManager.resolvePolling());
		System.setProperty("qa.sync.pollingInterval", "100ms");
		assertEquals(Duration.ofMillis(100), AbstractSyncManager.resolvePolling());
	}

	@Test
	@DisplayName("parseDuration comprend s, ms et un nombre nu (secondes)")
	void parsingDesDurees() {
		assertEquals(Duration.ofSeconds(15), AbstractSyncManager.parseDuration("15s"));
		assertEquals(Duration.ofMillis(500), AbstractSyncManager.parseDuration("500ms"));
		assertEquals(Duration.ofSeconds(10), AbstractSyncManager.parseDuration(" 10 "));
	}

	@Test
	@DisplayName("Le plafond de page load a un défaut de 2 min, surchargeable par clé (lot 2c)")
	void pageLoadTimeoutParDefautEtSurcharge() {
		assertEquals(Duration.ofMinutes(2), syncWith(SyncTimeout.MEDIUM).resolvePageLoadTimeout());
		System.setProperty("qa.sync.pageLoadTimeout", "45s");
		assertEquals(Duration.ofSeconds(45), syncWith(SyncTimeout.MEDIUM).resolvePageLoadTimeout());
	}
}
