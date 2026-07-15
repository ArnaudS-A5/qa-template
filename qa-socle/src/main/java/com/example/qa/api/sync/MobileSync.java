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
 * <p><b>Échappatoire « bris de glace »</b> (D15) : cette façade est volontairement <b>sous-classable</b>
 * et ses méthodes <b>non-{@code final}</b>. En cas de bug bloquant de synchro côté projet, créer une
 * <b>sous-classe nommée</b> qui surcharge la/les méthode(s) fautive(s) et l'instancier via un
 * <b>point d'obtention unique</b> du projet (un seul endroit à basculer puis à restaurer ; PageObjects
 * et tests intouchés, le type déclaré restant {@code MobileSync}). Le correctif stable est ensuite
 * <b>promu dans le socle</b> (changement de corps = non-breaking). Sous-classe <b>nommée</b> conseillée
 * (plus facile à analyser/refactorer qu'une classe anonyme).
 *
 * <p>Squelette — signatures arrêtées (étape 6). Implémentation des gestes mobiles <b>différée hors
 * périmètre étape 8</b> (pas de pilote mobile à ce stade) : chaque geste lève pour l'instant
 * {@link UnsupportedOperationException} (échec franc si appelé avant implémentation), et sera câblé au
 * démarrage d'un pilote mobile.
 */
public class MobileSync extends AbstractSyncManager {

	/** Message des gestes mobiles non encore implémentés (différés — cf. javadoc de classe). */
	private static final String DIFFERE = "Geste mobile non implémenté (différé — hors périmètre étape 8)";

	/** Façade mobile au profil de timeout par défaut ({@link SyncTimeout#MEDIUM}). */
	public MobileSync() {
		super(SyncTimeout.MEDIUM);
	}

	/** Façade mobile au profil de timeout donné (zone plus ou moins lente à se charger). */
	public MobileSync(SyncTimeout timeout) {
		super(timeout);
	}

	/** Vue bornée {@code within} (usage interne, cf. {@link #within(By)}). */
	protected MobileSync(AbstractSyncManager parent, By container) {
		super(parent, container);
	}

	@Override
	public MobileSync within(By container) {
		return new MobileSync(this, container);
	}

	// ============================================================================================
	// Gestes tactiles (W3C Actions / mobile: commands)
	// ============================================================================================

	/** Tape une fois sur l'élément cible. */
	public void tap(By locator) {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Tape une fois aux coordonnées écran données. */
	public void tap(int x, int y) {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Double-tape sur l'élément cible. */
	public void doubleTap(By locator) {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Appui long sur l'élément cible pendant la durée donnée. */
	public void longPress(By locator, Duration duration) {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Glisse d'un point à un autre (coordonnées écran). */
	public void swipe(int startX, int startY, int endX, int endY, Duration duration) {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Balaye l'élément cible dans une direction (haut/bas/gauche/droite). */
	public void swipe(By locator, SwipeDirection direction) {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Fait défiler jusqu'à rendre l'élément cible visible. */
	public void scrollTo(By locator) {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Fait défiler le conteneur cible dans une direction. */
	public void scroll(By container, SwipeDirection direction) {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Pince (zoom arrière) sur l'élément cible. */
	public void pinch(By locator) {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Écarte (zoom avant) sur l'élément cible. */
	public void zoom(By locator) {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Glisser-déposer d'un élément source vers un élément cible. */
	public void dragAndDrop(By source, By target) {
		throw new UnsupportedOperationException(DIFFERE);
	}

	// ============================================================================================
	// Clavier / matériel
	// ============================================================================================

	/** Masque le clavier virtuel s'il est affiché. */
	public void hideKeyboard() {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Le clavier virtuel est-il actuellement affiché. */
	public boolean isKeyboardShown() {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Presse une touche matérielle/système (back, home, volume...) identifiée par {@code keyName}. */
	public void pressKey(String keyName) {
		throw new UnsupportedOperationException(DIFFERE);
	}

	// ============================================================================================
	// Orientation / contexte (natif ⇄ webview)
	// ============================================================================================

	/** Passe l'appareil en mode portrait. */
	public void rotatePortrait() {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Passe l'appareil en mode paysage. */
	public void rotateLandscape() {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Orientation courante de l'appareil (ex. {@code "PORTRAIT"} / {@code "LANDSCAPE"}). */
	public String getOrientation() {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Bascule vers le contexte natif de l'application. */
	public void switchToNativeContext() {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Bascule vers le premier contexte WebView disponible. */
	public void switchToWebViewContext() {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Nom du contexte courant (ex. {@code "NATIVE_APP"} / {@code "WEBVIEW_..."}). */
	public String getCurrentContext() {
		throw new UnsupportedOperationException(DIFFERE);
	}

	// ============================================================================================
	// Cycle de vie application / fichiers
	// ============================================================================================

	/** Met l'application au premier plan (lancement / réveil). */
	public void launchApp() {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Met l'application en arrière-plan pour la durée donnée. */
	public void runAppInBackground(Duration duration) {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Réinitialise l'application sous test. */
	public void resetApp() {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Envoie un fichier sur l'appareil au chemin distant donné. */
	public void pushFile(String remotePath, byte[] data) {
		throw new UnsupportedOperationException(DIFFERE);
	}

	/** Récupère un fichier de l'appareil au chemin distant donné. */
	public byte[] pullFile(String remotePath) {
		throw new UnsupportedOperationException(DIFFERE);
	}
}
