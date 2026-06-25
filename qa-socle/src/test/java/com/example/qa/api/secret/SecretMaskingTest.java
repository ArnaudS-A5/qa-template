package com.example.qa.api.secret;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests comportementaux du masquage de {@link Secret} (étape 7, format acté D12 amendé 2026-06-25).
 *
 * <p>Format : <b>2 premiers caractères en clair + une étoile par caractère masqué (longueur − 2) + 16
 * hexa SHA-256</b>. La longueur réelle du secret est volontairement révélée (compromis assumé : test +
 * CyberArk en rotation). Vérifie : (a) la valeur en clair n'est accessible que via {@code value()} ;
 * (b) {@code masked()} / {@code toString()} ne révèlent jamais la partie cachée ; (c) le nombre d'étoiles
 * suit la longueur ; (d) le préfixe SHA-256 compare deux secrets sans les révéler (vecteur connu).
 */
class SecretMaskingTest {

	/** Forme attendue : 0 à 2 caractères visibles, un nombre quelconque d'étoiles, « (sha256:<16 hexa>) ». */
	private static final Pattern MASKED_FORMAT =
			Pattern.compile("^.{0,2}\\** \\(sha256:[0-9a-f]{16}\\)$");

	@Test
	@DisplayName("value() renvoie le clair ; toString()/masked() jamais")
	void valueRenvoieLeClairPasToString() {
		Secret secret = Secret.of("password");

		assertEquals("password", secret.value());
		assertFalse(secret.toString().contains("password"), "toString ne doit pas contenir le clair");
		assertFalse(secret.masked().contains("password"), "masked ne doit pas contenir le clair");
	}

	@Test
	@DisplayName("masked() respecte le format D12 amendé (vecteur connu : « password », 8 car. → 6 étoiles)")
	void maskedRespecteLeFormatActe() {
		// SHA-256("password") = 5e884898da28047151... → 16 premiers hexa = 5e884898da280471
		Secret secret = Secret.of("password");

		assertEquals("pa****** (sha256:5e884898da280471)", secret.masked());
		assertEquals(secret.masked(), secret.toString());
		assertTrue(MASKED_FORMAT.matcher(secret.masked()).matches());
	}

	@Test
	@DisplayName("Le nombre d'étoiles = longueur − 2 (la longueur réelle est révélée)")
	void nombreDEtoilesSuitLaLongueur() {
		assertEquals(6, compterEtoiles(Secret.of("password").masked()));   // 8 - 2
		assertEquals(9, compterEtoiles(Secret.of("supersecret").masked())); // 11 - 2
		assertEquals(0, compterEtoiles(Secret.of("ab").masked()));          // 2 - 2
	}

	@Test
	@DisplayName("masked() ne révèle jamais la partie cachée du secret")
	void maskedNeRevelePasLaPartieCachee() {
		Secret secret = Secret.of("supersecret"); // 11 caractères → su + 9 étoiles

		assertEquals("su********* (sha256:" + secret.sha256Prefix() + ")", secret.masked());
		assertFalse(secret.masked().contains("persecret"));
	}

	@Test
	@DisplayName("sha256Prefix() = 16 hexa minuscules, déterministe")
	void sha256PrefixEstStable() {
		assertEquals("5e884898da280471", Secret.of("password").sha256Prefix());
		assertTrue(Pattern.matches("[0-9a-f]{16}", Secret.of("x").sha256Prefix()));
	}

	@Test
	@DisplayName("Deux secrets égaux ont le même préfixe ; deux différents, des préfixes différents")
	void prefixePermetDeComparerSansReveler() {
		assertEquals(Secret.of("abc").sha256Prefix(), Secret.of("abc").sha256Prefix());
		assertNotEquals(Secret.of("abc").sha256Prefix(), Secret.of("abd").sha256Prefix());
	}

	@Test
	@DisplayName("Secret de 2 caractères ou moins : aucune étoile, seuls les caractères présents")
	void secretTresCourt() {
		assertFalse(Secret.of("ab").masked().contains("*"));          // 0 étoile
		assertTrue(Secret.of("ab").masked().startsWith("ab (sha256:"));
		assertEquals("A (sha256:" + Secret.of("A").sha256Prefix() + ")", Secret.of("A").masked());
		assertTrue(MASKED_FORMAT.matcher(Secret.of("").masked()).matches());
	}

	@Test
	@DisplayName("Secret.of(null) échoue immédiatement")
	void ofNullEchoue() {
		assertThrows(NullPointerException.class, () -> Secret.of(null));
	}

	private static long compterEtoiles(String masked) {
		return masked.chars().filter(c -> c == '*').count();
	}
}
