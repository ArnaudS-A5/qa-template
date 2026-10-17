package com.example.qa.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.InvalidElementStateException;
import org.openqa.selenium.NoSuchElementException;

/**
 * Mécanique « pure » du moteur de synchro ({@link AbstractSyncManager#poll}) : succès, retry sur
 * exceptions transitoires, timeout + diagnostic actif, propagation des exceptions non transitoires.
 * Sans navigateur — {@code action} et {@code diagnosis} sont simulés.
 */
class FluentWaitPollingTest {

	private static final By LOCATOR = By.id("valider");
	private static final Duration TIMEOUT = Duration.ofMillis(80);
	private static final Duration POLLING = Duration.ofMillis(10);

	/** Diagnostic qui compte ses appels et renvoie un jeu de conditions fixe. */
	private static Supplier<Set<SyncCondition>> diagnosisReturning(Set<SyncCondition> conditions, AtomicInteger calls) {
		return () -> {
			calls.incrementAndGet();
			return conditions;
		};
	}

	@Test
	@DisplayName("Succès immédiat : renvoie la valeur, le diagnostic n'est jamais appelé")
	void succesImmediat() {
		AtomicInteger diagCalls = new AtomicInteger();

		String result = AbstractSyncManager.poll(LOCATOR, TIMEOUT, POLLING,
				() -> "ok", diagnosisReturning(EnumSet.of(SyncCondition.ABSENT), diagCalls));

		assertEquals("ok", result);
		assertEquals(0, diagCalls.get());
	}

	@Test
	@DisplayName("Succès après 2 exceptions transitoires absorbées")
	void succesApresRetries() {
		AtomicInteger attempts = new AtomicInteger();
		Supplier<String> flaky = () -> {
			if (attempts.getAndIncrement() < 2) {
				throw new NoSuchElementException("pas encore là");
			}
			return "enfin";
		};

		String result = AbstractSyncManager.poll(LOCATOR, TIMEOUT, POLLING,
				flaky, diagnosisReturning(Set.of(), new AtomicInteger()));

		assertEquals("enfin", result);
		assertEquals(3, attempts.get());
	}

	@Test
	@DisplayName("Timeout : SyncException, cause = dernière ignorée, message multi-codes, latentes en suppressed")
	void timeoutProduitLeDiagnostic() {
		AtomicInteger diagCalls = new AtomicInteger();
		Supplier<String> alwaysBlocked = () -> {
			throw new InvalidElementStateException("désactivé");
		};

		Supplier<Set<SyncCondition>> diagnosis =
				diagnosisReturning(EnumSet.of(SyncCondition.DISABLED, SyncCondition.INTERCEPTED), diagCalls);
		SyncException thrown = assertThrows(SyncException.class,
				() -> AbstractSyncManager.poll(LOCATOR, TIMEOUT, POLLING, alwaysBlocked, diagnosis));

		assertInstanceOf(InvalidElementStateException.class, thrown.getCause());
		assertTrue(thrown.getMessage().contains("[DISABLED]"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains("[INTERCEPTED]"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains("valider"), thrown.getMessage());
		assertEquals(2, thrown.getSuppressed().length);
		assertInstanceOf(ElementClickInterceptedException.class, suppressedOfType(thrown, ElementClickInterceptedException.class));
		assertEquals(1, diagCalls.get()); // diagnostic évalué une seule fois
	}

	@Test
	@DisplayName("Diagnostic vide au timeout → message générique « instable »")
	void timeoutSansDiagnostic() {
		Supplier<String> alwaysBlocked = () -> {
			throw new NoSuchElementException("jamais");
		};

		Supplier<Set<SyncCondition>> diagnosis = diagnosisReturning(Set.of(), new AtomicInteger());
		SyncException thrown = assertThrows(SyncException.class,
				() -> AbstractSyncManager.poll(LOCATOR, TIMEOUT, POLLING, alwaysBlocked, diagnosis));

		assertTrue(thrown.getMessage().contains("indéterminée"), thrown.getMessage());
		assertEquals(0, thrown.getSuppressed().length);
	}

	@Test
	@DisplayName("Exception non transitoire : remontée immédiatement, pas de retry ni de diagnostic")
	void exceptionNonTransitoirePropagee() {
		AtomicInteger attempts = new AtomicInteger();
		AtomicInteger diagCalls = new AtomicInteger();
		Supplier<String> buggy = () -> {
			attempts.incrementAndGet();
			throw new IllegalArgumentException("bug de test, pas de synchro");
		};

		Supplier<Set<SyncCondition>> diagnosis = diagnosisReturning(Set.of(), diagCalls);
		assertThrows(IllegalArgumentException.class,
				() -> AbstractSyncManager.poll(LOCATOR, TIMEOUT, POLLING, buggy, diagnosis));

		assertEquals(1, attempts.get());
		assertEquals(0, diagCalls.get());
	}

	private static Throwable suppressedOfType(Throwable parent, Class<?> type) {
		for (Throwable suppressed : parent.getSuppressed()) {
			if (type.isInstance(suppressed)) {
				return suppressed;
			}
		}
		return null;
	}
}
