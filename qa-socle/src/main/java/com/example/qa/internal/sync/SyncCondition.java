package com.example.qa.internal.sync;

import java.util.Optional;
import java.util.function.Function;

import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.InvalidElementStateException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.interactions.MoveTargetOutOfBoundsException;

/**
 * Conditions de blocage de la synchronisation : chaque valeur est le <b>nom stable du socle</b> pour
 * une exception Selenium (mapping <b>1:1</b>) qui empêche une interaction — qu'elle ait été
 * <b>réellement levée</b> pendant la boucle de polling ou seulement <b>latente</b> (elle aurait été
 * levée si l'action avait été tentée). Le {@code code} est le tag versionné consommé par
 * {@code qa-maintenance} (D9) ; le {@code label} est le libellé lisible du diagnostic.
 *
 * <p><b>Deux étages</b> (cf. hiérarchie d'interaction) :
 * <ul>
 *   <li><b>résolution</b> — {@link #ABSENT}, {@link #STALE} : l'élément n'existe pas, l'action ne se
 *       pose pas (court-circuit) ;</li>
 *   <li><b>action</b> — {@link #HIDDEN}, {@link #DISABLED}, {@link #OUT_OF_BOUNDS}, {@link #INTERCEPTED} :
 *       l'élément est là mais pas exploitable. L'ordre de déclaration suit le <b>flux d'interaction</b>
 *       (préconditions croissantes) : {@link #INTERCEPTED} est le plus fin (il présuppose présent,
 *       visible, activé, dans la zone).</li>
 * </ul>
 *
 * <p><b>Emboîtement d'héritage</b> : {@code ElementClickInterceptedException ⊂
 * ElementNotInteractableException ⊂ InvalidElementStateException}. {@link #classify(Throwable)} en tient
 * compte en retenant toujours la condition la <b>plus spécifique</b>, indépendamment de l'ordre de
 * déclaration — jamais le type parent qui masquerait le fils.
 */
public enum SyncCondition {

	/** Aucun élément ne correspond au locator (étage résolution). */
	ABSENT("ABSENT", "élément introuvable", NoSuchElementException.class, NoSuchElementException::new),

	/** Référence périmée : l'élément a été détaché / re-rendu (étage résolution). */
	STALE("STALE", "référence périmée (élément re-rendu)", StaleElementReferenceException.class,
			StaleElementReferenceException::new),

	/** Présent mais pas dans un état interactable — typiquement non visible (étage action). */
	HIDDEN("HIDDEN", "présent mais non visible", ElementNotInteractableException.class,
			ElementNotInteractableException::new),

	/** Présent, visible, mais désactivé / non éditable — préconditions ou jeu de données (étage action). */
	DISABLED("DISABLED", "présent mais désactivé", InvalidElementStateException.class,
			InvalidElementStateException::new),

	/** Présent, visible, activé, mais hors de la zone atteignable (étage action). */
	OUT_OF_BOUNDS("OUT_OF_BOUNDS", "hors de la zone atteignable", MoveTargetOutOfBoundsException.class,
			MoveTargetOutOfBoundsException::new),

	/** Présent, visible, activé, dans la zone, mais un autre élément intercepte le clic (étage action). */
	INTERCEPTED("INTERCEPTED", "clic intercepté par un autre élément", ElementClickInterceptedException.class,
			ElementClickInterceptedException::new);

	private final String code;
	private final String label;
	private final Class<? extends WebDriverException> exceptionType;
	private final Function<String, ? extends WebDriverException> factory;

	SyncCondition(String code, String label, Class<? extends WebDriverException> exceptionType,
			Function<String, ? extends WebDriverException> factory) {
		this.code = code;
		this.label = label;
		this.exceptionType = exceptionType;
		this.factory = factory;
	}

	/** Tag versionné (contrat {@code qa-maintenance}). */
	public String code() {
		return code;
	}

	/** Libellé lisible du diagnostic. */
	public String label() {
		return label;
	}

	/**
	 * Classe une exception réellement levée vers la condition la <b>plus spécifique</b> qui l'englobe
	 * (ex. un {@code ElementClickInterceptedException} → {@link #INTERCEPTED}, jamais {@link #HIDDEN} ni
	 * {@link #DISABLED} qui sont ses parents). {@link Optional#empty()} si l'exception n'est pas une
	 * condition de synchro connue (→ non transitoire, à remonter telle quelle).
	 */
	public static Optional<SyncCondition> classify(Throwable throwable) {
		SyncCondition best = null;
		for (SyncCondition candidate : values()) {
			if (candidate.exceptionType.isInstance(throwable)
					&& (best == null || best.exceptionType.isAssignableFrom(candidate.exceptionType))) {
				best = candidate; // candidate est au moins aussi spécifique que best
			}
		}
		return Optional.ofNullable(best);
	}

	/**
	 * Reconstruit l'exception Selenium correspondant à cette condition (pour l'attacher en
	 * {@code suppressed} au diagnostic d'un timeout — les conditions latentes n'ont pas été réellement
	 * levées, on les matérialise pour le débogage).
	 */
	public WebDriverException toException(String message) {
		return factory.apply(message);
	}
}
