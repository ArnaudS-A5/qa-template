package com.example.qa.api.sync;

import java.util.List;
import net.serenitybdd.core.pages.WebElementFacade;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.interactions.Actions;
import com.example.qa.internal.sync.AbstractSyncManager;

/**
 * Façade de synchronisation et d'interaction <b>web</b> (WebDriver).
 *
 * <p>Hérite de {@link AbstractSyncManager} <b>tout</b> le socle commun (moteur {@code fluentWait} +
 * flag JS, et l'intégralité des actions/lectures/états/attentes par {@link By}, déléguées à l'API
 * publique Serenity {@code WebElementFacade}). {@code WebSync} n'ajoute donc <b>que le spécifique
 * web</b> : soumission de formulaire, double/clic-droit, listes déroulantes HTML {@code <select>},
 * classes CSS, focus fenêtre, propriétés CSS calculées.
 *
 * <p><b>No-leak (D3)</b> conservé : tout passe par {@code By}, aucune méthode ne rend de
 * {@code WebElement} vivant. La recherche imbriquée {@link #within(By)} renvoie un {@code WebSync}
 * (retour covariant).
 *
 * <p><b>Échappatoire « bris de glace »</b> (D15) : cette façade est volontairement <b>sous-classable</b>
 * et ses méthodes <b>non-{@code final}</b>. En cas de bug bloquant de synchro côté projet, créer une
 * <b>sous-classe nommée</b> qui surcharge la/les méthode(s) fautive(s) et l'instancier via un
 * <b>point d'obtention unique</b> du projet (un seul endroit à basculer puis à restaurer ; PageObjects
 * et tests intouchés, le type déclaré restant {@code WebSync}). Le correctif stable est ensuite
 * <b>promu dans le socle</b> (changement de corps = non-breaking). Sous-classe <b>nommée</b> conseillée
 * (plus facile à analyser/refactorer qu'une classe anonyme).
 *
 * <p>Squelette — signatures arrêtées (étape 6), corps réel à fournir (étape 8).
 */
public class WebSync extends AbstractSyncManager {

	/** Façade web au profil de timeout par défaut ({@link SyncTimeout#MEDIUM}). */
	public WebSync() {
		super(SyncTimeout.MEDIUM);
	}

	/** Façade web au profil de timeout donné (zone plus ou moins lente à se charger). */
	public WebSync(SyncTimeout timeout) {
		super(timeout);
	}

	/** Vue bornée {@code within} (usage interne, cf. {@link #within(By)}). */
	protected WebSync(AbstractSyncManager parent, By container) {
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

	/** Donne le focus fenêtre à l'élément cible (WebElementFacade#setWindowFocus). */
	public void setWindowFocus(By locator) {
		fluentWait(locator, element -> {
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
