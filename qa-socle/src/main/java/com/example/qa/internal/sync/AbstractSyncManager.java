package com.example.qa.internal.sync;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.serenitybdd.core.pages.WebElementFacade;
import net.serenitybdd.core.pages.WebElementFacadeImpl;
import net.serenitybdd.model.environment.EnvironmentSpecificConfiguration;
import net.thucydides.core.steps.StepEventBus;
import net.thucydides.core.webdriver.ConfigurableTimeouts;
import net.thucydides.core.webdriver.ThucydidesWebDriverSupport;
import net.thucydides.model.environment.SystemEnvironmentVariables;
import net.thucydides.model.util.EnvironmentVariables;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.InvalidElementStateException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Point;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import com.example.qa.api.exception.SyncException;
import com.example.qa.api.secret.Secret;
import com.example.qa.api.sync.SyncTimeout;
import com.example.qa.internal.assertion.SoftAssertions;

/**
 * Moteur de synchronisation <b>et</b> socle d'interactions commun aux deux façades
 * {@code WebSync} (web) et {@code MobileSync} (mobile).
 *
 * <p><b>Câblage</b> (D3/D4) : web et mobile ne diffèrent que par le <b>driver</b> et par les
 * <b>gestes spécifiques</b> (propres au mobile). Tout le reste — le moteur d'attente
 * {@link #fluentWait} (fluentWait + flag JS) <b>et</b> les actions/lectures/états par {@link By}
 * (qui s'appliquent identiquement, {@code AppiumDriver} étant un {@code WebDriver} et
 * {@code AppiumBy} un {@code By}) — est écrit <b>une seule fois ici</b> et hérité par les deux
 * sous-classes. {@code WebSync} n'ajoute que le spécifique web (formulaires, CSS, {@code <select>}
 * HTML, fenêtre) ; {@code MobileSync} n'ajoute que les <b>gestes</b> (tap multi-touch, swipe, scroll,
 * pinch, clavier, orientation, contextes natif/webview).
 *
 * <p><b>Stratégie d'implémentation</b> (cf. {@code WebSync}) : les actions délèguent à l'API publique
 * Serenity {@code WebElementFacade} (résolue par {@code By}), orchestrée par {@link #fluentWait}.
 * <b>No-leak (D3)</b> : aucune méthode ne rend de {@code WebElement} vivant ; la recherche imbriquée
 * passe par {@link #within(By)}, dont le type de retour est <b>covariant</b> dans chaque sous-classe.
 *
 * <p>Squelette — signatures arrêtées (étape 6), corps réel à fournir (étape 8).
 */
public abstract class AbstractSyncManager {

	/** Profil de timeout de cette instance (état propre ; le driver reste celui du thread). */
	private final SyncTimeout timeout;

	/**
	 * Contexte de recherche imbriquée ({@code within}) : {@code null} à la racine. Une vue résout ses
	 * {@code By} comme <b>descendants</b> de {@link #containerLocator}, lui-même résolu dans le contexte
	 * du {@link #parent} (récursif pour {@code within().within()}). Immuable → sûr en parallèle (D19).
	 */
	private final AbstractSyncManager parent;
	private final By containerLocator;

	/**
	 * <b>Driver</b> sur lequel le plafond de chargement de page a déjà été posé (thread-confiné,
	 * <b>non {@code static}</b>) — et non un simple booléen « déjà fait ».
	 *
	 * <p>La distinction est un correctif : le réglage porte sur le <b>driver</b>, pas sur l'instance de
	 * synchro. Avec un booléen, une instance conservée pendant que Serenity <b>recrée</b> le driver
	 * (redémarrage de navigateur en cours de session) restait marquée « déjà posé » et le nouveau driver
	 * tournait <b>sans plafond</b>. En mémorisant l'identité du driver, tout changement le rétablit.
	 * Cf. {@link #driver()}.
	 */
	private WebDriver pageLoadTimeoutAppliedTo;

	/** Construit un moteur racine adossé au profil de timeout donné (jamais {@code null}). */
	protected AbstractSyncManager(SyncTimeout timeout) {
		this.timeout = timeout;
		this.parent = null;
		this.containerLocator = null;
	}

	/** Construit une <b>vue bornée</b> {@code within} : hérite le profil du parent, borne à {@code container}. */
	protected AbstractSyncManager(AbstractSyncManager parent, By container) {
		this.timeout = parent.timeout;
		this.parent = parent;
		this.containerLocator = container;
	}

	// ============================================================================================
	// Moteur d'attente commun (D3) — protégé, utilisé par les actions ; jamais réimplémenté
	// ============================================================================================

	/**
	 * Attente robuste commune (D3) : ré-résout {@code locator} et applique {@code action} en boucle
	 * jusqu'à succès ou timeout (celui du <b>profil de l'instance</b>), en absorbant les exceptions
	 * transitoires de re-render. Au timeout, lève une {@link SyncException} avec le diagnostic actif.
	 * La résolution est <b>paresseuse</b> (no-leak, D3) : aucune méthode ne conserve de handle vivant.
	 *
	 * @param locator cible à ré-résoudre à chaque tour
	 * @param action  opération à appliquer sur l'élément résolu (renvoie la valeur attendue)
	 * @param <T>     type du résultat produit par l'action
	 * @return le résultat de l'action une fois l'élément exploitable
	 * @throws SyncException si l'action ne réussit pas avant le timeout du profil
	 */
	protected <T> T fluentWait(By locator, Function<WebElementFacade, T> action) {
		return withoutImplicitWait(() -> poll(locator, resolveTimeout(), resolvePolling(),
				() -> action.apply(resolve(locator)),
				() -> diagnose(locator)));
	}

	// ============================================================================================
	// Moteur de polling « pur » (sans navigateur) — cœur réutilisé par fluentWait (câblage étape 8)
	// ============================================================================================

	/** Intervalle de polling par défaut du moteur (D3). Surchargé par {@code qa.sync.pollingInterval}. */
	static final Duration POLLING_INTERVAL_DEFAULT = Duration.ofMillis(250);

