# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commandes courantes

```bash
# Builder et exécuter tous les tests (depuis la racine)
./mvnw clean test

# Lancer un seul test
./mvnw clean test -pl qa-socle -Dtest=TestFailureManagerTest

# Lancer une seule classe de test avec pattern
./mvnw clean test -pl qa-socle -Dtest="SecretMaskingTest#*"

# Publier sur Artifactory (snapshot)
./mvnw deploy
```

Le Maven Wrapper (`./mvnw`) est obligatoire — il épingle Maven 3.9.9 et évite toute divergence avec Maven 4. L'enforcer vérifie Java 17+ au build.

## Architecture

### Structure multi-module

```
pom.xml        # qa-parent : POM parent + agrégateur ; centralise TOUTES les versions
qa-socle/      # Le jar livré aux ~17 projets consommateurs
  src/main/    # Sources du socle
  src/test/    # Tests unitaires du socle
  docs/        # Décisions (decisions.md), roadmap, SFD, git-flow, serenity.conf.example
```

Les consommateurs déclarent `qa-parent` comme parent POM et ajoutent `qa-socle` comme dépendance — sans déclarer aucune version (tout est dans `dependencyManagement` du parent). La montée de version = bumper `<parent><version>` dans chaque projet consommateur.

### Convention de visibilité : un package par fonctionnalité (D15)

Toute la base de code est sous `com.example.qa`, **un package par fonctionnalité** (`sync`, `page`, `data`, `secret`, `failure`, `reporting`, `log`) plus la racine, qui porte `QaToolkitException`. Il n'y a **plus** de séparation `api` / `internal` : la frontière est portée par les **modificateurs Java**, la façade et ses implémentations vivant dans le même package.

- **`public`** — contrat consommé par les 17 projets (façades `WebSync`/`MobileSync` en `final` : pas de surcharge côté projet, un défaut se corrige dans le socle ; interfaces, factories, `Secret`, exceptions, `QaPageObject`). Gelé depuis 2026-06-24 (noms de packages réorganisés le 2026-10-17, avant la 1.0.0). Périmètre du garde-fou japicmp (étape 10).
- **package-private** — implémentations et moteurs (`AbstractSyncManager`, `CyberArkApiClient`, `AlmApiClient`, `CsvFileReaderWriter`, `SoftAssertions`…). Le compilateur interdit au consommateur de les référencer. Libres de changer, *sauf* les méthodes publiques héritées par `WebSync`/`MobileSync` (exception explicitée dans D15).
- **`public final`, par obligation ServiceLoader** — `TestFailureManager`, `RawDomCaptureListener`, `SoftAssertionExtension`, `LogbackConfigurator` : jamais référencés par le consommateur, leurs membres utilitaires sont package-private.
- **`sealed`** — seulement `AbstractDataFileManager` (permits CSV et Excel) : hors module nommé, `sealed` exige que les sous-classes soient dans le même package.

