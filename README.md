# Socle QA — `qa-parent` / `qa-socle`

Socle d'outils QA custom pour la stack **Selenium Java / Serenity BDD / JUnit 5 / Maven**,
consommé **comme dépendance Maven** par les ~17 projets de tests de l'équipe
(Git/Bitbucket, Artifactory JFrog, CI Jenkins gérée par une autre équipe).

> **Statut : API gelée (2026-06-24, packages réorganisés par fonctionnalité le 2026-10-17), implémentation en cours (étape 8).** Sont implémentés et testés : le
> **masquage `Secret`** + DOM, la **capture d'échec** (`TestFailureManager` + DOM brut), l'**infra
> soft-assert** (D22-bis) et tout le **domaine SYNC web** (moteur `fluentWait`, 78 méthodes, profils
> `SyncTimeout`, diagnostic actif, `within`, `should…`/soft-assert — noms alignés sur Serenity), plus
> **`QaPageObject`** (classe de base des Page Objects consommateurs). Le domaine SYNC est **validé au
> navigateur** (campagne `qa-test` du 2026-08-02, Chrome) : profils effectivement appliqués, diagnostics
> `[ABSENT]`/`[DISABLED]` produits, prédicats instantanés, `pageLoadTimeout` posé. Restent :
> `DataFileManager`, `LogbackConfigurator`, `CyberArkApiClient`, reporting AUTO, la **sentinelle flag JS**
> et les garde-fous ArchUnit. Gestes mobiles différés.

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
qa-socle/       # La librairie socle (jar) : un package par fonctionnalité (sync, data, secret, failure...)
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
   `META-INF/services/...`).
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

Un **package par fonctionnalité** (`sync`, `page`, `data`, `secret`, `failure`, `reporting`, `log`), plus la
racine `com.example.qa` pour `QaToolkitException`. La frontière entre ce que les 17 projets utilisent et ce
qui reste interne est portée par les **modificateurs Java** : la façade (`public`) et ses implémentations
(package-private) vivent dans le même package, si bien qu'un projet consommateur **ne peut pas** référencer
une implémentation — il passe par la façade. Cf. décision [D15](qa-socle/docs/decisions.md).

Le rôle central de `WebSync` / `MobileSync` n'est pas de réécrire Selenium ou Serenity. Le socle expose
des opérations familières de `WebElement` / `WebElementFacade`, mais chaque méthode doit intégrer la
synchro maison : ré-résolution du `By`, polling, absorption des exceptions transitoires de DOM
(`NoSuchElement`, `StaleElement`, `ElementNotInteractable`, `ElementClickIntercepted`...), puis
délégation à l'opération native Selenium/Serenity quand l'élément est réellement exploitable.

