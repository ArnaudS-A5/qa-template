---
name: maven-local-run-debug
description: "Exécution et debug LOCAUX ciblés des tests Serenity/JUnit 5 via Maven (test unique, suite, profils local/grid, propriétés WebDriver et Serenity). USE FOR : lancer un test précis, déboguer localement, agréger les rapports. DO NOT USE FOR : configuration Artifactory ou pipeline CI."
---

# Skill — Exécution & debug locaux via Maven

> Priorité de la refonte : exécution et **debug en local via Maven** (avant : JUnit local + Maven en CI).

## Commandes de base

```bash
# Exécuter un test précis
mvn test -Dtest=SouscriptionChienTest

# Exécuter une méthode précise
mvn test -Dtest=SouscriptionChienTest#souscrire_assurance_chien_tous_risques

# Exécuter plusieurs classes
mvn test -Dtest=SouscriptionChienTest,DevisHabitationTest

# Choisir le navigateur
mvn test -Dtest=SouscriptionChienTest -Dwebdriver.driver=chrome
```

## Profils d'exécution

- **local** : WebDriver direct (maintenance, campagne, debug). Profil par défaut recommandé.
- **grid** : exécution sur Selenium Grid / Selenoid (disponible).
- **browserstack** : à activer quand les licences seront disponibles.

```bash
mvn test -Plocal -Dtest=SouscriptionChienTest
mvn test -Pgrid  -Dtest=SouscriptionChienTest
```

## Propriétés Serenity utiles

| Propriété | Usage |
|---|---|
| `serenity.outputDirectory` | Répertoire des rapports |
| `restart.browser.each.scenario` | Redémarrage du navigateur par scénario |
| `webdriver.timeouts.implicitlywait` | À garder bas : la synchro passe par `WebSync` |
| `headless.mode` | Exécution sans interface (CI / runs massifs) |

## Debug local

- Lancer en debug depuis VS Code (cf. skill `vscode-config-troubleshooting` pour `launch.json`).
- Pour attacher un debugger Maven en ligne de commande :

```bash
mvnDebug test -Dtest=SouscriptionChienTest
```

## Agrégation des rapports

```bash
mvn serenity:aggregate
# Rapport HTML : target/site/serenity/index.html
```

## Résultats exploitables par la maintenance

Un dossier `KO__...` est créé sous `target/qa-results/` **uniquement** pour chaque test en échec
(aucun artefact pour un test OK — cf. convention de nommage du README). Après un run massif, ces
dossiers sont la matière première de l'agent `qa-maintenance`.

## Bonnes pratiques

- Toujours **cibler** le test en cours de travail (`-Dtest=`) pour des boucles rapides.
- Réexécuter **uniquement** le test corrigé pour valider, avant d'élargir.
- Préférer le profil **local** pour le debug pas-à-pas.
