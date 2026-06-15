# Socle QA — `qa-parent` / `qa-socle`

Socle d'outils QA custom pour la stack **Selenium Java / Serenity BDD / JUnit 5 / Maven**,
consommé **comme dépendance Maven** par les ~17 projets de tests de l'équipe
(Git/Bitbucket, Artifactory JFrog, CI Jenkins gérée par une autre équipe).

> **Statut : squelette en construction.** Les signatures sont en cours de stabilisation ; les corps
> restent volontairement vides ou neutres avant l'étape d'implémentation.

## Sommaire

- [Structure du dépôt](#structure-du-dépôt)
- [Build](#build)
- [Adoption (personnalisation)](#adoption-personnalisation)
- [Stack & contexte](#stack--contexte)
- [Architecture du socle](#architecture-du-socle)
- [Convention de nommage des résultats de tests](#convention-de-nommage-des-résultats-de-tests)
- [Documentation](#documentation)
- [Assistance Copilot (annexe)](#assistance-copilot-annexe)

## Structure du dépôt

```
pom.xml         # Parent POM (qa-parent) : centralise versions (dépendances + plugins), agrégateur
qa-socle/       # La librairie socle (jar) : WebSync, DataFileManager, TestFailureManager...
  └── docs/     # Décisions d'architecture, feuille de route, gouvernance Git
.github/        # CI suivie ponctuellement ; configs assistants locales ignorées/externalisées
```

## Build

```bash
mvn clean test    # depuis la racine du dépôt
```

Le POM racine (`qa-parent`) est l'agrégateur : il builde l'ensemble du socle. `qa-socle` en hérite
(aucune version déclarée dans les modules, tout est piloté par le `dependencyManagement` du parent).

## Adoption (personnalisation)

`qa-template` est un **squelette réutilisable** : le base package et le `groupId` Maven
`com.example.qa` sont des **placeholders volontaires** (cf. [D17](qa-socle/docs/decisions.md)), à
personnaliser **au clone**, avant tout gel d'API ou release. Trois gestes, une fois :

1. **Renommer le base package** `com.example.qa` → `<votre.package>` via le refactor IDE
   *« Rename package »* (met à jour déclarations + imports + dossiers, et le fichier
   `META-INF/services/...` à venir).
2. **Changer le `groupId`** dans les 2 POM : `pom.xml` (`<groupId>`) et `qa-socle/pom.xml`
   (`<parent><groupId>`).
3. **Rien d'autre** : les configs runtime du socle sont *package-agnostiques* par design
   (le log d'action écrit sous un namespace stable `"qa"`) → aucune string de resource à toucher.

## Stack & contexte

| Dimension | Choix |
|---|---|
| Langage / outil | Java 17, Selenium, **Serenity BDD** (sans Cucumber/Gherkin) |
| Tests | **JUnit 5**, exécution + debug **locale via Maven** prioritaire |
| Architecture consommateurs | **Page Object Model** (PageObjects + Steps) |
| Build / dépôt | **Maven**, **Git/Bitbucket**, **Artifactory JFrog** (`settings.xml`) |
| CI | **Jenkins** (cible) — géré par une **autre équipe** (collaboration) |
| Exécution | Local (WebDriver direct) prioritaire ; **Selenium Grid/Selenoid** dispo ; **BrowserStack** en attente de licences |
| Versioning | **Parent POM + version épinglée explicite** (une ligne à bumper par projet) — décision **D6-bis** (amende D6 : abandon de `RELEASE`, supprimé en Maven 4) |
| Mobile | **Appium** anticipé (à venir) |

## Architecture du socle

Frontière **`api`** (contrat public consommé par les 17 projets) vs **`internal`** (impls + moteurs,
non contractuels), cf. décision [D15](qa-socle/docs/decisions.md).

Le rôle central de `WebSync` / `MobileSync` n'est pas de réécrire Selenium ou Serenity. Le socle expose
des opérations familières de `WebElement` / `WebElementFacade`, mais chaque méthode doit intégrer la
synchro maison : ré-résolution du `By`, polling, absorption des exceptions transitoires de DOM
(`NoSuchElement`, `StaleElement`, `ElementNotInteractable`, `ElementClickIntercepted`...), puis
délégation à l'opération native Selenium/Serenity quand l'élément est réellement exploitable.

| Domaine | `api` (public) | `internal` (caché) | Rôle |
|---|---|---|---|
| `sync` | `WebSync`, `MobileSync` | `AbstractSyncManager` | synchronisation robuste (fluentWait + flag JS) |
| `data` | `DataFileManager` | `AbstractDataFileManager`, `ExcelFileReaderWriter`, `CsvFileReaderWriter` | données de test Excel/CSV (lecture + écriture) |
| `log` | *(aucun type public — log d'action via SLF4J natif)* | `LogbackConfigurator` *(coquille existante ; resources à venir — D16-bis)* | default Logback (namespace `"qa"`, clé `qa.logger.level`) imposé par la présence du jar, surchargeable ; **pas de façade maison** |
| `failure` | *(hook à venir, étape 6)* | `TestFailureManager` | artefacts d'échec (logs + dump HTML) |
| `secret` | `SecretManager`, `Secret` | `CyberArkApiClient` | récupération de secrets au runtime + valeur sensible avec masquage à implémenter (D12) |
| `reporting` | `ReportingManager` | `AlmApiClient` | remontée des résultats vers ALM (D13) |
| `exception` | `QaToolkitException` + `SyncException` / `DataFileException` / `SecretException` / `ReportingException` | — | hiérarchie d'erreurs **unchecked** (D18) ; traduit les exceptions tierces en conservant la `cause` |

Les impls `internal` sont exposées uniquement via des points d'entrée publics quand c'est nécessaire :
`DataFiles` existe déjà pour `data`; les points d'accès `secret` et `reporting` restent à confirmer
avant gel de l'API.

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

- [Décisions d'architecture actées](qa-socle/docs/decisions.md) (D1–D20, amendées par D6-bis et D16-bis)
- [Feuille de route de construction](qa-socle/docs/roadmap.md) (10 étapes)
- [Gouvernance Git flow](qa-socle/docs/git-flow.md) (`master` + `develop` + branches de travail)

## Assistance Copilot (annexe)

Les configurations assistants/agents ne font pas partie du livrable `qa-socle` : elles sont ignorées
par défaut et destinées à être externalisées. Le dépôt ne doit documenter ici que les éléments suivis
qui participent réellement au build ou à la CI du socle.
