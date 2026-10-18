package com.example.qa.sync;

import java.util.List;
import net.serenitybdd.core.pages.WebElementFacade;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.interactions.Actions;

/**
 * Façade de synchronisation et d'interaction <b>web</b> (WebDriver).
 *
 * <p>Hérite d'{@code AbstractSyncManager} (moteur commun, package-private) <b>tout</b> le socle commun (moteur {@code fluentWait} +
 * flag JS, et l'intégralité des actions/lectures/états/attentes par {@link By}, déléguées à l'API
 * publique Serenity {@code WebElementFacade}). {@code WebSync} n'ajoute donc <b>que le spécifique
 * web</b> : soumission de formulaire, double/clic-droit, clic et saisie par JavaScript, listes
 * déroulantes HTML {@code <select>}, classes CSS, focus fenêtre, propriétés CSS calculées.
 *
 * <p><b>No-leak (D3)</b> conservé : tout passe par {@code By}, aucune méthode ne rend de
 * {@code WebElement} vivant. La recherche imbriquée {@link #within(By)} renvoie un {@code WebSync}
 * (retour covariant).
 *
 * <p><b>Non extensible</b> (D15) : cette façade est {@code final}. Le socle est livré recetté par
 * {@code qa-test} ; un défaut se corrige <b>dans le socle</b>, jamais par une surcharge côté projet.
 *
 * <p>Implémenté (étape 8) : domaine SYNC web validé au navigateur (campagne {@code qa-test} du
 * 2026-08-02).
 */
public final class WebSync extends AbstractSyncManager {

	/** Façade web au profil de timeout par défaut ({@link SyncTimeout#MEDIUM}). */
	public WebSync() {
		super(SyncTimeout.MEDIUM);
	}

	/** Façade web au profil de timeout donné (zone plus ou moins lente à se charger). */
	public WebSync(SyncTimeout timeout) {
		super(timeout);
	}

	/** Vue bornée {@code within} (usage interne, cf. {@link #within(By)}). */
	WebSync(AbstractSyncManager parent, By container) {
		super(parent, container);
	}

	@Override
	public WebSync within(By container) {
		return new WebSync(this, container);
	}

	// ============================================================================================
	// Actions spécifiques web
	// ============================================================================================

	/** Soumet le formulaire contenant l'élément cible (WebElement#submit). */
	public void submit(By locator) {
		act(locator, WebElementFacade::submit);
	}

	/** Double-clic sur l'élément cible (WebElementFacade#doubleClick). */
	public void doubleClick(By locator) {
		actRaw(locator, element -> new Actions(currentDriver()).doubleClick(element).perform());
	}

	/** Clic droit / contextuel sur l'élément cible (WebElementFacade#contextClick). */
	public void contextClick(By locator) {
		actRaw(locator, element -> new Actions(currentDriver()).contextClick(element).perform());
	}

	/** Saisit du texte puis presse Tab (WebElementFacade#typeAndTab). */
	public void typeAndTab(By locator, String value) {
		actRaw(locator, element -> {
			element.clear();
			element.sendKeys(value);
			element.sendKeys(Keys.TAB);
		});
	}

	/**
	 * Script du clic JavaScript : déclenche le gestionnaire de clic de l'élément, sans passer par
	 * l'interaction native (ni défilement, ni contrôle de visibilité ou de recouvrement).
	 */
	private static final String CLICK_VIA_JS_SCRIPT = "arguments[0].click();";

	/**
	 * Script de la saisie JavaScript : donne le focus, affecte la valeur par le <b>setter natif du
	 * prototype</b> (et non par la propriété de l'instance, que certains frameworks surveillent pour
	 * détecter un changement), puis déclenche {@code input} et {@code change}. Refuse tout autre élément
	 * qu'un {@code input} ou un {@code textarea} : sans cela l'affectation serait sans effet et l'appel
	 * réussirait à tort.
	 */
	private static final String TYPE_VIA_JS_SCRIPT =
			"var el=arguments[0], v=arguments[1];"
					+ "var isInput=el instanceof HTMLInputElement, isArea=el instanceof HTMLTextAreaElement;"
					+ "if(!isInput && !isArea){throw new Error(\"typeViaJS : l'element n'est ni un input ni un textarea\");}"
					+ "el.focus();"
					+ "var proto=isInput ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype;"
					+ "Object.getOwnPropertyDescriptor(proto,'value').set.call(el,v);"
					+ "el.dispatchEvent(new Event('input',{bubbles:true}));"
					+ "el.dispatchEvent(new Event('change',{bubbles:true}));";

