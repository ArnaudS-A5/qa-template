---
name: serenity-pom-conventions
description: "Conventions d'architecture Page Object Model pour Serenity BDD + JUnit 5 (sans Cucumber). USE FOR : structurer pages/steps/tests, nommer les classes, séparer les couches, intégrer le socle. DO NOT USE FOR : stratégie d'attente (voir stable-locators-and-waits) ou data-driven (voir test-data-excel-manager)."
---

# Skill — Conventions Page Object Model (Serenity + JUnit 5)

## Architecture en couches

```
src/test/java/.../
├── pages/      # PageObjects : déclaration des locators + actions bas niveau (via WebSync)
├── steps/      # Steps : actions métier lisibles, orchestrent les PageObjects
└── tests/      # Classes de test JUnit 5 : scénarios, assertions
```

**Séparation stricte :**

- Un **PageObject** déclare les locators et expose des actions élémentaires. Il ne contient **aucune**
  logique de test ni assertion.
- Une **Step** exprime une action métier (« souscrire une assurance chien »). Elle **n'expose aucun
  locator** et orchestre les PageObjects.
- Une **classe de test** décrit le scénario et porte les **assertions**.

## Conventions de nommage

- PageObject : `XxxPage` (ex. `SouscriptionPage`).
- Steps : `XxxSteps` (ex. `SouscriptionSteps`).
- Test : `XxxTest` (ex. `SouscriptionChienTest`).
- Méthodes de Step : verbe métier explicite (`cocherGarantie`, `selectionnerProfessionnelSante`).

## Squelette JUnit 5 + Serenity

```java
@ExtendWith(SerenityJUnit5Extension.class)
class SouscriptionChienTest {

    @Steps
    SouscriptionSteps souscription;

    @Test
    void souscrire_assurance_chien_tous_risques() {
        souscription.ouvrirFormulaire();
        souscription.cocherGarantie("Assurance chien tous risques");
        souscription.valider();
        // assertions...
    }
}
```

## Intégration du socle (obligatoire)

- **Toutes** les interactions passent par `WebSync` (jamais `WebElement`/`Thread.sleep` direct).
  Cf. `stable-locators-and-waits`.
- **QaLogger** aux points clés (entrée d'action, choix de données).
- **TestFailureManager** sur les échecs (production des fichiers `ERROR_/FAIL_*.log` et dump HTML).
- Données via **`DataFileManager`** (cf. `test-data-excel-manager`).

## Cohérence avec la dépendance

Le PageObject côté projet consommateur peut **étendre** une classe du socle (ex. un `WebSync`
spécialisé) pour ajouter des actions métier **sans dupliquer** le moteur d'attente. Cf. `custom-toolkit-integration`.

## Anti-patterns à refuser

- Locator déclaré dans une Step ou un Test.
- Assertion dans un PageObject.
- Logique de test dans un PageObject.
- Couplage direct au driver Selenium hors du socle.
