---
name: stable-locators-and-waits
description: "Stratégie de locators stables et de synchronisation pour SPA (Angular/React/Vue) via WebSync/fluentWait + flag JS. USE FOR : écrire des attentes robustes, choisir des locators fiables, éviter Thread.sleep et les exceptions de re-render. DO NOT USE FOR : configuration Maven ou reporting."
---

# Skill — Locators stables & synchronisation (`WebSync`)

## Objectif

Garantir des interactions **robustes** sur des applications **SPA** (Angular, React, Vue) où le DOM
mute **sans** changement d'URL ni de state, et où des exceptions transitoires surviennent pendant les
re-renders.

## 1. Hiérarchie des locators (du plus stable au moins stable)

1. **`data-*`** (ex. `data-testid`) — idéal, stable, non couplé au style.
2. **`id`** — stable s'il est maîtrisé (attention aux ids générés dynamiquement).
3. **CSS sémantique** — attributs métier (`name`, `aria-label`, rôle).
4. **XPath par label relatif** — en dernier recours, ancré sur un **texte métier** stable
   (ex. `//label[normalize-space()='Assurance chien tous risques']`).

À éviter : xpath absolus, index positionnels, classes CSS de style, textes traduits volatils.

## 2. Synchronisation : tout passe par `WebSync`

### Règles

- **Aucune** utilisation directe de `WebDriverWait`, `Thread.sleep`, ou `WebElement` dans les
  PageObjects/Steps.
- **Toutes** les actions (`click`, `type`, `selectByLabel`, `getText`, `isDisplayed`, ...) passent par
  une méthode **privée** `fluentWait(...)` interne à `WebSync`.

### Moteur `fluentWait`

`FluentWait` avec :

- **polling court** (~250 ms) pour rester réactif sans marteler le DOM ;
- **`ignoring(...)`** des exceptions de re-render :
  - `NoSuchElementException`
  - `StaleElementReferenceException`
  - `ElementNotInteractableException`
  - `ElementClickInterceptedException`

```java
protected <T> T fluentWait(Function<WebDriver, T> condition) {
    return new FluentWait<>(driver)
            .withTimeout(Duration.ofSeconds(30))
            .pollingEvery(Duration.ofMillis(250))
            .ignoring(NoSuchElementException.class)
            .ignoring(StaleElementReferenceException.class)
            .ignoring(ElementNotInteractableException.class)
            .ignoring(ElementClickInterceptedException.class)
            .until(condition);
}
```

> NB : exemple indicatif. Les signatures réelles seront alignées sur le code existant fourni
> (plusieurs propositions de synchro ont été développées ; faire le tri).

### Flag JavaScript (détection de chargement/re-render)

- On **n'injecte pas** de code applicatif : on injecte un simple **drapeau** (le re-render efface le
  contexte JS, donc le drapeau avec).
- `WebSync` **pose** le drapeau ; sa **disparition** signale qu'un re-render/chargement est en cours
  (ou vient de se produire) → re-poser le drapeau et continuer à attendre.
- Drapeau **présent** + élément cible stable → la page est prête.
- Bien définir **qui pose** le drapeau et **quand il est re-posé** (éviter un drapeau « collé » qui
  survivrait au re-render et donnerait un faux « prêt »).

### Pourquoi attendre l'élément cible plutôt que chasser un spinner

Les spinners/overlays sont incohérents (absents, multiples, sélecteurs changeants). Attendre l'**état
stable de l'élément cible** en **absorbant les exceptions** est plus simple et plus fiable.

## 3. Messages d'erreur différenciés (au timeout)

À produire pour alimenter `TestFailureManager` et l'agent d'auto-correction :

- Élément **absent** au timeout → « mauvaise page / page non chargée dans les délais ».
- Élément **présent mais jamais interactable** → « application instable / indisponible ».

## 4. Architecture (anticipation mobile)

`AbstractSyncManager` (moteur `fluentWait` + flag JS) → `WebSync` (web) / `MobileSync`
(plus tard). L'abstraction n'est justifiée que par l'arrivée du mobile (driver différent, moteur commun).

## Anti-patterns à refuser

- `Thread.sleep(...)`.
- `WebDriverWait` exposé hors du socle.
- Catch silencieux d'exceptions sans `ignoring` structuré.
- Locators positionnels / xpath absolus.