	/**
	 * Clique par <b>JavaScript</b> ({@code arguments[0].click()}) sur l'élément cible, pour les composants
	 * pilotés par JavaScript dont le clic natif ({@code click(By)}) reste sans effet.
	 *
	 * <p>Même cadre que les autres actions : attente de l'élément (profil de timeout), refus d'un élément
	 * <b>désactivé</b> ({@code [DISABLED]} au timeout), diagnostic, reporting, et fonctionne dans
	 * {@link #within(By)}. En revanche, le clic JavaScript <b>saute</b> les contrôles de l'interaction
	 * native : pas de défilement, pas de vérification de visibilité ni de recouvrement.
	 */
	public void clickViaJS(By locator) {
		actRaw(locator, element -> js(currentDriver()).executeScript(CLICK_VIA_JS_SCRIPT, element));
	}

	/**
	 * Saisit du texte par <b>JavaScript</b> dans un {@code input} ou un {@code textarea}, pour les champs
	 * pilotés par JavaScript dont la saisie native ({@code type(By, CharSequence...)}) reste sans effet.
	 * La valeur existante est <b>remplacée</b> ; les événements {@code input} et {@code change} sont
	 * déclenchés pour que le framework de la page prenne la valeur en compte.
	 *
	 * <p>Même cadre que les autres actions (attente, garde d'activation, diagnostic, reporting,
	 * {@code within}). <b>Pas de variante {@code Secret}</b> : un secret se saisit avec
	 * {@code type(By, Secret)}.
	 *
	 * @param value texte à saisir (non {@code null})
	 */
	public void typeViaJS(By locator, String value) {
		actRaw(locator, element -> js(currentDriver()).executeScript(TYPE_VIA_JS_SCRIPT, element, value));
	}

	/**
	 * Attend la présence de l'élément cible (profil de l'instance), puis donne le focus à la <b>fenêtre</b>
	 * courante ({@code window.focus()}, WebElementFacade#setWindowFocus). Le focus porte sur la fenêtre,
	 * pas sur l'élément. Élément absent au timeout → {@link SyncException} {@code [ABSENT]}.
	 */
	public void setWindowFocus(By locator) {
		fluentWait(locator, element -> {
			element.getWrappedElement(); // Serenity ne résout pas l'élément : résolution forcée (absent → [ABSENT])
			element.setWindowFocus();
			return null;
		});
	}

	// ============================================================================================
	// Listes déroulantes HTML <select> (spécifique web)
	// ============================================================================================

	/** Sélectionne une option par son libellé visible (WebElementFacade#selectByVisibleText). */
	public void selectByVisibleText(By locator, String visibleText) {
		fluentWait(locator, element -> {
			element.selectByVisibleText(visibleText);
			return null;
		});
	}

	/** Sélectionne une option par sa valeur (WebElementFacade#selectByValue). */
	public void selectByValue(By locator, String value) {
		fluentWait(locator, element -> {
			element.selectByValue(value);
			return null;
		});
	}

	/** Sélectionne une option par son index (WebElementFacade#selectByIndex). */
	public void selectByIndex(By locator, int index) {
		fluentWait(locator, element -> {
			element.selectByIndex(index);
			return null;
		});
	}

	/** Désélectionne une option par son libellé visible (WebElementFacade#deselectByVisibleText). */
	public void deselectByVisibleText(By locator, String visibleText) {
		fluentWait(locator, element -> {
			element.deselectByVisibleText(visibleText);
			return null;
		});
	}

