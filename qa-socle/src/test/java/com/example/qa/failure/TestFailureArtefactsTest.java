package com.example.qa.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.qa.failure.RawDomCaptureListener.CapturedDom;
import com.example.qa.failure.TestFailureManager.FailureArtefacts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.thucydides.model.domain.TestResult;
import net.thucydides.model.domain.TestStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests des parties pures de {@link TestFailureManager} (nommage, sanitization, écriture des 3 fichiers).
 * Sans Serenity ni navigateur : on appelle directement le cœur testable {@code writeArtefacts(...)}.
 */
class TestFailureArtefactsTest {

	private static final ZonedDateTime WHEN = ZonedDateTime.of(2026, 6, 25, 14, 30, 5, 0, ZoneId.of("UTC"));

	@Test
	@DisplayName("Nom de dossier = KO__{ENV}__{Test}__{ts} (délimiteur __)")
	void nomDeDossierRespecteLeContrat() {
		assertEquals("KO__RECETTE__monTest__2026-06-25_14-30-05",
				TestFailureManager.folderName("RECETTE", "monTest", WHEN));
	}

	@Test
	@DisplayName("sanitize translittère les accents et neutralise les caractères interdits (pas de __ parasite)")
	void sanitizeNeutraliseLesCaracteresInterdits() {
		assertEquals("echecProvoque", TestFailureManager.sanitize("echecProvoque()"));
		assertEquals("clic_sur_bouton", TestFailureManager.sanitize("clic sur bouton"));
		assertEquals("a_b_c", TestFailureManager.sanitize("a/b\\c"));
		assertEquals("Verifier_quon_a_reussi", TestFailureManager.sanitize("Vérifier qu'on a réussi"));
		assertEquals("Un_echec_de_connexion_est_capture", TestFailureManager.sanitize("Un échec de connexion est capturé"));
		assertEquals("unknown", TestFailureManager.sanitize(""));
		assertEquals("unknown", TestFailureManager.sanitize(null));
	}

	@Test
	@DisplayName("failingLeaf renvoie la feuille KO (l'action fautive), pas la step parente")
	void failingLeafRenvoieLaFeuille() {
		TestStep parent = new TestStep("groupe parent");
		parent.addChildStep(new TestStep("enfant"));    // a des enfants -> pas une feuille
		parent.setResult(TestResult.FAILURE);           // échec propagé au parent
		TestStep leaf = new TestStep("action fautive"); // feuille KO
		leaf.setResult(TestResult.FAILURE);

		Optional<TestStep> result = TestFailureManager.failingLeaf(List.of(parent, leaf));

		assertEquals("action fautive", result.orElseThrow().getDescription());
	}

	@Test
	@DisplayName("Écrit les 3 fichiers (ERROR.log, FAIL.log, {step}.html) dans le bon dossier")
	void ecritLesTroisFichiers(@TempDir Path tmp) throws IOException {
		FailureArtefacts content = new FailureArtefacts(
				"NullPointerException: x", "=== Cause ===\ntrace\n[FAILURE] step1",
				List.of(new CapturedDom("clic sur bouton", "<html>dump</html>")));

		Path dir = TestFailureManager.writeArtefacts(tmp, "INTEG", "monTest", WHEN, content);

		assertEquals("KO__INTEG__monTest__2026-06-25_14-30-05", dir.getFileName().toString());
		assertEquals("NullPointerException: x", Files.readString(dir.resolve("ERROR.log")));
		assertTrue(Files.readString(dir.resolve("FAIL.log")).contains("step1"));
		assertEquals("<html>dump</html>", Files.readString(dir.resolve("clic_sur_bouton.html")));
	}

	@Test
	@DisplayName("Un dump HTML par step en échec ; même nom de step → suffixes -2, -3")
	void unDumpParStepEnEchec(@TempDir Path tmp) throws IOException {
		FailureArtefacts content = new FailureArtefacts("e", "f", List.of(
				new CapturedDom("L'élément devrait être visible : By.id: cache", "<html>1</html>"),
				new CapturedDom("L'élément devrait être présent : By.id: absent", "<html>2</html>"),
				new CapturedDom("L'élément devrait être visible : By.id: cache", "<html>3</html>"),
				new CapturedDom("L'élément devrait être visible : By.id: cache", "<html>4</html>")));

		Path dir = TestFailureManager.writeArtefacts(tmp, "INTEG", "t", WHEN, content);

		assertEquals("<html>1</html>", Files.readString(dir.resolve("Lelement_devrait_etre_visible_By.id_cache.html")));
		assertEquals("<html>2</html>", Files.readString(dir.resolve("Lelement_devrait_etre_present_By.id_absent.html")));
		assertEquals("<html>3</html>", Files.readString(dir.resolve("Lelement_devrait_etre_visible_By.id_cache-2.html")));
		assertEquals("<html>4</html>", Files.readString(dir.resolve("Lelement_devrait_etre_visible_By.id_cache-3.html")));
		try (Stream<Path> files = Files.list(dir)) {
			assertEquals(6, files.count());
		}
	}

	@Test
	@DisplayName("Pas de dump HTML sans capture (seulement ERROR.log + FAIL.log)")
	void pasDeHtmlSansCapture(@TempDir Path tmp) throws IOException {
		FailureArtefacts content = new FailureArtefacts("e", "f", List.of());

		Path dir = TestFailureManager.writeArtefacts(tmp, "INTEG", "t", WHEN, content);

		try (Stream<Path> files = Files.list(dir)) {
			assertEquals(2, files.count());
		}
	}
}