| Domaine | Contrat public | Caché (package-private) | Rôle |
|---|---|---|---|
| `sync` | `WebSync`, `MobileSync`, `SwipeDirection`, `SyncTimeout`, `SyncException` | `AbstractSyncManager`, `SyncCondition` | synchronisation robuste (`fluentWait`, profils de timeout, diagnostic actif) — **web implémenté et validé au navigateur** |
| `page` | `QaPageObject` | *(aucun)* | classe de base des Page Objects consommateurs : expose `web` (profil `MEDIUM`) et `web(SyncTimeout)` — **héritage**, aucune déclaration à écrire |
| `data` | `DataFileManager`, `DataFiles` (factory), `DataFileException` | `AbstractDataFileManager` (`sealed`), `ExcelFileReaderWriter`, `CsvFileReaderWriter` | données de test Excel/CSV (lecture + écriture) |
| `log` | *(aucun type d'usage — log d'action via SLF4J natif)* | `LogbackConfigurator` *(`public final` car chargé par ServiceLoader ; coquille existante, resources à venir — D16-bis)* | default Logback (namespace `"qa"`, clé `qa.logger.level`) imposé par la présence du jar, surchargeable ; **pas de façade maison** |
| `failure` | *(aucun type d'usage — activation native par présence du jar)* | `TestFailureManager`, `RawDomCaptureListener` *(`public final`, ServiceLoader)* | artefacts d'échec (`ERROR.log`/`FAIL.log` + dump du **DOM brut**, champs password masqués) — **implémenté** |
| soft-assert *(package `sync`)* | *(aucun type d'usage — extension Jupiter auto-détectée, D22-bis)* | `SoftAssertions` ; `SoftAssertionExtension` *(`public final`, ServiceLoader)* | soft-assert (clé `qa.assertions.soft`) : collecte les échecs en mode soft, échec agrégé en fin de test — **infrastructure implémentée** |
| `secret` | `SecretManager`, `SecretManagers` (factory), `Secret`, `SecretException` | `CyberArkApiClient` | récupération de secrets au runtime (`CyberArkApiClient` — coquille) + valeur sensible `Secret` **au masquage implémenté** (D12) |
| `reporting` | `ReportingException` *(aucun type d'usage — reporting AUTO, D13)* | `ReportingManager`, `AlmApiClient`, `TestExecutionReport`, `ExecutionStatus` | remontée des résultats vers ALM (D13) |
| racine | `QaToolkitException` *(abstraite)* | — | hiérarchie d'erreurs **unchecked** (D18) : le consommateur **attrape** les exceptions du socle, seul le socle les lève ; traduit les exceptions tierces en conservant la `cause` |

Les implémentations ne sont exposées que par des points d'entrée publics quand c'est nécessaire :
`DataFiles` pour `data` et **`SecretManagers`** pour `secret` (factories publiques) ; `reporting` est
**AUTO** (listener du socle) → **aucun point d'accès public** (tous ses types d'usage sont package-private,
cf. D13/D15).

Sources : [qa-socle/src/main/java/com/example/qa/](qa-socle/src/main/java/com/example/qa/).
Le contenu réel (signatures puis implémentations) sera intégré selon la
[feuille de route](qa-socle/docs/roadmap.md).

## Convention de nommage des résultats de tests

Dossier racine : `target/qa-results/` (réglable par `qa.failure.artefacts.outputDir` — le pointer **hors
de `target/`** pour survivre à un `mvn clean`). Un dossier n'est créé **que pour un test KO** (aucun
artefact si le test est OK) et contient **toujours ces trois fichiers** :

```
KO__{ENV}__{NomDuTest}__{yyyy-MM-dd_HH-mm-ss}/
    ERROR.log                       # erreur synthétique : uniquement la cause directe
    FAIL.log                        # trace complète : stacktrace + historique de toutes les steps
    {nomDeLaStepEnErreur}.html      # dump du DOM au moment de l'échec
```

- **Contrat de sortie versionné** : ce format (nom de dossier + 3 fichiers) est figé ; le modifier est un
  **breaking change** (SemVer), au même titre que l'API publique — `qa-maintenance` et la collecte CI en dépendent.
- **Délimiteur `__`** (double) entre les champs du dossier : volontaire, car les champs peuvent contenir
  des `_` (noms de méthodes de test, timestamp `…_HH-mm-ss`) → un simple `_` rendrait le re-découpage ambigu.
- `ENV` en CAPITALES (ex. `RECETTE`, `INTEG`) ; les noms de fichiers ne répètent **pas** `ENV`/timestamp
  (déjà portés par le dossier).
- `NomDuTest` dans le nom du dossier garantit l'unicité en exécution parallèle (un dossier par test KO).
- Fonctionne en parallèle (local) et en CI (artefacts téléchargeables et traitables localement).
- Ces dossiers sont la matière première de l'agent de maintenance (`qa-maintenance`).

## Documentation

- [Spécifications fonctionnelles détaillées (SFD)](qa-socle/docs/sfd.md) — contrat fonctionnel (besoins `BF-*`)
- [Décisions d'architecture actées](qa-socle/docs/decisions.md) (D1–D22, amendées par D6-bis, D16-bis et D22-bis)
- [Points d'attention](qa-socle/docs/points-attention.md) — risques et vigilances non couverts par les décisions
- [Feuille de route de construction](qa-socle/docs/roadmap.md) (10 étapes)
- [Gouvernance Git flow](qa-socle/docs/git-flow.md) (`master` + `develop` + branches de travail)
- [Exemple de configuration consommateur](qa-socle/docs/serenity.conf.example) — registre des clés `qa.*` (défauts inclus)
- [Revue du socle](qa-socle/docs/revue-socle.md) — analyse structure/classes + pièges d'implémentation

## Assistance Copilot (annexe)

Les configurations assistants/agents ne font pas partie du livrable `qa-socle` : elles sont ignorées
par défaut et destinées à être externalisées. Le dépôt ne doit documenter ici que les éléments suivis
qui participent réellement au build ou à la CI du socle.
