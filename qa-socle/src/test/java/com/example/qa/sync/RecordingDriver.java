package com.example.qa.sync;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.thucydides.core.webdriver.ConfigurableTimeouts;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Driver simulé pour tester le moteur sans navigateur, injecté par
 * {@code ThucydidesWebDriverSupport.useDriver}. Porte un implicit wait comme le {@code WebDriverFacade} de
 * Serenity ({@link ConfigurableTimeouts}), note celui en vigueur à chaque recherche, et enregistre les
 * scripts exécutés. Page vide par défaut : seuls les locators déclarés par {@link #withElement} existent.
 *
 * <p>Le nom de la classe ne doit pas contenir {@code Mock} : Serenity n'exécute alors aucun JavaScript
 * ({@code JavascriptSupport#isNotAMockedDriver}).
 */
final class RecordingDriver implements WebDriver, ConfigurableTimeouts, JavascriptExecutor {

	private Duration implicitWait;
	private final Set<By> presentLocators = new HashSet<>();
	private final List<Duration> implicitWaitSeenByLookups = new ArrayList<>();
	private final List<String> executedScripts = new ArrayList<>();

	RecordingDriver(Duration implicitWait) {
		this.implicitWait = implicitWait;
	}

	/** Déclare un élément présent dans la page pour {@code locator}. */
	RecordingDriver withElement(By locator) {
		presentLocators.add(locator);
		return this;
	}

	/** Implicit wait en vigueur au moment de chaque recherche ({@code findElement} / {@code findElements}). */
	List<Duration> implicitWaitSeenByLookups() {
		return implicitWaitSeenByLookups;
	}

	/** Scripts exécutés par {@code executeScript}, dans l'ordre. */
	List<String> executedScripts() {
		return executedScripts;
	}

	@Override
	public List<WebElement> findElements(By by) {
		implicitWaitSeenByLookups.add(implicitWait);
		return presentLocators.contains(by) ? List.of(stubElement(by)) : List.of();
	}

	@Override
	public WebElement findElement(By by) {
		implicitWaitSeenByLookups.add(implicitWait);
		if (!presentLocators.contains(by)) {
			throw new NoSuchElementException("absent : " + by);
		}
		return stubElement(by);
	}

	/** Élément sans comportement : toute interaction lève, seule sa présence compte. */
	private static WebElement stubElement(By by) {
		return (WebElement) Proxy.newProxyInstance(WebElement.class.getClassLoader(), new Class<?>[] {WebElement.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "toString" -> "élément simulé " + by;
					case "hashCode" -> System.identityHashCode(proxy);
					case "equals" -> proxy == args[0];
					default -> throw new UnsupportedOperationException(method.getName());
				});
	}

	@Override
	public Object executeScript(String script, Object... args) {
		executedScripts.add(script);
		return null;
	}

	@Override
	public Object executeAsyncScript(String script, Object... args) {
		executedScripts.add(script);
		return null;
	}

	@Override
	public void setImplicitTimeout(Duration implicitTimeout) {
		this.implicitWait = implicitTimeout;
	}

	@Override
	public Duration getCurrentImplicitTimeout() {
		return implicitWait;
	}

	@Override
	public Duration resetTimeouts() {
		return implicitWait;
	}

	@Override
	public void get(String url) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getCurrentUrl() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getTitle() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getPageSource() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void close() {
		// rien à libérer
	}

	@Override
	public void quit() {
		// rien à libérer
	}

	@Override
	public Set<String> getWindowHandles() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getWindowHandle() {
		throw new UnsupportedOperationException();
	}

	@Override
	public TargetLocator switchTo() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Navigation navigate() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Options manage() {
		throw new UnsupportedOperationException(); // pageLoadTimeout : best-effort côté socle
	}
}
