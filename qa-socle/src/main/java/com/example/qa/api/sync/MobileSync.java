package com.example.qa.api.sync;

import java.time.Duration;
import org.openqa.selenium.By;
import com.example.qa.internal.sync.AbstractSyncManager;

/**
 * Façade de synchronisation et d'interaction <b>mobile</b> (Appium).
 *
 * <p>Hérite de {@link AbstractSyncManager} <b>tout</b> le socle commun (moteur {@code fluentWait} +
 * flag JS, et l'intégralité des actions/lectures/états/attentes par {@link By}). C'est possible sans
 * duplication car {@code AppiumDriver} est un {@code WebDriver} et {@code AppiumBy} (accessibilityId,
 * iOSClassChain, androidUIAutomator...) est un {@code By} : la résolution d'élément via
 * {@code WebElementFacade} fonctionne donc à l'identique. {@code MobileSync} n'ajoute que les
 * <b>gestes spécifiques mobile</b> que ni Selenium ni Serenity ne couvrent.
 *
 * <p><b>Driver</b> (D19) : les gestes s'appuient sur le {@code WebDriver} Serenity du thread courant,
 * casté en interne en {@code AppiumDriver} ({@code AndroidDriver}/{@code IOSDriver}) — <b>aucun type
 * Appium n'apparaît dans ce contrat public</b> (mêmes principes de neutralité que D12/D15). Les gestes
 * passent par les Actions W3C / les commandes {@code mobile:} du java-client (présent au classpath,
 * transitif de {@code serenity-core}).
 *
 * <p><b>No-leak (D3)</b> conservé : tout passe par {@code By}. La recherche imbriquée {@link #within(By)}
 * renvoie un {@code MobileSync} (retour covariant).
 *
 * <p>Squelette — signatures arrêtées (étape 6). Implémentation des gestes mobiles <b>différée hors
 * périmètre étape 8</b> (pas de pilote mobile à ce stade) ; reportée au démarrage d'un pilote mobile.
 */
public class MobileSync extends AbstractSyncManager {

	@Override
	public MobileSync within(By container) {
		return null;
	}

	// ============================================================================================
	// Gestes tactiles (W3C Actions / mobile: commands)
	// ============================================================================================

	/** Tape une fois sur l'élément cible. */
	public void tap(By locator) {
	}

	/** Tape une fois aux coordonnées écran données. */
	public void tap(int x, int y) {
	}

	/** Double-tape sur l'élément cible. */
	public void doubleTap(By locator) {
	}

	/** Appui long sur l'élément cible pendant la durée donnée. */
	public void longPress(By locator, Duration duration) {
	}

	/** Glisse d'un point à un autre (coordonnées écran). */
	public void swipe(int startX, int startY, int endX, int endY, Duration duration) {
	}

	/** Balaye l'élément cible dans une direction (haut/bas/gauche/droite). */
	public void swipe(By locator, SwipeDirection direction) {
	}

	/** Fait défiler jusqu'à rendre l'élément cible visible. */
	public void scrollTo(By locator) {
	}

	/** Fait défiler le conteneur cible dans une direction. */
	public void scroll(By container, SwipeDirection direction) {
	}

	/** Pince (zoom arrière) sur l'élément cible. */
	public void pinch(By locator) {
	}

	/** Écarte (zoom avant) sur l'élément cible. */
	public void zoom(By locator) {
	}

	/** Glisser-déposer d'un élément source vers un élément cible. */
	public void dragAndDrop(By source, By target) {
	}

	// ============================================================================================
	// Clavier / matériel
	// ============================================================================================

	/** Masque le clavier virtuel s'il est affiché. */
	public void hideKeyboard() {
	}

	/** Le clavier virtuel est-il actuellement affiché. */
	public boolean isKeyboardShown() {
		return false;
	}

	/** Presse une touche matérielle/système (back, home, volume...) identifiée par {@code keyName}. */
	public void pressKey(String keyName) {
	}

	// ============================================================================================
	// Orientation / contexte (natif ⇄ webview)
	// ============================================================================================

	/** Passe l'appareil en mode portrait. */
	public void rotatePortrait() {
	}

	/** Passe l'appareil en mode paysage. */
	public void rotateLandscape() {
	}

	/** Orientation courante de l'appareil (ex. {@code "PORTRAIT"} / {@code "LANDSCAPE"}). */
	public String getOrientation() {
		return null;
	}

	/** Bascule vers le contexte natif de l'application. */
	public void switchToNativeContext() {
	}

	/** Bascule vers le premier contexte WebView disponible. */
	public void switchToWebViewContext() {
	}

	/** Nom du contexte courant (ex. {@code "NATIVE_APP"} / {@code "WEBVIEW_..."}). */
	public String getCurrentContext() {
		return null;
	}

	// ============================================================================================
	// Cycle de vie application / fichiers
	// ============================================================================================

	/** Met l'application au premier plan (lancement / réveil). */
	public void launchApp() {
	}

	/** Met l'application en arrière-plan pour la durée donnée. */
	public void runAppInBackground(Duration duration) {
	}

	/** Réinitialise l'application sous test. */
	public void resetApp() {
	}

	/** Envoie un fichier sur l'appareil au chemin distant donné. */
	public void pushFile(String remotePath, byte[] data) {
	}

	/** Récupère un fichier de l'appareil au chemin distant donné. */
	public byte[] pullFile(String remotePath) {
		return null;
	}
}