	/**
	 * Boucle de synchronisation « pure » : répète {@code action} jusqu'à succès ou {@code timeout}, en
	 * <b>absorbant</b> les seules exceptions transitoires de synchro (celles reconnues par
	 * {@link SyncCondition#classify}). Toute autre exception (bug, sélecteur invalide…) est <b>remontée
	 * telle quelle</b> immédiatement. Au timeout, lève une {@link SyncException} construite par
	 * {@link #buildSyncException} : {@code cause} = <b>dernière</b> exception transitoire réellement
	 * ignorée, message + {@code suppressed} = <b>diagnostic actif</b> ({@code diagnosis}, évalué une
	 * seule fois à cet instant).
	 *
	 * <p>Testable sans navigateur : {@code action} et {@code diagnosis} sont fournis par l'appelant
	 * ({@code fluentWait} en production, des lambdas simulées en test).
	 */
	static <T> T poll(By locator, Duration timeout, Duration pollingInterval,
			Supplier<T> action, Supplier<Set<SyncCondition>> diagnosis) {
		long deadlineNanos = System.nanoTime() + timeout.toNanos();
		Throwable lastIgnored = null;
		while (true) {
			try {
				return action.get();
			} catch (RuntimeException failure) {
				if (SyncCondition.classify(failure).isEmpty()) {
					throw failure; // non transitoire : ce n'est pas un problème de synchro
				}
				lastIgnored = failure; // on écrase à chaque tour → la DERNIÈRE ignorée survivra
			}
			if (System.nanoTime() >= deadlineNanos) {
				throw buildSyncException(locator, timeout, lastIgnored, diagnosis.get());
			}
			sleepQuietly(pollingInterval);
		}
	}

	/**
	 * Construit la {@link SyncException} d'un timeout : message avec les <b>codes stables</b> des
	 * conditions latentes (contrat {@code qa-maintenance}, D9) + libellés lisibles, {@code cause} =
	 * dernière exception réellement ignorée, et chaque condition latente <b>matérialisée en
	 * {@code suppressed}</b> (objets Selenium typés, pour le débogage). Diagnostic vide → message
	 * générique « instable ».
	 */
	static SyncException buildSyncException(By locator, Duration timeout, Throwable cause,
			Set<SyncCondition> latentConditions) {
		StringBuilder message = new StringBuilder("Synchronisation impossible sur ")
				.append(locator).append(" après ").append(humanize(timeout));
		if (latentConditions.isEmpty()) {
			message.append(" — cause indéterminée (application instable ?)");
		} else {
			latentConditions.forEach(condition -> message.append(" [").append(condition.code()).append(']'));
			message.append(" : ").append(latentConditions.stream()
					.map(SyncCondition::label).collect(Collectors.joining(" ; ")));
		}
		SyncException exception = new SyncException(message.toString(), cause);
		latentConditions.forEach(condition ->
				exception.addSuppressed(condition.toException(condition.label() + " sur " + locator)));
		return exception;
	}

	private static String humanize(Duration timeout) {
		long millis = timeout.toMillis();
		return millis >= 1000 ? (millis / 1000) + " s" : millis + " ms";
	}

	private static void sleepQuietly(Duration pollingInterval) {
		try {
			Thread.sleep(Math.max(0L, pollingInterval.toMillis()));
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new SyncException("Attente de synchronisation interrompue", interrupted);
		}
	}

	// ============================================================================================
	// Câblage navigateur (lot 2a) : timeout du profil, résolution paresseuse, diagnostic actif
	// ============================================================================================

	/** Clé du pas de polling ; défaut {@link #POLLING_INTERVAL_DEFAULT}. */
	static final String POLLING_INTERVAL_KEY = "qa.sync.pollingInterval";

	/** Timeout effectif = clé {@code qa.sync.timeout.<profil>} si définie, sinon le défaut du profil. */
	Duration resolveTimeout() {
		return config(timeout.key()).map(AbstractSyncManager::parseDuration).orElse(timeout.defaultValue());
	}

	static Duration resolvePolling() {
		return config(POLLING_INTERVAL_KEY).map(AbstractSyncManager::parseDuration).orElse(POLLING_INTERVAL_DEFAULT);
	}

