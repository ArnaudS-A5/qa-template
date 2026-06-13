# Socle QA — `qa-parent` / `qa-socle`

Socle d'outils QA custom pour la stack **Selenium Java / Serenity BDD / JUnit 5 / Maven**,
consommé **comme dépendance Maven** par les ~17 projets de tests de l'équipe
(Git/Bitbucket, Artifactory JFrog, CI Jenkins gérée par une autre équipe).

> **Statut : squelette en construction.** Les classes sont des coquilles vides (par choix) ;
> la séquence de construction est pilotée par la [feuille de route](qa-socle/docs/roadmap.md).

## Sommaire

- [Structure du dépôt](#structure-du-dépôt)
- [Build](#build)
- [Stack & contexte](#stack--contexte)
- [Architecture du socle](#architecture-du-socle)
- [Convention de nommage des résultats de tests](#convention-de-nommage-des-résultats-de-tests)
- [Documentation](#documentation)
- [Assistance Copilot (annexe)](#assistance-copilot-annexe)

## Structure du dépôt

```
pom.xml         # Parent POM (qa-parent) : centralise versions (dépendances + plugins), agrégateur
qa-socle/       # La librairie socle (jar) : WebSync, DataFileManager, QaLogger, TestFailureManager...
  └── docs/     # Décisions d'architecture, feuille de route, gouvernance Git
.github/        # Config Copilot (agents + skills) — annexe, à externaliser (voir plus bas)
```

## Build

```bash
mvn clean test    # depuis la racine du dépôt
```

Le POM racine (`qa-parent`) est l'agrégateur : il builde l'ensemble du socle. `qa-socle` en hérite
(aucune version déclarée dans les modules, tout est piloté par le `dependencyManagement` du parent).

## Stack & contexte

| Dimension | Choix |
|---|---|
| Langage / outil | Java 17, Selenium, **Serenity BDD** (sans Cucumber/Gherkin) |
| Tests | **JUnit 5**, exécution + debug **locale via Maven** prioritaire |
| Architecture consommateurs | **Page Object Model** (PageObjects + Steps) |
| Build / dépôt | **Maven**, **Git/Bitbucket**, **Artifactory JFrog** (`settings.xml`) |
| CI | **Jenkins** (cible) — géré par une **autre équipe** (collaboration) |
| Exécution | Local (WebDriver direct) prioritaire ; **Selenium Grid/Selenoid** dispo ; **BrowserStack** en attente de licences |
| Versioning | **Parent POM + version `RELEASE`** chez les consommateurs (décision D6) |
| Mobile | **Appium** anticipé (à venir) |

## Architecture du socle

Frontière **`api`** (contrat public consommé par les 17 projets) vs **`internal`** (impls + moteurs,
non contractuels), cf. décision [D15](qa-socle/docs/decisions.md).

| Domaine | `api` (public) | `internal` (caché) | Rôle |
|---|---|---|---|
| `sync` | `WebSync`, `MobileSync` | `AbstractSyncManager` | synchronisation robuste (fluentWait + flag JS) |
| `data` | `DataFileManager` | `AbstractDataFileManager`, `ExcelFileReaderWriter`, `CsvFileReaderWriter` | données de test Excel/CSV (lecture + écriture) |
| `log` | `QaLogger` | — | journalisation aux points clés (façade SLF4J) |
| `failure` | *(hook à venir, étape 6)* | `TestFailureManager` | artefacts d'échec (logs + dump HTML) |
| `secret` | `SecretManager` | `CyberArkApiClient` | récupération de secrets au runtime (D12) |
| `reporting` | `ReportingManager` | `AlmApiClient` | remontée des résultats vers ALM (D13) |

Les impls `internal` seront obtenues via des **factories publiques** (créées à l'étape 6 de la roadmap),
jamais par `new` direct côté consommateur.

Sources : [qa-socle/src/main/java/com/example/qa/](qa-socle/src/main/java/com/example/qa/).
Le contenu réel (signatures puis implémentations) sera intégré selon la
[feuille de route](qa-socle/docs/roadmap.md).

## Convention de nommage des résultats de tests

Dossier racine : `target/qa-results/`. Un dossier n'est créé **que pour un test KO**
(aucun artefact si le test est OK) et contient **toujours ces trois fichiers** :

```
KO__{ENV}__{NomDuTest}__{yyyy-MM-dd_HH-mm-ss}/
    ERROR_{ENV}_{timestamp}.log     # erreur synthétique : uniquement la cause directe
    FAIL_{ENV}_{timestamp}.log      # trace complète : stacktrace + historique de toutes les steps
    {nomDeLaStepEnErreur}.html      # dump du DOM au moment de l'échec
```

- `ENV` en CAPITALES (ex. `RECETTE`, `INTEG`).
- `NomDuTest` dans le nom du dossier garantit l'unicité en exécution parallèle (un dossier par test KO).
- Fonctionne en parallèle (local) et en CI (artefacts téléchargeables et traitables localement).
- Ces dossiers sont la matière première de l'agent de maintenance (`qa-maintenance`).

## Documentation

- [Décisions d'architecture actées](qa-socle/docs/decisions.md) (D1–D13)
- [Feuille de route de construction](qa-socle/docs/roadmap.md) (10 étapes)
- [Gouvernance Git flow](qa-socle/docs/git-flow.md) (`master` + `develop` + branches de travail)

## Assistance Copilot (annexe)

`.github/` contient la configuration **GitHub Copilot** (5 agents, 13 skills) qui assiste l'équipe
sur ce socle et les projets de tests. Elle est **indépendante du socle** : elle a vocation à être
**externalisée dans son propre dépôt** et importée séparément. Elle vit ici temporairement, le temps
de la construction.

| Agents | Skills |
|---|---|
| [`qa-test-author`](.github/agents/qa-test-author.agent.md) · [`qa-maintenance`](.github/agents/qa-maintenance.agent.md) · [`socle-framework-builder`](.github/agents/socle-framework-builder.agent.md) · [`cicd-build-engineer`](.github/agents/cicd-build-engineer.agent.md) · [`qa-tech-lead-refonte`](.github/agents/qa-tech-lead-refonte.agent.md) | conventions POM, locators/WebSync, Maven local, Artifactory, Git flow, triage Serenity, data Excel/CSV, diagnostic flaky, auto-fix locator, classification d'échecs, suivi de campagne, config VS Code, intégration socle — détail sous [.github/skills/](.github/skills/) |
