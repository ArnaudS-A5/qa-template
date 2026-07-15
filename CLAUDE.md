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

### Convention de visibilité api / internal (D15)

Toute la base de code est sous `com.example.qa` et se divise en deux sous-arbres :

- **`api.<domaine>`** — contrat public, gelé depuis 2026-06-24. Ce que les 17 projets importent. Périmètre du garde-fou japicmp (étape 10).
- **`internal.<domaine>`** — implémentations, moteurs, listeners. Jamais référencé par le consommateur. Libre de changer *sauf* les méthodes publiques héritées par `WebSync`/`MobileSync` (exception explicitée dans D15).

### Domaines fonctionnels

| Domaine | Accès public (`api`) | Interne (`internal`) | Activation |
|---|---|---|---|
| `sync` | `WebSync`, `MobileSync`, `SwipeDirection`, `SyncTimeout` | `AbstractSyncManager`, `SyncCondition` | Instance créée par le consommateur (`new WebSync()` = profil MEDIUM) |
| `data` | `DataFileManager` (interface), `DataFiles` (factory) | `AbstractDataFileManager`, `ExcelFileReaderWriter`, `CsvFileReaderWriter` | Via factory |
| `secret` | `SecretManager` (interface), `SecretManagers` (factory), `Secret` | `CyberArkApiClient` | Via factory |
| `failure` | *(aucun)* | `TestFailureManager`, `RawDomCaptureListener` | **Auto** — ServiceLoader (présence du jar) |
| `assertion` | *(aucun)* | `SoftAssertions`, `SoftAssertionExtension` | **Auto** — ServiceLoader Jupiter + autodetection (Parent POM) |
| `reporting` | *(aucun)* | `ReportingManager`, `AlmApiClient`, `TestExecutionReport`, `ExecutionStatus` | **Auto** — ServiceLoader |
| `log` | *(aucun)* | `LogbackConfigurator` | **Auto** — ServiceLoader Logback |
| `exception` | `QaToolkitException` + 4 sous-types domaine | — | Levées par le socle |

### Activation native (ServiceLoader)

Trois services sont auto-découverts par la simple présence du jar `qa-socle` — aucune annotation côté consommateur :

1. `META-INF/services/org.junit.platform.launcher.TestExecutionListener` → `TestFailureManager` (écrit les artefacts `KO__` à l'échec)
2. `META-INF/services/net.thucydides.model.steps.StepListener` → `RawDomCaptureListener` (capture `driver.getPageSource()` au moment du `stepFailed`, mémorisé en `ThreadLocal`)
3. `META-INF/services/org.junit.jupiter.api.extension.Extension` → `SoftAssertionExtension` (draine le collecteur soft-assert en fin de test et fait échouer si nécessaire — D22-bis). Nécessite `junit.jupiter.extensions.autodetection.enabled=true`, posé dans la config Surefire du Parent POM (hérité).

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
- **Exceptions unchecked** : toutes les exceptions du socle étendent `QaToolkitException` (RuntimeException). Les exceptions tierces checked (`IOException`…) sont traduites à la frontière avec `cause` conservée. Aucune clause `throws` dans les signatures publiques.
- **Nommage** : interface = nom du concept (`SecretManager`, `DataFileManager`) ; abstract = préfixe `Abstract` ; impl = nom descriptif du rôle réel (`CyberArkApiClient`, `ExcelFileReaderWriter`). Pas de suffixe `Impl`, pas de préfixe `I`.
- **Masquage** : `Secret#toString()` / `masked()` → 2 premiers caractères + étoiles proportionnelles + 16 hexa SHA-256. Format figé (D12).
- **Configuration** : le socle lit ses clés via l'API Serenity (`EnvironmentSpecificConfiguration`). Précédence : system properties `-D` > `serenity.conf` > `serenity.properties`. Pas de couche de config maison.
- **Thread-safety** : pas de champ `static` mutable partagé. L'état par test est délégué à Serenity (`TestOutcome`/`TestStep`). Les drivers sont confinés au thread courant.

## Git flow

Branches : `master` (stable, taguée aux releases) → `develop` (intégration) → `fix/*` ou `feature/*` (travail quotidien, PR vers `develop`). Les PR vers `main`/`master` sont des releases.

Convention de commit : `<type>(<zone>): <résumé impératif court>` avec types `feat|fix|test|refactor|chore|docs|ci`.

## Statut du projet (2026-07-13)

- **Étapes 1–6 terminées** : structure, versioning, exceptions, config, stratégie de test, signatures gelées (gel API 2026-06-24).
- **Étape 7 en cours** : masquage `Secret` + masquage du DOM (`maskPasswordInputs`, champs `type=password`) implémentés et testés unitairement ; reste la validation « zéro clair » bout-en-bout au pilote (D20).
- **Étape 8 en cours** :
  - **FAILURE** (`TestFailureManager` + `RawDomCaptureListener`) et **infra soft-assert** (`SoftAssertions`/`SoftAssertionExtension`, D22-bis) — faits + testés.
  - **SYNC web** (2026-07-13, cf. D3 maj) — **fait** : moteur `fluentWait(By,Function)`/`poll` (testé), profils `SyncTimeout`, diagnostic actif `SyncCondition`, 78 méthodes, `within` (deux-temps), `type(By,Secret)`, `should…` via `tryAssert` (testé), noms alignés strictement sur Serenity. **Reste (lot 2c)** : flag JS, neutralisation C8 (`serenity.conf`), `pageLoadTimeout`, tests comportementaux au projet `qa-test`. Gestes `MobileSync` différés (mobile hors périmètre).
  - **Restent** : `DataFileManager`, `LogbackConfigurator`, ArchUnit, `CyberArkApiClient`, reporting AUTO.
- **Étapes 9–10** : à faire (pilote, japicmp, release 1.0.0).
- **Points de vigilance non couverts par les décisions** : voir `qa-socle/docs/points-attention.md`.

### Domaine SYNC — repères d'implémentation

- **Profils de timeout** : `new WebSync(SyncTimeout.LONG)` — jamais de durée en dur dans les PageObjects ; valeurs pilotées par `qa.sync.timeout.<profil>`. Deux étages d'attente : page (`readyState`, `qa.sync.pageLoadTimeout`) vs objet (`fluentWait`).
- **Prédicats** (`is…`/`contains…`) = état courant, **ne lèvent jamais** (absent → `false`). **`should…`** = assertion **avec attente** branchée sur le soft-assert (`tryAssert`).
- **`within`** = vue bornée immuable, résolution **deux-temps** (no-leak, localise container vs child).
- **Nommage** : le socle reprend **exactement** le nom Serenity/Selenium appelé (wrapper de synchro).

Le base package `com.example.qa` et le `groupId` sont des placeholders volontaires à remplacer à l'adoption (refactor IDE « Rename package » + 2 `<groupId>` dans les POM).

## Documentation de référence

- `qa-socle/docs/decisions.md` — décisions D1–D22-bis, source de vérité pour tout choix architectural
- `qa-socle/docs/roadmap.md` — 10 étapes avec statut
- `qa-socle/docs/sfd.md` — contrat fonctionnel (`BF-*`)
- `qa-socle/docs/git-flow.md` — gouvernance Git de l'équipe
- `qa-socle/docs/serenity.conf.example` — registre de toutes les clés `qa.*` avec défauts
- `qa-socle/docs/points-attention.md` — points de vigilance (PA-*) non couverts par les décisions
