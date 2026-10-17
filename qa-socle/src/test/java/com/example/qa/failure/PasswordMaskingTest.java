package com.example.qa.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Masquage best-effort de la {@code value} des champs {@code <input type="password">} dans le DOM brut
 * ({@link TestFailureManager#maskPasswordInputs(String)}) — le seul cas réaliste de fuite d'un secret
 * dans le markup capté à l'échec. Tests purs, sans Serenity ni navigateur.
 */
class PasswordMaskingTest {

	@Test
	@DisplayName("Masque la value d'un input password (guillemets doubles conservés)")
	void masqueValuePassword() {
		String masked = TestFailureManager.maskPasswordInputs(
				"<input type=\"password\" value=\"hunter2\">");
		assertEquals("<input type=\"password\" value=\"*******\">", masked);
	}

	@Test
	@DisplayName("Aucun clair du secret ne subsiste dans le markup masqué")
	void aucunClairNeSubsiste() {
		String masked = TestFailureManager.maskPasswordInputs(
				"<form><input name='pwd' type='password' value='S3cr3t!'/></form>");
		assertFalse(masked.contains("S3cr3t!"));
		assertEquals("<form><input name='pwd' type='password' value='*******'/></form>", masked);
	}

	@Test
	@DisplayName("Insensible à la casse et à l'ordre des attributs (value avant type)")
	void casseEtOrdreIndifferents() {
		String masked = TestFailureManager.maskPasswordInputs(
				"<INPUT VALUE=\"abcd\" TYPE=PASSWORD>");
		assertEquals("<INPUT VALUE=\"****\" TYPE=PASSWORD>", masked);
	}

	@Test
	@DisplayName("Les champs non-password sont laissés intacts")
	void champsNonPasswordIntacts() {
		String html = "<input type=\"text\" value=\"jean\"><input type=\"email\" value=\"a@b.c\">";
		assertEquals(html, TestFailureManager.maskPasswordInputs(html));
	}

	@Test
	@DisplayName("value vide → rien à masquer (pas d'étoile parasite)")
	void valueVide() {
		assertEquals("<input type=\"password\" value=\"\">",
				TestFailureManager.maskPasswordInputs("<input type=\"password\" value=\"\">"));
	}

	@Test
	@DisplayName("Input password sans attribut value → inchangé")
	void passwordSansValue() {
		assertEquals("<input type=\"password\" name=\"pwd\">",
				TestFailureManager.maskPasswordInputs("<input type=\"password\" name=\"pwd\">"));
	}

	@Test
	@DisplayName("Plusieurs champs password masqués, texte autour préservé")
	void plusieursChamps() {
		String masked = TestFailureManager.maskPasswordInputs(
				"<p>hi</p><input type='password' value='aa'><input type='password' value='bbbb'>");
		assertEquals("<p>hi</p><input type='password' value='**'><input type='password' value='****'>",
				masked);
	}

	@Test
	@DisplayName("null et vide renvoyés tels quels")
	void nullEtVide() {
		assertNull(TestFailureManager.maskPasswordInputs(null));
		assertEquals("", TestFailureManager.maskPasswordInputs(""));
	}
}