Ajouter un type : `public` seulement si le consommateur en a besoin, sinon package-private (et `final` si aucune extension n'est prévue).

### Domaines fonctionnels

| Domaine | Contrat public | Caché (package-private) | Activation |
|---|---|---|---|
| `sync` | `WebSync`, `MobileSync`, `SwipeDirection`, `SyncTimeout`, `SyncException` | `AbstractSyncManager`, `SyncCondition` | Instance créée par le consommateur (`new WebSync()` = profil MEDIUM) |
| `page` | `QaPageObject` | *(aucun)* | **Héritage** : le PageObject consommateur étend `QaPageObject` et dispose de `web` |
| `data` | `DataFileManager` (interface), `DataFiles` (factory), `DataFileException` | `AbstractDataFileManager` (`sealed`), `ExcelFileReaderWriter`, `CsvFileReaderWriter` | Via factory |
| `secret` | `SecretManager` (interface), `SecretManagers` (factory), `Secret`, `SecretException` | `CyberArkApiClient` | Via factory |
| `failure` | *(aucun type d'usage)* | `TestFailureManager`, `RawDomCaptureListener` (`public final`, ServiceLoader) | **Auto** — ServiceLoader (présence du jar) |
| soft-assert (package `sync`) | *(aucun type d'usage)* | `SoftAssertions` ; `SoftAssertionExtension` (`public final`, ServiceLoader) | **Auto** — ServiceLoader Jupiter + autodetection (Parent POM) |
| `reporting` | `ReportingException` | `ReportingManager`, `AlmApiClient`, `TestExecutionReport`, `ExecutionStatus` | **Auto** — ServiceLoader |
| `log` | *(aucun type d'usage)* | `LogbackConfigurator` (`public final`, ServiceLoader) | **Auto** — ServiceLoader Logback |
| racine `com.example.qa` | `QaToolkitException` (abstraite) | — | Levée via les sous-types de domaine |

### Activation native (ServiceLoader)

Trois services sont auto-découverts par la simple présence du jar `qa-socle` — aucune annotation côté consommateur :

1. `META-INF/services/org.junit.platform.launcher.TestExecutionListener` → `failure.TestFailureManager` (écrit les artefacts `KO__` à l'échec)
2. `META-INF/services/net.thucydides.model.steps.StepListener` → `failure.RawDomCaptureListener` (capture `driver.getPageSource()` au moment du `stepFailed`, mémorisé en `ThreadLocal`)
3. `META-INF/services/org.junit.jupiter.api.extension.Extension` → `sync.SoftAssertionExtension` (draine le collecteur soft-assert en fin de test et fait échouer si nécessaire — D22-bis). Nécessite `junit.jupiter.extensions.autodetection.enabled=true`, posé dans la config Surefire du Parent POM (hérité).

`TestFailureManager` consomme le DOM capté via `consumeCapturedDom()` dans `executionFinished` (même thread → thread-safe, validé en parallèle), et masque la `value` des `<input type="password">` avant écriture du `.html` (`maskPasswordInputs`).

### Contrat de sortie (artefacts d'échec)

Format versionné au même titre que l'API publique — le modifier est un breaking change :

```
target/qa-results/KO__{ENV}__{NomDuTest}__{yyyy-MM-dd_HH-mm-ss}/
    ERROR.log          # cause directe seulement
    FAIL.log           # stacktrace complète + historique des steps
    {stepEnEchec}.html # DOM brut au moment de l'échec (accents translittérés dans le nom)
```

Le délimiteur `__` (double) est volontaire : les champs contiennent des `_` simples. Configurable via `qa.failure.artefacts.*` dans `serenity.conf`.

### Règles de conception à respecter

- **No-leak** : `WebSync`/`MobileSync` ne retournent jamais de `WebElement`/`WebElementFacade` vivant. Tout passe par `By` et retourne des valeurs. La recherche contextuelle passe par `within(By)`.
- **Exceptions unchecked** : toutes les exceptions du socle étendent `QaToolkitException` (RuntimeException, abstraite). Chaque sous-type est `final`, rangé dans sa fonctionnalité, et ses constructeurs sont package-private : le consommateur les **attrape**, seul le socle les **lève**. Les exceptions tierces checked (`IOException`…) sont traduites à la frontière avec `cause` conservée. Aucune clause `throws` dans les signatures publiques.
- **Nommage** : interface = nom du concept (`SecretManager`, `DataFileManager`) ; abstract = préfixe `Abstract` ; impl = nom descriptif du rôle réel (`CyberArkApiClient`, `ExcelFileReaderWriter`). Pas de suffixe `Impl`, pas de préfixe `I`.
- **Masquage** : `Secret#toString()` / `masked()` → 2 premiers caractères + étoiles proportionnelles + 16 hexa SHA-256. Format figé (D12).
- **Configuration** : le socle lit ses clés via l'API Serenity (`EnvironmentSpecificConfiguration`). Précédence : system properties `-D` > `serenity.conf` > `serenity.properties`. Pas de couche de config maison.
- **Visibilité** : le consommateur n'accède qu'aux types `public` (façades, interfaces, factories) ; tout le reste est package-private (cf. D15). Ne jamais rendre un type `public` « pour les tests » : les tests du socle vivent dans le même package.
- **Thread-safety** : pas de champ `static` mutable partagé. L'état par test est délégué à Serenity (`TestOutcome`/`TestStep`). Les drivers sont confinés au thread courant.

## Git flow

Branches : `master` (stable, taguée aux releases) → `develop` (intégration) → `fix/*` ou `feature/*` (travail quotidien, PR vers `develop`). Les PR vers `main`/`master` sont des releases.

Convention de commit : `<type>(<zone>): <résumé impératif court>` avec types `feat|fix|test|refactor|chore|docs|ci`.

## Statut du projet (2026-10-18)

- **Étapes 1–6 terminées** : structure, versioning, exceptions, config, stratégie de test, signatures gelées (gel API 2026-06-24).
- **Réorganisation par fonctionnalité (2026-10-17, branche `feature/package-by-feature`)** : packages `api`/`internal` remplacés par un package par fonctionnalité, implémentations package-private (D15 réécrite). Les noms complets des types publics ont changé (ex. `com.example.qa.sync.WebSync`) : les imports de `qa-test` sont à mettre à jour. Build vert, 70 tests.
- **Étape 7 en cours** : masquage `Secret` + masquage du DOM (`maskPasswordInputs`, champs `type=password`) implémentés et testés unitairement ; validation « zéro clair » **confirmée au navigateur** (0 occurrence en clair dans les dumps).
- **Étape 8 en cours** :
  - **FAILURE** (`TestFailureManager` + `RawDomCaptureListener`) et **infra soft-assert** (`SoftAssertions`/`SoftAssertionExtension`, package `sync`, D22-bis) — faits + testés.
  - **SYNC web** — **fait et validé au navigateur** (campagne `qa-test` du 2026-08-02) : moteur `poll`, profils `SyncTimeout` **effectivement appliqués**, diagnostic actif `SyncCondition`, 80 méthodes (dont `clickViaJS` / `typeViaJS`, 2026-10-17), `within`, `type(By,Secret)`, `should…` via `tryAssert`. **C8 (« une seule horloge ») réellement atteint** : `NO_FACADE_CLOCK = 1L`, les 8 actions mutantes sur `WebElement` brut via `actRaw`, implicit wait neutralisé via `ConfigurableTimeouts`, garde `requireEnabled`, `classify` élargi aux exceptions Serenity. `pageLoadTimeout` appliqué (marqueur lié au driver).
  - **Écarts T43 / T76** (2026-10-18) : `count` / `getTexts` / `within` sans implicit wait ; `setWindowFocus(By)` attend son élément (`[ABSENT]` au timeout). Couverts par tests unitaires (faux driver `RecordingDriver`), **à revalider dans `qa-test`** (Chrome + Firefox). Build vert, 75 tests.
  - **`QaPageObject`** (package `page`) : classe de base des PageObjects consommateurs.
  - **Restent** : sentinelle **flag JS**, `DataFileManager`, `LogbackConfigurator`, ArchUnit, `CyberArkApiClient`, reporting AUTO, gestes `MobileSync`.
- **Étapes 9–10** : à faire (pilote, japicmp, release 1.0.0).
- **Suivi des correctifs** : `qa-socle/docs/plan-remediation.md` (plan micro, statuts à jour).
- **Points de vigilance non couverts par les décisions** : voir `qa-socle/docs/points-attention.md`.

> ⚠️ Le socle s'appuie sur des **comportements internes non contractuels** de Serenity 4.2.22 (défaut de
> 5 s si timeout ≤ 0, `WithRetries` 12×250 ms compilé en dur, implicit wait propre à `WebDriverFacade`,
> exceptions héritant de `TimeoutException`). **Rejouer la campagne `qa-test` à chaque bump Serenity** —
> cf. PA-15.

### Domaine SYNC — repères d'implémentation

- **Profils de timeout** : `new WebSync(SyncTimeout.LONG)` — jamais de durée en dur dans les PageObjects ; valeurs pilotées par `qa.sync.timeout.<profil>`. Deux étages d'attente : page (`readyState`, `qa.sync.pageLoadTimeout`) vs objet (`fluentWait`).
- **Prédicats** (`is…`/`contains…`) = état courant, **ne lèvent jamais** (absent → `false`) et **instantanés** : tous passent par `query()`, enveloppé dans `withoutImplicitWait`. **`should…`** = assertion **avec attente** branchée sur le soft-assert (`tryAssert`).
- **`within`** = vue bornée immuable, résolution **deux-temps** (no-leak, localise container vs child).
- **Nommage** : le socle reprend **exactement** le nom Serenity/Selenium appelé (wrapper de synchro).
- **Deux chemins d'exécution, une seule horloge (C8)** :
  - **10 actions mutantes** (`click`, `type`×2, `typeAndEnter`, `typeAndTab`, `clear`, `doubleClick`, `contextClick`, `clickViaJS`, `typeViaJS`) → **`actRaw`** sur `WebElement` **brut**. Motif pour les 8 natives : les méthodes correspondantes de la facade passent par `WithRetries` (12×250 ms, **compilé en dur**) = seconde horloge inévitable autrement. Le reporting Serenity est **réémis** (`notifyScreenChange`).
  - **52 autres** (lectures, prédicats, `select…`, géométrie, attentes) → **`WebElementFacade`**, dont l'audit bytecode montre qu'elles ne portent aucun mécanisme d'attente propre.
- **Actions JavaScript** (D3 maj 2026-10-17) : `clickViaJS(By)` et `typeViaJS(By, String)`, pour les composants dont l'action native reste sans effet. Suffixe `ViaJS` (`JS` en majuscules). `typeViaJS` remplace la valeur d'un `input`/`textarea` (setter natif + événements `input` et `change`). Pas de variante `Secret` ; web uniquement.
- **Neutralisation de l'implicit wait** : via **`ConfigurableTimeouts`** (API Serenity) — `WebDriverFacade` maintient le sien **au-dessus** du driver natif ; agir sur `manage().timeouts()` seul est **sans effet** sur la résolution d'élément.
- **`requireEnabled`** : le `WebElement` natif accepte **silencieusement** un clic sur un élément désactivé (faux succès). La garde lève une exception classifiable → `[DISABLED]` au timeout, sur les actions mutantes.

Le base package `com.example.qa` et le `groupId` sont des placeholders volontaires à remplacer à l'adoption (refactor IDE « Rename package » + 2 `<groupId>` dans les POM).

## Documentation de référence

- `qa-socle/docs/decisions.md` — décisions D1–D22-bis, source de vérité pour tout choix architectural
- `qa-socle/docs/roadmap.md` — 10 étapes avec statut
- `qa-socle/docs/sfd.md` — contrat fonctionnel (`BF-*`)
- `qa-socle/docs/git-flow.md` — gouvernance Git de l'équipe
- `qa-socle/docs/serenity.conf.example` — registre de toutes les clés `qa.*` avec défauts
- `qa-socle/docs/points-attention.md` — points de vigilance (PA-*) non couverts par les décisions