	/** Interprète une durée de config : {@code "15s"}, {@code "500ms"}, ou un nombre nu (secondes). */
	static Duration parseDuration(String raw) {
		String value = raw.trim();
		if (value.endsWith("ms")) {
			return Duration.ofMillis(Long.parseLong(value.substring(0, value.length() - 2).trim()));
		}
		if (value.endsWith("s")) {
			return Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1).trim()));
		}
		return Duration.ofSeconds(Long.parseLong(value));
	}

	private static Optional<String> config(String key) {
		EnvironmentVariables env = SystemEnvironmentVariables.createEnvironmentVariables();
		return EnvironmentSpecificConfiguration.from(env).getOptionalProperty(key);
	}

	// ============================================================================================
	// Plafond de chargement de page — appliqué AUTOMATIQUEMENT au 1ᵉʳ contact driver (lot 2c)
	// ============================================================================================

	/** Clé du plafond de chargement de page ; défaut {@link #PAGE_LOAD_TIMEOUT_DEFAULT}. */
	static final String PAGE_LOAD_TIMEOUT_KEY = "qa.sync.pageLoadTimeout";

	/** Plafond de chargement de page par défaut (2 min) : évite qu'une page qui « pend » bloque sans fin. */
	static final Duration PAGE_LOAD_TIMEOUT_DEFAULT = Duration.ofMinutes(2);

	/** Plafond effectif = clé {@code qa.sync.pageLoadTimeout} si définie, sinon 2 min. */
	Duration resolvePageLoadTimeout() {
		return config(PAGE_LOAD_TIMEOUT_KEY).map(AbstractSyncManager::parseDuration).orElse(PAGE_LOAD_TIMEOUT_DEFAULT);
	}

	/**
	 * Driver Serenity du thread courant. Pose le plafond {@link #resolvePageLoadTimeout()} <b>une fois par
	 * driver</b>, au 1ᵉʳ contact du socle avec celui-ci — <b>automatique</b>, sans aucun geste consommateur
	 * ni type public ajouté. Le marqueur retient <b>l'identité du driver</b>
	 * ({@link #pageLoadTimeoutAppliedTo}, non {@code static} → thread-safe) et non un simple « déjà fait » :
	 * si Serenity <b>recrée</b> le driver en cours de session, le plafond est <b>reposé</b> sur le nouveau.
	 * Le réglage Selenium étant <b>dynamique</b> (il vaut pour les navigations suivantes), l'appliquer au
	 * 1ᵉʳ accès suffit. Best-effort : un driver/contexte qui refuse le réglage ne fait pas échouer le test.
	 *
	 * <p><b>Portée assumée</b> : une navigation effectuée <b>avant</b> tout appel au socle (typiquement le
	 * {@code openUrl} d'ouverture d'un test) n'est pas couverte — elle retombe sur le défaut W3C du
	 * navigateur (300 s). Protéger ce cas supposerait soit de prendre la main sur la fabrique de driver
	 * Serenity, soit d'imposer une navigation par le socle (mécanisme opt-in que D16 écarte). Écart
	 * documenté, cf. {@code points-attention.md} (PA-8).
	 */
	private WebDriver driver() {
		WebDriver driver = ThucydidesWebDriverSupport.getDriver();
		if (driver != pageLoadTimeoutAppliedTo) {
			pageLoadTimeoutAppliedTo = driver; // posé d'abord : un refus ne doit pas re-tenter à chaque résolution
			try {
				driver.manage().timeouts().pageLoadTimeout(resolvePageLoadTimeout());
			} catch (RuntimeException refused) {
				// certains drivers/contextes refusent ce réglage → best-effort, on n'échoue pas pour ça
			}
		}
		return driver;
	}

	/**
	 * Exécute {@code operation} avec l'<b>implicit wait neutralisé à 0</b>, puis le <b>restaure</b>
	 * (try/finally). Objectif (C8) : garder <b>deux attentes distinctes</b> — le polling du socle
	 * ({@link #fluentWait}, {@link #actRaw}, {@link #query}, {@link #tryAssert}) reste <b>fin</b> (250 ms)
	 * et ne s'additionne <b>jamais</b> à l'implicit wait (3 s), lequel n'est là que comme <b>filet pour les
	 * appels Selenium bruts</b> du consommateur (hors {@code WebSync}).
	 *
	 * <p><b>La neutralisation passe par {@link ConfigurableTimeouts}</b> (API Serenity), et <b>non</b> par
	 * {@code driver.manage().timeouts().implicitlyWait(...)}. Raison vérifiée au navigateur : le
	 * {@code WebDriverFacade} de Serenity maintient son <b>propre</b> implicit timeout, au-dessus du driver
	 * natif — c'est celui-là que consulte la résolution d'élément. Régler le driver natif à 0 laissait donc
	 * la couche Serenity attendre ses 3 s : chaque prédicat coûtait ~3 s (T14 mesuré à 12 s pour quatre
	 * prédicats) et une action pouvait « absorber » l'attente au point de faire réussir un cas qui devait
	 * expirer (L4, L5). Le driver est confiné au thread → sûr en parallèle. Best-effort : driver absent,
	 * non-Serenity ou timeouts inaccessibles → l'opération s'exécute sans toggle.
	 */
	private <T> T withoutImplicitWait(Supplier<T> operation) {
		ConfigurableTimeouts timeouts;
		Duration previous;
		try {
			WebDriver driver = driver();
			if (!(driver instanceof ConfigurableTimeouts configurable)) {
				return operation.get(); // driver hors Serenity → rien à neutraliser à ce niveau
			}
			timeouts = configurable;
			previous = timeouts.getCurrentImplicitTimeout();
			timeouts.setImplicitTimeout(Duration.ZERO);
		} catch (RuntimeException noToggle) {
			return operation.get(); // pas de driver / timeouts inaccessibles → exécution sans toggle
		}
		try {
			return operation.get();
		} finally {
			try {
				timeouts.setImplicitTimeout(previous);
			} catch (RuntimeException ignored) {
				// restauration best-effort : ne pas masquer l'éventuelle exception de l'opération
			}
		}
	}

	/**
	 * Résout {@code locator} en {@link WebElementFacade} <b>paresseux</b> (le facade ré-résout le
	 * {@code By} à chaque usage → no-leak, D3). Timeouts délégués à {@code 0} : le {@code fluentWait}
	 * maison est la <b>seule horloge</b> (C8). Dans une vue {@code within}, résout d'abord le container
	 * (récursif) <b>puis</b> le descendant — d'où l'ordre naturel « container avant child » au débogage.
	 */
	private WebElementFacade resolve(By locator) {
		if (parent == null) {
			return WebElementFacadeImpl.wrapWebElement(driver(), locator, NO_FACADE_CLOCK, NO_FACADE_CLOCK, locator.toString());
		}
		return parent.resolve(containerLocator).find(locator);
	}

	/**
	 * Timeout confié au {@code WebElementFacade} : <b>1 ms</b>, et surtout <b>pas {@code 0}</b> (C8).
	 * Vérifié au bytecode Serenity 4.2.22 : le constructeur applique
	 * {@code waitForTimeout = (param > 0) ? param : defaultWaitForTimeout()} — un {@code 0} est donc
	 * interprété comme « non défini » et <b>remplacé par le défaut de 5 s</b>
	 * ({@code DefaultTimeouts.DEFAULT_WAIT_FOR_TIMEOUT}), ce qui réactivait l'horloge qu'on croyait
	 * neutraliser. Avec 1 ms, la facade rend la main immédiatement : le {@link #poll} du socle
	 * redevient la <b>seule</b> horloge. Se propage aux vues {@code within} via {@code find()}
	 * (la facade fille hérite du timeout du parent).
	 */
	private static final long NO_FACADE_CLOCK = 1L;

	/**
	 * Contexte de recherche de cette instance : le {@code driver} à la racine, sinon l'élément
	 * <b>container</b> de la vue {@code within} (résolu récursivement). Utilisé par le diagnostic et les
	 * collections pour chercher <b>dans</b> le bon périmètre. Lève si le container est introuvable.
	 */
	private SearchContext resolveScope(WebDriver driver) {
		return parent == null ? driver : parent.resolveScope(driver).findElement(containerLocator);
	}

	/**
	 * Attente explicite : délègue au {@code waitUntil*} de Serenity avec le {@code timeout} donné — une
	 * <b>seule horloge</b> (pas d'enveloppe {@code fluentWait}, donc pas d'empilement C8), Serenity gérant
	 * nativement les attentes positives et négatives. Toute exception au timeout est traduite en
	 * {@link SyncException} (frontière D18, cause conservée).
	 */
	private void await(By locator, Duration awaitTimeout, Consumer<WebElementFacade> waitAction) {
		try {
			waitAction.accept(resolveForWait(locator, awaitTimeout));
		} catch (RuntimeException failure) {
			throw new SyncException("Attente non satisfaite sur " + locator + " après " + humanize(awaitTimeout), failure);
		}
	}

	/** Facade résolu avec un timeout d'attente <b>réel</b> (pour les {@code waitUntil*}), contexte {@code within} inclus. */
	private WebElementFacade resolveForWait(By locator, Duration awaitTimeout) {
		long millis = awaitTimeout.toMillis();
		if (parent == null) {
			return WebElementFacadeImpl.wrapWebElement(driver(), locator, millis, millis, locator.toString());
		}
		return parent.resolveForWait(containerLocator, awaitTimeout).find(locator);
	}

	/**
	 * Interroge un prédicat d'état sur l'élément résolu et rend l'état courant <b>sans jamais lever</b>
	 * (comme les {@code is…}/{@code contains…} : élément absent → {@code false}). Ouvert aux sous-classes
	 * (ex. prédicats web de {@code WebSync}) sans leur exposer le {@code WebElementFacade} lui-même.
	 */
	protected boolean query(By locator, Predicate<WebElementFacade> predicate) {
		return withoutImplicitWait(() -> {
			try {
				return predicate.test(resolve(locator));
			} catch (RuntimeException failure) {
				if (SyncCondition.classify(failure).isPresent()) {
					return false; // élément absent / pas prêt → false : un prédicat ne lève jamais (#2)
				}
				throw failure; // vrai bug (locator invalide…) → remonte
			}
		});
	}

	/**
	 * Diagnostic actif au timeout : sonde l'état réel de l'élément, dans l'<b>ordre du flux</b>
	 * d'interaction, et renvoie <b>toutes</b> les conditions en défaut. La résolution
	 * ({@link SyncCondition#ABSENT}/{@link SyncCondition#STALE}) <b>court-circuite</b> l'étage action ;
	 * l'interception (qui présuppose présent/visible/activé/dans la zone) n'est testée qu'en dernier
	 * ressort. Best-effort : une sonde qui lève n'ajoute pas de faux positif.
	 */
	private Set<SyncCondition> diagnose(By locator) {
		EnumSet<SyncCondition> conditions = EnumSet.noneOf(SyncCondition.class);
		WebDriver driver;
		try {
			driver = driver();
		} catch (RuntimeException noDriver) {
			return conditions; // pas de driver → diagnostic vide (message générique « instable »)
		}
		SearchContext scope;
		try {
			scope = resolveScope(driver); // le container de la vue within (le driver à la racine)
		} catch (NoSuchElementException containerAbsent) {
			conditions.add(SyncCondition.ABSENT); // c'est le CONTAINER (within) qui manque
			return conditions;
		} catch (StaleElementReferenceException stale) {
			conditions.add(SyncCondition.STALE);
			return conditions;
		}
		WebElement element;
		try {
			element = scope.findElement(locator);
		} catch (NoSuchElementException absent) {
			conditions.add(SyncCondition.ABSENT);
			return conditions;
		} catch (StaleElementReferenceException stale) {
			conditions.add(SyncCondition.STALE);
			return conditions;
		}
		if (!probe(element::isDisplayed, true)) {
			conditions.add(SyncCondition.HIDDEN);
		}
		if (!probe(element::isEnabled, true)) {
			conditions.add(SyncCondition.DISABLED);
		}
		if (!inViewport(driver, element)) {
			conditions.add(SyncCondition.OUT_OF_BOUNDS);
		}
		// L'interception présuppose que tout le reste est OK → testée en dernier, seule.
		if (conditions.isEmpty() && isIntercepted(driver, element)) {
			conditions.add(SyncCondition.INTERCEPTED);
		}
		return conditions;
	}

	/** Sonde booléenne best-effort : renvoie {@code onError} si l'appel lève (pas de faux positif). */
	private static boolean probe(BooleanSupplier sonde, boolean onError) {
		try {
			return sonde.getAsBoolean();
		} catch (RuntimeException failure) {
			return onError;
		}
	}

	/** L'élément est-il (au moins partiellement) dans le viewport ? Doute → {@code true} (pas de faux positif). */
	private static boolean inViewport(WebDriver driver, WebElement element) {
		return probe(() -> Boolean.TRUE.equals(js(driver).executeScript(
				"var r=arguments[0].getBoundingClientRect();"
						+ "return r.bottom>0 && r.right>0"
						+ " && r.top<(window.innerHeight||document.documentElement.clientHeight)"
						+ " && r.left<(window.innerWidth||document.documentElement.clientWidth);",
				element)), true);
	}

	/** Un autre élément recouvre-t-il le point central de la cible ? Doute → {@code false} (pas de faux positif). */
	private static boolean isIntercepted(WebDriver driver, WebElement element) {
		return probe(() -> Boolean.TRUE.equals(js(driver).executeScript(
				"var r=arguments[0].getBoundingClientRect();"
						+ "var cx=r.left+r.width/2, cy=r.top+r.height/2;"
						+ "var top=document.elementFromPoint(cx,cy);"
						+ "return !(top===arguments[0] || arguments[0].contains(top)"
						+ " || (top && top.contains(arguments[0])));",
				element)), false);
	}

	private static JavascriptExecutor js(WebDriver driver) {
		return (JavascriptExecutor) driver;
	}

	// ============================================================================================
	// Sentinelle JS de détection d'async (lot 2c) — EMPLACEMENT RÉSERVÉ, à finaliser demain
	// ============================================================================================

	/**
	 * Action <b>mutante</b> (susceptible de déclencher un chargement asynchrone / re-render). Aujourd'hui =
	 * simple synchro par {@link #fluentWait}. Les commentaires ci-dessous marquent l'<b>emplacement</b> de
	 * la sentinelle JS (lot 2c), à câbler sur la structure de référence :
	 * <ul>
	 *   <li><b>avant</b> l'action : déposer un <b>drapeau</b> dans le DOM ;</li>
	 *   <li><b>après</b> l'action : relire le drapeau — <b>disparu</b> = un re-render a balayé le contexte
	 *       (chargement async en cours) → attendre qu'il retombe ; <b>encore là</b> = rien de majeur.</li>
	 * </ul>
	 * Principe voulu : <b>cheap à vide</b> (pas de {@code sleep} systématique), polling uniquement s'il y a
	 * réellement de l'async. Aucune {@code readyState} (l'async n'en change pas). Vide pour l'instant → aucun
	 * impact perf.
	 */
	protected void act(By locator, Consumer<WebElementFacade> action) {
		// ── Emplacement sentinelle JS (lot 2c) : dépôt du drapeau AVANT l'action ──
		fluentWait(locator, element -> {
			action.accept(element);
			return null;
		});
		// ── Emplacement sentinelle JS (lot 2c) : relecture du drapeau APRÈS l'action ──
	}

	/**
	 * Action <b>mutante</b> exécutée sur un {@link WebElement} <b>brut</b> (Selenium natif), et non sur
	 * le {@code WebElementFacade} — <b>seule</b> façon de garantir C8 (« une seule horloge ») sur ces
	 * méthodes.
	 *
	 * <p><b>Pourquoi le brut ici</b> : vérifié au bytecode Serenity 4.2.22, {@code click}, {@code type},
	 * {@code typeAndEnter}, {@code typeAndTab}, {@code clear}, {@code doubleClick} et {@code contextClick}
	 * passent par {@code WithRetries.perform(action, 12)} — <b>12 tentatives espacées de 250 ms</b>,
	 * déclenchées par {@code InvalidElementStateException}. Ce nombre est une <b>constante compilée</b>
	 * (aucune clé, aucun réglage) : c'est une <b>seconde horloge</b> impossible à neutraliser autrement
	 * qu'en ne l'invoquant pas. {@code click} y ajoute {@code waitUntilElementAvailable()}.
	 *
	 * <p>Serenity n'apporte ici <b>aucune capacité</b> que Selenium n'ait pas : ses méthodes sont des
	 * compositions de {@code clear()} / {@code sendKeys()} / {@code Keys} / {@code Actions} appliquées au
	 * {@code WebElement} obtenu par {@code getElement()}. Le socle reproduit ces compositions à
	 * l'identique et <b>réémet lui-même</b> le signal de reporting via {@link #notifyScreenChange()} :
	 * la fonctionnalité est intégralement conservée, seule la seconde horloge disparaît.
	 *
	 * <p>Le reste de la surface (lectures, prédicats, {@code select…}, géométrie, attentes) continue de
	 * passer par le {@code WebElementFacade} : ces méthodes ne portent <b>aucun</b> mécanisme d'attente
	 * propre (audit bytecode), leur comportement temporel est donc déjà celui du seul {@link #poll}.
	 */
	protected void actRaw(By locator, Consumer<WebElement> action) {
		withoutImplicitWait(() -> poll(locator, resolveTimeout(), resolvePolling(),
				() -> {
					WebElement element = resolveElement(locator);
					requireEnabled(element);
					action.accept(element);
					return null;
				},
				() -> diagnose(locator)));
		notifyScreenChange();
	}

	/**
	 * Refuse d'agir sur un élément <b>désactivé</b>, en levant une exception <b>classifiable</b>
	 * ({@link SyncCondition#DISABLED}) que {@link #poll} absorbe : on retente jusqu'au timeout du profil,
	 * puis on remonte le diagnostic {@code [DISABLED]}.
	 *
	 * <p><b>Pourquoi cette garde</b> : le {@code WebElementFacade} vérifiait l'état via
	 * {@code waitUntilEnabled()} avant de cliquer. Le {@code WebElement} natif, lui, <b>accepte
	 * silencieusement</b> un clic sur un {@code <button disabled>} — le navigateur n'exécute rien et ne
	 * lève rien. Sans garde, le socle rendrait donc un <b>faux succès</b> : le test croirait avoir cliqué
	 * et continuerait sur un écran inchangé, ce qui est plus nuisible qu'un échec franc.
	 *
	 * <p>Seul l'état <i>activé</i> est contrôlé ici : les autres empêchements (élément masqué, clic
	 * intercepté, hors zone) font déjà lever le clic natif, et sont donc classés par le moteur sans
	 * sonde supplémentaire — d'où un seul appel WebDriver ajouté par tentative.
	 */
	private static void requireEnabled(WebElement element) {
		if (!element.isEnabled()) {
			throw new InvalidElementStateException("élément présent mais désactivé");
		}
	}

	/**
	 * Résout {@code locator} en {@link WebElement} <b>brut</b> dans le scope courant (le document à la
	 * racine, le container de la vue {@code within} sinon) — pendant « natif » de {@link #resolve(By)}.
	 * Re-résolu à <b>chaque</b> tour de {@link #poll} → no-leak conservé (aucun handle mémorisé).
	 */
	private WebElement resolveElement(By locator) {
		return resolveScope(driver()).findElement(locator);
	}

	/**
	 * Driver du thread courant, pour les sous-classes qui composent une interaction native
	 * ({@code Actions} : double-clic, clic droit…). Le driver reste <b>confiné au thread</b> et n'est
	 * jamais mémorisé — no-leak (D3) préservé, aucun {@code WebElement} n'étant exposé.
	 */
	protected WebDriver currentDriver() {
		return driver();
	}

	/**
	 * Signale à Serenity un <b>changement d'écran</b> après une action mutante : c'est ce que fait
	 * {@code WebElementFacade} en interne, et ce qui permet aux listeners de déclencher une capture
	 * selon {@code serenity.take.screenshots}. Réémis explicitement puisque le socle n'appelle plus la
	 * facade pour ces actions. Best-effort : un incident de reporting ne doit jamais faire échouer
	 * l'action ni le test.
	 */
	private static void notifyScreenChange() {
		try {
			StepEventBus.getParallelEventBus().notifyScreenChange();
		} catch (RuntimeException reportingIssue) {
			// reporting best-effort : l'action a réussi, un souci de rapport ne doit pas la contredire
		}
	}

	// ============================================================================================
	// Recherche imbriquée (no-leak) — retour covariant (WebSync / MobileSync) dans les sous-classes
	// ============================================================================================

	/**
	 * Renvoie une vue bornée aux descendants de {@code container} (recherche imbriquée), sans exposer
	 * de handle : on chaîne des {@code By} relatifs, ré-résolus à chaque action (jamais stale). Chaque
	 * sous-classe précise le type de retour ({@code WebSync} ou {@code MobileSync}).
	 */
	public abstract AbstractSyncManager within(By container);

	// ============================================================================================
	// Actions communes
	// ============================================================================================

	/** Clique/active l'élément cible une fois visible et interactable (WebElement#click). */
	public void click(By locator) {
		actRaw(locator, WebElement::click);
	}

	/** Saisit du texte après avoir vidé le champ (WebElementFacade#type). */
	public void type(By locator, CharSequence... keysToSend) {
		CharSequence[] keys = nonNullKeys(keysToSend);
		actRaw(locator, element -> {
			element.clear();
			element.sendKeys(keys);
		});
	}

	/**
	 * Écarte les {@code null} du tableau de touches — équivalent du {@code nonNullCharSequenceFrom}
	 * interne de Serenity, reproduit ici puisque le socle compose lui-même la saisie.
	 */
	private static CharSequence[] nonNullKeys(CharSequence... keysToSend) {
		if (keysToSend == null) {
			return new CharSequence[0];
		}
		return Arrays.stream(keysToSend).filter(Objects::nonNull).toArray(CharSequence[]::new);
	}

	/**
	 * Saisit une <b>valeur sensible</b> dans le champ cible : la valeur en clair ({@code secret.value()})
	 * est tapée dans le DOM. <b>Point unique</b> où un {@link Secret} est déballé pour la saisie. Le
	 * <b>log d'action masqué</b> ({@code secret.toString()}) sera ajouté avec le domaine LOG (différé) ; le
	 * masquage du DOM à l'échec est déjà couvert par le blanking des champs {@code type=password}
	 * ({@code TestFailureManager}).
	 */
	public void type(By locator, Secret secret) {
		actRaw(locator, element -> {
			element.clear();
			element.sendKeys(secret.value());
		});
	}

	/** Saisit du texte puis presse Entrée (WebElementFacade#typeAndEnter). */
	public void typeAndEnter(By locator, String value) {
		actRaw(locator, element -> {
			element.clear();
			element.sendKeys(value, Keys.ENTER);
		});
	}

	/** Vide la valeur de l'élément cible (WebElement#clear). */
	public void clear(By locator) {
		actRaw(locator, WebElement::clear);
	}

	// ============================================================================================
	// Lectures texte / valeur / métadonnées communes
	// ============================================================================================

	/** Texte visible de l'élément cible (WebElement#getText). */
	public String getText(By locator) {
		return fluentWait(locator, WebElementFacade::getText);
	}

	/** Texte « effectif » résolu par Serenity (valeur de champ ou texte selon l'élément) (WebElementState#getTextValue). */
	public String getTextValue(By locator) {
		return fluentWait(locator, WebElementFacade::getTextValue);
	}

	/** Valeur de l'attribut {@code value} (WebElementFacade#getValue). */
	public String getValue(By locator) {
		return fluentWait(locator, WebElementFacade::getValue);
	}

	/** Nom de balise / type d'élément (WebElement#getTagName). */
	public String getTagName(By locator) {
		return fluentWait(locator, WebElementFacade::getTagName);
	}

	/**
	 * Valeur résolue attribut/propriété (WebElement#getAttribute). {@code getAttribute} est <b>déprécié</b>
	 * par Selenium 4 mais <b>conservé</b> ici : sa sémantique « valeur résolue » (attribut HTML + propriété
	 * DOM) n'est reproduite par aucun de {@link #getDomAttribute}/{@link #getDomProperty} pris seul, et il
	 * fait partie du contrat public gelé.
	 */
	@SuppressWarnings("deprecation")
	public String getAttribute(By locator, String name) {
		return fluentWait(locator, element -> element.getAttribute(name));
	}

	/** Valeur de l'attribut DOM/élément (WebElement#getDomAttribute). */
	public String getDomAttribute(By locator, String name) {
		return fluentWait(locator, element -> element.getDomAttribute(name));
	}

	/** Valeur de la propriété DOM/élément (WebElement#getDomProperty). */
	public String getDomProperty(By locator, String name) {
		return fluentWait(locator, element -> element.getDomProperty(name));
	}

	/** Rôle WAI-ARIA / rôle d'accessibilité calculé (WebElement#getAriaRole). */
	public String getAriaRole(By locator) {
		return fluentWait(locator, WebElementFacade::getAriaRole);
	}

	/** Nom accessible calculé (WebElement#getAccessibleName). */
	public String getAccessibleName(By locator) {
		return fluentWait(locator, WebElementFacade::getAccessibleName);
	}

	// ============================================================================================
	// États booléens communs
	// ============================================================================================

	/** L'élément cible est-il sélectionné/coché (WebElement#isSelected). */
	public boolean isSelected(By locator) {
		return query(locator, WebElementFacade::isSelected);
	}

	/** L'élément cible est-il activé (WebElement#isEnabled). */
	public boolean isEnabled(By locator) {
		return query(locator, WebElementFacade::isEnabled);
	}

	/** L'élément cible est-il désactivé (WebElementState#isDisabled). */
	public boolean isDisabled(By locator) {
		return query(locator, WebElementFacade::isDisabled);
	}

	/** L'élément cible est-il visible, avec attente courte (WebElementState#isVisible). */
	public boolean isVisible(By locator) {
		return query(locator, WebElementFacade::isVisible);
	}

	/** L'élément cible est-il affiché immédiatement, sans attente (WebElementState#isCurrentlyVisible). */
	public boolean isCurrentlyVisible(By locator) {
		return query(locator, WebElementFacade::isCurrentlyVisible);
	}

	/** L'élément cible est-il interactable / cliquable (WebElementState#isClickable). */
	public boolean isClickable(By locator) {
		return query(locator, WebElementFacade::isClickable);
	}

	/** L'élément cible est-il présent dans l'arbre (WebElementState#isPresent). */
	public boolean isPresent(By locator) {
		return query(locator, WebElementFacade::isPresent);
	}

	/** L'élément cible a-t-il le focus (WebElementState#hasFocus). */
	public boolean hasFocus(By locator) {
		return query(locator, WebElementFacade::hasFocus);
	}

	/** L'élément cible contient-il le texte donné (WebElementState#containsText). */
	public boolean containsText(By locator, String value) {
		return query(locator, element -> element.containsText(value));
	}

	/** L'élément cible a-t-il exactement ce texte (WebElementState#containsOnlyText). */
	public boolean containsOnlyText(By locator, String value) {
		return query(locator, element -> element.containsOnlyText(value));
	}

	/** L'élément cible contient-il la valeur donnée (WebElementState#containsValue). */
	public boolean containsValue(By locator, String value) {
		return query(locator, element -> element.containsValue(value));
	}

	// ============================================================================================
	// Assertions d'état communes (should* → void) : attendre la condition, sinon AssertionError
	// via le soft-assert (D22-bis). Messages lisibles, centralisés (non verrouillés).
	// ============================================================================================

	static final String MSG_BE_VISIBLE = "L'élément devrait être visible";
	static final String MSG_BE_CURRENTLY_VISIBLE = "L'élément devrait être visible immédiatement";
	static final String MSG_NOT_BE_VISIBLE = "L'élément ne devrait pas être visible";
	static final String MSG_BE_PRESENT = "L'élément devrait être présent";
	static final String MSG_NOT_BE_PRESENT = "L'élément ne devrait pas être présent";
	static final String MSG_BE_ENABLED = "L'élément devrait être activé";
	static final String MSG_NOT_BE_ENABLED = "L'élément ne devrait pas être activé";
	static final String MSG_BE_SELECTED = "L'élément devrait être sélectionné";
	static final String MSG_NOT_BE_SELECTED = "L'élément ne devrait pas être sélectionné";
	static final String MSG_CONTAIN_TEXT = "L'élément devrait contenir le texte";
	static final String MSG_CONTAIN_ONLY_TEXT = "L'élément devrait contenir uniquement le texte";
	static final String MSG_NOT_CONTAIN_TEXT = "L'élément ne devrait pas contenir le texte";

	/**
	 * Assertion avec attente : poll {@code condition} (un prédicat <b>immédiat</b>) jusqu'au timeout du
	 * profil ; si elle n'est jamais satisfaite, lève une {@link AssertionError} portant {@code message}.
	 * Le tout passe par {@link SoftAssertions#check} → en mode <b>soft</b> l'échec est collecté (le test
	 * tombera en fin de test), en mode <b>hard</b> il est relancé aussitôt. Point de branchement unique
	 * des {@code should…} (D22-bis) — une seule horloge (la boucle), le prédicat n'attend pas lui-même.
	 */
	protected void tryAssert(By locator, BooleanSupplier condition, String message) {
		SoftAssertions.check(() -> withoutImplicitWait(() -> {
			long deadlineNanos = System.nanoTime() + resolveTimeout().toNanos();
			while (!condition.getAsBoolean()) {
				if (System.nanoTime() >= deadlineNanos) {
					throw new AssertionError(message + " : " + locator);
				}
				sleepQuietly(resolvePolling());
			}
			return null;
		}));
	}

	/** Échoue si l'élément n'est pas (ou ne devient pas) visible (WebElementState#shouldBeVisible). */
	public void shouldBeVisible(By locator) {
		tryAssert(locator, () -> isVisible(locator), MSG_BE_VISIBLE);
	}

	/** Échoue si l'élément n'est pas visible <b>immédiatement</b> (WebElementState#shouldBeCurrentlyVisible). */
	public void shouldBeCurrentlyVisible(By locator) {
		SoftAssertions.check(() -> {
			if (!isCurrentlyVisible(locator)) {
				throw new AssertionError(MSG_BE_CURRENTLY_VISIBLE + " : " + locator);
			}
		});
	}

	/** Échoue si l'élément reste visible (WebElementState#shouldNotBeVisible). */
	public void shouldNotBeVisible(By locator) {
		tryAssert(locator, () -> !isVisible(locator), MSG_NOT_BE_VISIBLE);
	}

	/** Échoue si l'élément n'est pas (ou ne devient pas) présent (WebElementState#shouldBePresent). */
	public void shouldBePresent(By locator) {
		tryAssert(locator, () -> isPresent(locator), MSG_BE_PRESENT);
	}

	/** Échoue si l'élément reste présent (WebElementState#shouldNotBePresent). */
	public void shouldNotBePresent(By locator) {
		tryAssert(locator, () -> !isPresent(locator), MSG_NOT_BE_PRESENT);
	}

	/** Échoue si l'élément n'est pas (ou ne devient pas) activé (WebElementState#shouldBeEnabled). */
	public void shouldBeEnabled(By locator) {
		tryAssert(locator, () -> isEnabled(locator), MSG_BE_ENABLED);
	}

	/** Échoue si l'élément reste activé (WebElementState#shouldNotBeEnabled). */
	public void shouldNotBeEnabled(By locator) {
		tryAssert(locator, () -> !isEnabled(locator), MSG_NOT_BE_ENABLED);
	}

	/** Échoue si l'élément n'est pas (ou ne devient pas) sélectionné (WebElementState#shouldBeSelected). */
	public void shouldBeSelected(By locator) {
		tryAssert(locator, () -> isSelected(locator), MSG_BE_SELECTED);
	}

	/** Échoue si l'élément reste sélectionné (WebElementState#shouldNotBeSelected). */
	public void shouldNotBeSelected(By locator) {
		tryAssert(locator, () -> !isSelected(locator), MSG_NOT_BE_SELECTED);
	}

	/** Échoue si l'élément ne contient pas (ou ne finit pas par contenir) le texte (WebElementState#shouldContainText). */
	public void shouldContainText(By locator, String textValue) {
		tryAssert(locator, () -> containsText(locator, textValue), MSG_CONTAIN_TEXT + " « " + textValue + " »");
	}

	/** Échoue si l'élément ne contient pas exactement ce texte (WebElementState#shouldContainOnlyText). */
	public void shouldContainOnlyText(By locator, String textValue) {
		tryAssert(locator, () -> containsOnlyText(locator, textValue), MSG_CONTAIN_ONLY_TEXT + " « " + textValue + " »");
	}

	/** Échoue si l'élément contient (ou continue de contenir) le texte (WebElementState#shouldNotContainText). */
	public void shouldNotContainText(By locator, String textValue) {
		tryAssert(locator, () -> !containsText(locator, textValue), MSG_NOT_CONTAIN_TEXT + " « " + textValue + " »");
	}

	// ============================================================================================
	// Géométrie / capture communes
	// ============================================================================================

	/** Coin supérieur gauche de l'élément rendu (WebElement#getLocation). */
	public Point getLocation(By locator) {
		return fluentWait(locator, WebElementFacade::getLocation);
	}

	/** Largeur/hauteur de l'élément rendu (WebElement#getSize). */
	public Dimension getSize(By locator) {
		return fluentWait(locator, WebElementFacade::getSize);
	}

	/** Position et taille de l'élément rendu (WebElement#getRect). */
	public Rectangle getRect(By locator) {
		return fluentWait(locator, WebElementFacade::getRect);
	}

	/** Capture d'écran de l'élément cible (WebElement#getScreenshotAs / TakesScreenshot). */
	public <X> X getScreenshotAs(By locator, OutputType<X> target) {
		return fluentWait(locator, element -> element.getScreenshotAs(target));
	}

	// ============================================================================================
	// Collections communes (sans handle vivant)
	// ============================================================================================

	/** Nombre d'éléments correspondant au locator (remplace findElements brut) — état courant, sans attente. */
	public int count(By locator) {
		return findAll(locator).size();
	}

	/** Textes visibles de tous les éléments correspondant au locator (information extraite, non stale). */
	public List<String> getTexts(By locator) {
		return findAll(locator).stream().map(WebElement::getText).toList();
	}

	/**
	 * Éléments correspondant au locator dans le <b>scope</b> courant (le document à la racine, le container
	 * within sinon). État courant, sans attente : container introuvable → liste vide (jamais d'exception).
	 */
	private List<WebElement> findAll(By locator) {
		WebDriver driver = driver();
		try {
			return resolveScope(driver).findElements(locator);
		} catch (NoSuchElementException | StaleElementReferenceException containerGone) {
			return List.of();
		}
	}

	// ============================================================================================
	// Attentes explicites communes
	// ============================================================================================

	/** Attend que l'élément cible devienne visible (WebElementFacade#waitUntilVisible). */
	public void waitUntilVisible(By locator) {
		await(locator, resolveTimeout(), WebElementFacade::waitUntilVisible);
	}

	/** Attend que l'élément cible devienne présent (WebElementFacade#waitUntilPresent). */
	public void waitUntilPresent(By locator) {
		await(locator, resolveTimeout(), WebElementFacade::waitUntilPresent);
	}

	/** Attend que l'élément cible devienne invisible (WebElementFacade#waitUntilNotVisible). */
	public void waitUntilNotVisible(By locator) {
		await(locator, resolveTimeout(), WebElementFacade::waitUntilNotVisible);
	}

	/** Attend que l'élément cible devienne cliquable/interactable (WebElementFacade#waitUntilClickable). */
	public void waitUntilClickable(By locator) {
		await(locator, resolveTimeout(), WebElementFacade::waitUntilClickable);
	}

	/** Attend que l'élément cible devienne activé (WebElementFacade#waitUntilEnabled). */
	public void waitUntilEnabled(By locator) {
		await(locator, resolveTimeout(), WebElementFacade::waitUntilEnabled);
	}

	/** Attend que l'élément cible devienne désactivé (WebElementFacade#waitUntilDisabled). */
	public void waitUntilDisabled(By locator) {
		await(locator, resolveTimeout(), WebElementFacade::waitUntilDisabled);
	}

	/** Attend la présence/interactabilité de l'élément cible jusqu'au timeout fourni. */
	public void waitForElement(By locator, Duration timeout) {
		await(locator, timeout, WebElementFacade::waitUntilVisible);
	}

	/** Attend la disparition de l'élément cible jusqu'au timeout fourni. */
	public void waitForElementToDisappear(By locator, Duration timeout) {
		await(locator, timeout, WebElementFacade::waitUntilNotVisible);
	}
}