	/** Désélectionne une option par sa valeur (WebElementFacade#deselectByValue). */
	public void deselectByValue(By locator, String value) {
		fluentWait(locator, element -> {
			element.deselectByValue(value);
			return null;
		});
	}

	/** Désélectionne une option par son index (WebElementFacade#deselectByIndex). */
	public void deselectByIndex(By locator, int index) {
		fluentWait(locator, element -> {
			element.deselectByIndex(index);
			return null;
		});
	}

	/** Désélectionne toutes les options (WebElementFacade#deselectAll). */
	public void deselectAll(By locator) {
		fluentWait(locator, element -> {
			element.deselectAll();
			return null;
		});
	}

	/** Libellés visibles de toutes les options de la liste (WebElementFacade#getSelectOptions). */
	public List<String> getSelectOptions(By locator) {
		return fluentWait(locator, WebElementFacade::getSelectOptions);
	}

	/** Valeurs de toutes les options de la liste (WebElementFacade#getSelectOptionValues). */
	public List<String> getSelectOptionValues(By locator) {
		return fluentWait(locator, WebElementFacade::getSelectOptionValues);
	}

	/** Libellé visible de la 1ʳᵉ option sélectionnée (WebElementFacade#getFirstSelectedOptionVisibleText). */
	public String getFirstSelectedOptionVisibleText(By locator) {
		return fluentWait(locator, WebElementFacade::getFirstSelectedOptionVisibleText);
	}

	/** Libellés visibles de toutes les options sélectionnées (WebElementState#getSelectedVisibleTexts). */
	public List<String> getSelectedVisibleTexts(By locator) {
		return fluentWait(locator, WebElementFacade::getSelectedVisibleTexts);
	}

	/** Valeur de la 1ʳᵉ option sélectionnée (WebElementFacade#getFirstSelectedOptionValue). */
	public String getFirstSelectedOptionValue(By locator) {
		return fluentWait(locator, WebElementFacade::getFirstSelectedOptionValue);
	}

	/** Valeurs de toutes les options sélectionnées (WebElementState#getSelectedValues). */
	public List<String> getSelectedValues(By locator) {
		return fluentWait(locator, WebElementFacade::getSelectedValues);
	}

	/** La liste déroulante contient-elle l'option donnée (WebElementState#containsSelectOption). */
	public boolean containsSelectOption(By locator, String value) {
		return query(locator, element -> element.containsSelectOption(value));
	}

	/** Message d'échec de {@link #shouldContainSelectedOption} (lisible, non verrouillé). */
	private static final String MSG_CONTAIN_SELECTED_OPTION = "La liste devrait contenir l'option";

	/** Échoue si la liste ne contient pas (ou ne finit pas par contenir) l'option (WebElementState#shouldContainSelectedOption). */
	public void shouldContainSelectedOption(By locator, String textValue) {
		tryAssert(locator, () -> containsSelectOption(locator, textValue), MSG_CONTAIN_SELECTED_OPTION + " « " + textValue + " »");
	}

	// ============================================================================================
	// Spécifique DOM web
	// ============================================================================================

	/** Texte interne brut, y compris masqué (WebElementFacade#getTextContent). */
	public String getTextContent(By locator) {
		return fluentWait(locator, WebElementFacade::getTextContent);
	}

	/** Libellé ARIA résolu par Serenity (WebElementFacade#getAriaLabel). */
	public String getAriaLabel(By locator) {
		return fluentWait(locator, WebElementFacade::getAriaLabel);
	}

	/** Valeur calculée d'une propriété CSS (WebElement#getCssValue). */
	public String getCssValue(By locator, String propertyName) {
		return fluentWait(locator, element -> element.getCssValue(propertyName));
	}

	/** L'élément cible porte-t-il la classe CSS donnée (WebElementFacade#hasClass). */
	public boolean hasClass(By locator, String cssClassName) {
		return query(locator, element -> element.hasClass(cssClassName));
	}

	/** Le conteneur cible contient-il des éléments correspondant au sélecteur (WebElementFacade#containsElements). */
	public boolean containsElements(By locator, By childSelector) {
		return query(locator, element -> element.containsElements(childSelector));
	}
}
