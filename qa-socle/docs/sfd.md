tell# Spécifications Fonctionnelles Détaillées (SFD) — Socle QA

> **Statut du document** : version de référence pour la phase de conception (coquilles typées).
> Décrit les **besoins fonctionnels** du socle tels qu'arrêtés par les décisions d'architecture
> ([decisions.md](decisions.md)) et séquencés par la [roadmap](roadmap.md). Sert de contrat
> fonctionnel pour l'implémentation (étape 8).
>
> **Convention** : chaque besoin porte un identifiant `BF-<DOMAINE>-<n>` traçable. Les points non
> encore tranchés sont marqués **⚠️ Point ouvert**.

---

## 1. Introduction

### 1.1 Objet

Ce document spécifie **ce que le socle QA doit faire** (capacités fonctionnelles exposées aux projets
de test et comportements contractuels internes), indépendamment de la manière dont c'est implémenté.
Il ne décrit pas le code ; il décrit le **contrat fonctionnel** que le code devra respecter.

### 1.2 Périmètre

Le socle est une **librairie Java** (dépendance Maven `qa-socle`) **consommée par ~17 projets de
test** automatisés. Il n'est pas une application exécutable : il est intégré et piloté par les tests
des projets consommateurs (Serenity BDD + JUnit 5, Page Object Model, **sans** Cucumber — D1/D2).

**Dans le périmètre** : synchronisation web/mobile, données de test, secrets, masquage, reporting ALM,
capture d'échec, journalisation, gestion d'erreurs, configuration, gouvernance des versions.

**Hors périmètre** : le pipeline CI Jenkins (géré par une autre équipe — D7), l'agent `qa-maintenance`
(consommateur des artefacts, spécifié à part — D9), les tests métier des projets consommateurs.

### 1.3 Acteurs

| Acteur | Rôle vis-à-vis du socle |
|---|---|
| **Auteur de test QA** | Écrit les PageObjects/tests ; utilise `WebSync`/`MobileSync`, `DataFiles`, `SecretManager`, `ReportingManager`. |
| **Projet consommateur** (×17) | Hérite du Parent POM, dépend du jar `qa-socle` ; fournit sa configuration (`serenity.conf`). |
| **Agent `qa-maintenance`** | Consomme les artefacts d'échec `KO__…` pour la classification et l'auto-correction (D9). |
| **CI / Jenkins** (autre équipe) | Exécute les builds, récupère les artefacts, joue la non-régression (D7). |
| **Mainteneur du socle** | Publie les versions, fait évoluer l'API sous garde-fou de compatibilité. |

### 1.4 Documents de référence

- [decisions.md](decisions.md) — registre des décisions d'architecture (D1…D20, D6-bis, D16-bis).
- [roadmap.md](roadmap.md) — séquence de construction et statut d'avancement.
- [README](../../README.md) — convention de nommage des résultats, adoption.

### 1.5 Glossaire

- **Socle** : la librairie `qa-socle` (et son Parent POM `qa-parent`).
- **Coquille typée** : classe/interface dont les signatures sont figées mais le corps non implémenté.
- **No-leak** : aucun handle d'élément vivant (`WebElement`) n'est exposé hors du socle.
- **Masquage à la source** : un secret n'apparaît jamais en clair, sauf déballage explicite.
- **Activation native** : mécanisme déclenché par la seule présence du jar (ServiceLoader), sans code
  ni annotation côté consommateur.
- **Contrat de sortie** : format des artefacts (`KO__…`) traité comme une API versionnée.

---

## 2. Présentation générale

### 2.1 Architecture fonctionnelle

Deux sous-arbres de visibilité (D15) :

- `com.example.qa.api.<domaine>` — **contrat public** consommé par les 17 projets (interfaces, types
  valeur, façades, exceptions).
- `com.example.qa.internal.<domaine>` — **implémentations et moteurs**, non destinés à l'extension.

Domaines fonctionnels : `sync`, `data`, `secret`, `reporting`, `failure`, `log`, `exception`.

### 2.2 Principes directeurs (transverses)

| Principe | Énoncé | Réf. |
|---|---|---|
| **Neutralité des contrats** | Aucun type spécifique à un fournisseur (CyberArk, ALM, Appium, POI) n'apparaît dans l'API publique. | D4, D12, D13 |
| **No-leak** | Toute interaction passe par un `By` et renvoie une **valeur** ; aucun élément vivant exposé. | D3 |
| **Masquage à la source** | Les valeurs sensibles se masquent d'elles-mêmes ; le clair n'est obtenu que par appel explicite. | D12, D16-bis |
| **Erreurs unchecked** | Toute la hiérarchie d'exceptions est unchecked ; les exceptions tierces *checked* sont traduites à la frontière. | D18 |
| **Activation native** | Capture d'échec et log par défaut s'activent par présence du jar, sans code consommateur. | D16, D16-bis |
| **Configuration 100 % Serenity** | Le socle ne crée aucune couche de config maison ; il lit `serenity.conf`/properties/system props. | D19 |
| **Réutilisation** | On compose Serenity/Selenium/POI plutôt que de les réimplémenter. | D3, D14, D16 |

### 2.3 État d'avancement (rappel)

Signatures (coquilles typées) **figées** pour `sync`, `data`, `secret`, `reporting`, `log`, `exception`.
`failure` reste à cadrer. **Aucune implémentation** à ce stade : tous les corps réels relèvent de
l'étape 8. Cette SFD est le contrat que l'étape 8 devra honorer.

---

## 3. Besoins fonctionnels

### 3.1 Domaine SYNC — Synchronisation et interaction web/mobile

**Objectif** : exposer aux PageObjects une surface familière (proche de `WebElement`/`WebElementFacade`)
mais avec une **synchronisation intégrée à chaque méthode**, robuste aux SPA (Angular/React/Vue).
**Composants** : `WebSync` (`api.sync`), `MobileSync` (`api.sync`), `AbstractSyncManager` (`internal.sync`),
`SwipeDirection` (`api.sync`). **Réf.** : D3, D4, D15, D18.

| ID | Besoin fonctionnel |
|---|---|
| **BF-SYNC-01** | Toute action, lecture ou évaluation d'état rattachée à un élément **doit** passer par le moteur d'attente `fluentWait` ; aucune interaction directe hors de ce moteur. |
| **BF-SYNC-02** | Le moteur `fluentWait` **attend** la satisfaction d'une condition jusqu'à un **timeout**, par **polling court (~250 ms)**, en **absorbant** les exceptions transitoires de re-render (`NoSuchElementException`, `StaleElementReferenceException`, `ElementNotInteractableException`, `ElementClickInterceptedException`). |
| **BF-SYNC-03** | Le socle **doit** injecter un **flag JavaScript** dont la disparition signale un re-render en cours, et continuer à poller l'élément cible jusqu'au timeout. |
| **BF-SYNC-04** | **No-leak** : aucune méthode publique ne **doit** retourner un `WebElement`/`WebElementFacade` vivant. Tout est désigné par `By` et renvoie des valeurs immuables (chaînes, booléens, nombres, géométrie). |
| **BF-SYNC-05** | La recherche imbriquée **doit** se faire via `within(By)` qui renvoie une vue bornée aux descendants (type covariant `WebSync`/`MobileSync`), en ré-résolvant des `By` relatifs (jamais stale). |
| **BF-SYNC-06** | Le socle **doit** offrir les **actions communes** : `click`, `type(By, CharSequence…)`, `typeAndEnter`, `clear`. |
| **BF-SYNC-07** | Le socle **doit** offrir les **lectures communes** : texte (`getText`, `getTextValue`, `getValue`), métadonnées (`getTagName`, `getAttribute`, `getDomAttribute`, `getDomProperty`, `getAriaRole`, `getAccessibleName`). |
| **BF-SYNC-08** | Le socle **doit** offrir les **états booléens** : `isSelected`, `isEnabled`, `isDisabled`, `isDisplayed`, `isCurrentlyVisible`, `isClickable`, `isPresent`, `hasFocus`, `containsText`, `containsOnlyText`, `containsValue`. |
| **BF-SYNC-09** | Le socle **doit** offrir des **assertions d'état** (`shouldBeVisible`, `shouldBePresent`, `shouldContainText`, … et leurs négations) qui **lèvent** en cas d'échec et ne retournent rien (no-leak). |
| **BF-SYNC-10** | Le socle **doit** offrir **géométrie/capture** (`getLocation`, `getSize`, `getRect`, `getScreenshotAs`), **collections sans handle** (`count`, `getTexts`) et **attentes explicites** (`waitUntilVisible/Present/NotVisible/Clickable/Enabled/Disabled`, `waitForElement`, `waitForElementToDisappear`). |
| **BF-SYNC-11** | `WebSync` **doit** ajouter le **spécifique web** : `submit`, `doubleClick`, `contextClick`, `typeAndTab`, `setWindowFocus`, listes `<select>` (sélection/désélection par libellé/valeur/index, lecture des options/sélections), DOM/CSS (`getTextContent`, `getAriaLabel`, `getCssValue`, `hasClass`, `containsElements`). |
| **BF-SYNC-12** | `MobileSync` **doit** ajouter les **gestes mobiles** : tap/doubleTap/longPress, swipe (coordonnées et par `By`+`SwipeDirection`), scroll/scrollTo, pinch/zoom, dragAndDrop ; clavier (`hideKeyboard`, `isKeyboardShown`, `pressKey`) ; orientation (`rotatePortrait/Landscape`, `getOrientation`) ; contexte natif/webview (`switchToNativeContext`, `switchToWebViewContext`, `getCurrentContext`) ; cycle de vie app (`launchApp`, `runAppInBackground`, `resetApp`) ; fichiers (`pushFile`, `pullFile`). |
| **BF-SYNC-13** | Le driver mobile **doit** être obtenu depuis le `WebDriver` Serenity du thread courant et casté **en interne** en `AppiumDriver` : **aucun type Appium** ne **doit** apparaître dans l'API publique. |
| **BF-SYNC-14** | La saisie d'une **valeur sensible** **doit** se faire via `type(By, Secret)` : le clair (`secret.value()`) est saisi dans le DOM, mais seul le rendu **masqué** apparaît dans le log d'action. C'est le **seul** point de déballage d'un `Secret` pour la saisie. |
| **BF-SYNC-15** | Au timeout, l'échec **doit** être traduit en `SyncException` porteuse d'un **message différencié** : « mauvaise page / page non chargée » si l'élément est absent ; « application instable » s'il est présent mais jamais interactable. Ces messages alimentent `TestFailureManager` et l'agent de maintenance (D9). |

**Règles de gestion** : `fluentWait` est écrit **une seule fois** dans l'abstract, `protected`, jamais
réimplémenté par les sous-classes (D3 maj). À l'intérieur, l'action délègue à l'API publique Serenity
`WebElementFacade` (résolue par `By`) pour réutiliser retry intégré, `waitUntil*` et reporting.

**Critères d'acceptation** (étape 8) : un test contre un site bidon valide (a) la réévaluation du
locator après mutation DOM sans changement d'URL, (b) les deux messages différenciés, (c) l'absence de
tout `WebElement` exposé dans l'API.

**⚠️ Point ouvert** : frontière API — `WebSync`/`MobileSync` (`api`) héritent toute leur surface de
`AbstractSyncManager` (`internal`). Décider si ce contrat commun reste `internal` (garde-fou compat
élargi), monte en `api`, ou passe par une façade, **avant le gel** (roadmap étape 6).

---

### 3.2 Domaine DATA — Gestion des fichiers de données

**Objectif** : lire/écrire des jeux de données de test (Excel, CSV) derrière un contrat neutre,
permettant la bascule Excel↔CSV sans toucher au code de test.
**Composants** : `DataFileManager`, `DataFiles` (`api.data`) ; `AbstractDataFileManager`,
`ExcelFileReaderWriter`, `CsvFileReaderWriter` (`internal.data`). **Réf.** : D5, D15, D18.

| ID | Besoin fonctionnel |
|---|---|
| **BF-DATA-01** | Le consommateur **doit** dépendre uniquement de l'interface `DataFileManager` ; les implémentations concrètes restent internes. |
| **BF-DATA-02** | Le socle **doit** modéliser une ligne en `Map<String,String>` (clés = en-têtes de colonnes) et un jeu en `List<Map<String,String>>` **en préservant l'ordre des lignes**. |
| **BF-DATA-03** | Le socle **doit** offrir : `readRows()` (source par défaut), `readRows(String source)` (feuille nommée), `getValue(int rowIndex, String column)` (accès ciblé), `writeRows(List…)` et `writeRows(String source, List…)`. |
| **BF-DATA-04** | `getValue` **doit** être 0-based (hors ligne d'en-têtes) et **ne jamais** renvoyer `null` (cellule vide → chaîne vide). |
| **BF-DATA-05** | L'instanciation **doit** passer exclusivement par la factory `DataFiles` : `excel(Path)`, `excel(Path, String password)`, `csv(Path)`, `csv(Path, char delimiter)`. Aucun `new` direct sur une impl. |
| **BF-DATA-06** | Le format **Excel** (Apache POI) **doit** gérer les classeurs `.xlsx`, y compris **chiffrés** (ouverts par mot de passe), et la notion de **feuille** comme « source nommée ». |
| **BF-DATA-07** | Le format **CSV** **doit** gérer le texte délimité (séparateur par défaut ou explicite) ; le paramètre `source` (feuille) **doit** être ignoré sans erreur. |
| **BF-DATA-08** | Toute défaillance (fichier introuvable, colonne absente, format invalide, classeur chiffré illisible, index hors bornes) **doit** être levée en `DataFileException` **unchecked**, l'exception bas-niveau (POI/IO) conservée en `cause`. |
| **BF-DATA-09** | Le modèle **doit** être directement consommable par `@ParameterizedTest`/`@MethodSource` (JUnit 5). |

**Règles de gestion** : code commun (mapping, en-têtes, validation, accès ciblé) dans
`AbstractDataFileManager` ; chaque format ne fournit que la lecture/écriture **brute**
(`readRawRows`/`writeRawRows`). Les tests dépendent de l'interface, jamais de l'impl (D5).

**Critères d'acceptation** (étape 8) : fixtures de fichiers (dont un `.xlsx` chiffré) ; un même test
passe en basculant `DataFiles.excel(...)` ↔ `DataFiles.csv(...)` sans autre modification.

---

### 3.3 Domaine SECRET — Récupération des secrets

**Objectif** : récupérer des credentials (mots de passe, tokens) via un contrat neutre, pour absorber
un changement d'outil de gestion des secrets sans impacter les projets appelants.
**Composants** : `SecretManager` (`api.secret`) ; `CyberArkApiClient` (`internal.secret`). **Réf.** :
D12, D15, D18, D19.

| ID | Besoin fonctionnel |
|---|---|
| **BF-SEC-01** | Le socle **doit** exposer une interface neutre `SecretManager` comme **seule** dépendance des appelants, sans aucun type spécifique au fournisseur. |
| **BF-SEC-02** | `SecretManager` **doit** offrir `Secret getSecret(String name)` (nom logique, cas courant) et `Secret getSecret(String safe, String object)` (cas multi-coffre piloté depuis le code). |
| **BF-SEC-03** | Toute récupération **doit** retourner un `Secret` (jamais un `String` nu), afin que la valeur soit **masquée par défaut** partout (cf. domaine MASK). |
| **BF-SEC-04** | Les **coordonnées d'infrastructure** du fournisseur (CyberArk AppID/Safe/Folder) **doivent** provenir de la **configuration** (`serenity.conf`, D19), résolues par l'implémentation ; le code de test ne fournit que ce qui **identifie** le secret. |
| **BF-SEC-05** | L'implémentation `CyberArkApiClient` **doit** reconstruire l'URL de l'API REST CyberArk depuis les paramètres, appeler l'API HTTP, et extraire le mot de passe de la charge JSON retournée. |
| **BF-SEC-06** | Changer de fournisseur de secrets **doit** se limiter à une nouvelle implémentation + une ligne d'injection, **zéro changement** chez les appelants. |

**Règles de gestion** : composant **singleton, stateless par requête**, thread-safe (D19). L'objet
requête (`SecretRequest`) a été abandonné (D12 maj) : l'identification par `name` ou `(safe, object)`
suffit. Erreurs traduites en `SecretException` **sans jamais exposer le secret** dans le message (D18).

**⚠️ Point ouvert** : factory publique `secret` — confirmer si un point d'accès `api` est nécessaire
pour masquer l'impl `internal`, ou si l'injection suffit (roadmap étape 6, D15).

---

### 3.4 Domaine MASK — Masquage des valeurs sensibles

**Objectif** : garantir qu'**aucun secret n'apparaît en clair** dans les logs, les `TestStep` Serenity,
les dumps d'échec ou par concaténation accidentelle. **Risque OWASP** isolé (roadmap étape 7).
**Composants** : `Secret` (`api.secret`) ; application par `TestFailureManager` et `WebSync`/`MobileSync`.
**Réf.** : D12, D14, D16-bis.

| ID | Besoin fonctionnel |
|---|---|
| **BF-MASK-01** | `Secret` **doit** être un type **immuable** encapsulant une valeur sensible, créé via `Secret.of(String)`. |
| **BF-MASK-02** | La valeur en clair **doit** n'être accessible que par `value()`, appelé **explicitement** (typiquement la saisie d'un champ). Tout appel à `value()` est un point sensible identifié. |
| **BF-MASK-03** | `toString()` **doit** renvoyer **toujours** le rendu masqué : un `Secret` loggué/concaténé par erreur ne **doit** jamais fuiter le clair. |
| **BF-MASK-04** | Le rendu masqué (`masked()`) **doit** permettre de **diagnostiquer/comparer** deux secrets **sans** reconstituer la valeur, via un préfixe visible + un hash court. `sha256Prefix()` expose le préfixe de hash seul. |
| **BF-MASK-05** | Le masquage **doit** être appliqué **en amont de toute écriture**. `TestFailureManager` **doit** appliquer le masquage **à la sérialisation** des `TestStep` (jamais depuis une sortie SLF4J brute). |
| **BF-MASK-06** | **Aucun secret en clair** ne **doit** apparaître dans les 3 artefacts `KO__…` ni dans le log live. |

**Format de masquage (acté, D12)** : « **2 premiers caractères en clair + masque fixe + 16 hexa de
SHA-256** » (ex. `Bo******** (sha256:0a1b2c3d4e5f6a7b)`). C'est un **contrat de sortie versionné** (D16).
Révéler 2 caractères est un **compromis de sécurité assumé** (risque connu sur les secrets très courts,
jugé acceptable ; la comparaison stricte passe par le préfixe SHA-256).

**Critères d'acceptation** (étape 7/8) : test prouvant l'absence de toute occurrence en clair du secret
dans `ERROR_*.log`, `FAIL_*.log`, le dump HTML et la sortie console, y compris en cas de log accidentel.

---

### 3.5 Domaine REPORTING — Remontée des résultats dans ALM

**Objectif** : publier l'avancement et le résultat des cas de test dans l'outil de gestion de tests
(ALM OpenText/HP), derrière un contrat neutre. **Composants** : `ReportingManager`, `TestExecutionResult`,
`ExecutionStatus` (`api.reporting`) ; `AlmApiClient` (`internal.reporting`). **Réf.** : D13, D15, D19.

| ID | Besoin fonctionnel |
|---|---|
| **BF-REP-01** | Le socle **doit** exposer une interface neutre `ReportingManager` comme seule dépendance des appelants ; changer d'outil de reporting = nouvelle impl, zéro changement appelant. |
| **BF-REP-02** | Le cycle de publication **doit** comporter **deux appels par test** : `publishStart(String almTestId)` en début (statut « In Progress ») et `publishEnd(TestExecutionResult)` en fin (statut final), **quel que soit** le résultat. |
| **BF-REP-03** | `TestExecutionResult` **doit** être un objet valeur neutre immuable (`almTestId` + `status`), **extensible** (durée, message d'erreur…) sans rompre la signature de `publishEnd`. |
| **BF-REP-04** | `ExecutionStatus` **doit** énumérer `IN_PROGRESS`, `PASSED`, `FAILED`. |
| **BF-REP-05** | La résolution de l'identifiant ALM **doit** être **interne** à `AlmApiClient` : le code de test ne manipule **jamais** de coordonnées ALM brutes. |
| **BF-REP-06** | Le mapping test↔ALM **doit** offrir **deux modes alternatifs** (pilotés par config, jamais cumulés) : **mode annotation** (`@WithTag("alm.testId:1042")` lu par réflexion via `TestAnnotations`) et **mode fichier** (mapping externe nom de classe → coordonnées ALM, sans recompilation). |
| **BF-REP-07** | Les **coordonnées ALM** **doivent** suivre deux profils config : **défaut** (domain/project/test plan/test set dans `serenity.conf`, test instance ID par test) et **tout fichier** (toutes coordonnées dans le fichier ; clés `qa.alm.*` ignorées). |
| **BF-REP-08** | L'**authentification** **doit** lire le login via `qa.alm.login` (`serenity.conf`) et récupérer le **mot de passe via `SecretManager`** (CyberArk) — **jamais** en clair dans la config. |
| **BF-REP-09** | La remontée **doit** être désactivable par `qa.reporting.enabled` (défaut `true`) sans toucher au code. |
| **BF-REP-10** | Chaque appel **doit** être **stateless** : ouverture/fermeture de sa propre session ALM, aucune session partagée entre threads (sûreté en parallèle Serenity). |

**⚠️ Points ouverts** : version ALM cible (~18.4 à confirmer avant impl) ; format du fichier de mapping
(à figer étape 8) ; factory publique `reporting` à confirmer (D15).

---

### 3.6 Domaine FAIL — Capture d'échec et artefacts

**Objectif** : produire automatiquement, pour **chaque test KO**, un jeu d'artefacts de diagnostic
**uniforme sur les 17 projets**, consommé par l'agent `qa-maintenance` et la récupération CI.
**Composant** : `TestFailureManager` (`internal.failure`). **Réf.** : D8, D16, D16-bis, D9.

| ID | Besoin fonctionnel |
|---|---|
| **BF-FAIL-01** | Pour un test **KO uniquement**, le socle **doit** créer un dossier `KO__{ENV}__{NomDuTest}__{ts}/` contenant **toujours** trois fichiers : `ERROR_*.log` (synthétique), `FAIL_*.log` (trace complète) et un **dump HTML** de la step en erreur. **Aucun artefact** pour un test OK. |
| **BF-FAIL-02** | Le mécanisme **doit** s'activer **nativement** par la seule présence du jar, via le **ServiceLoader de JUnit Platform** : `TestFailureManager` (`internal.failure`) implémente lui-même `TestExecutionListener`, déclaré dans `META-INF/services/org.junit.platform.launcher.TestExecutionListener`, auto-enregistré par le `Launcher` que Surefire lance. **Aucun type public, aucune annotation, aucun import** côté consommateur. *(Serenity ne découvre pas les `StepListener` par ServiceLoader — cf. D16 corrigée.)* |
| **BF-FAIL-03** | L'option « annotation » (ex. `@ExtendWith(...)`) est **explicitement rejetée** : une annotation oubliée produirait une absence silencieuse d'artefacts. L'opt-out **doit** se faire par configuration, pas par omission de code. |
| **BF-FAIL-04** | Les contenus **doivent** être bâtis **en Java** à partir du `TestOutcome` Serenity (`getTestSteps()` + `TestStep.getException()`/`getErrorMessage()`), **sans buffer maison**, en appliquant le **masquage** (BF-MASK-05). |
| **BF-FAIL-05** | Le mécanisme **doit** être paramétrable par clés de config **toutes dotées d'une valeur par défaut** (figées en constantes `TestFailureManager`), unifiées sous `qa.failure.artefacts.*` : `.enabled` (`true`, opt-out), `.outputDir` (`target/qa-results`), `.dumpHtml` (`true`). Le `{ENV}` du nommage `KO__` **n'est pas une clé du socle** : il est lu de l'**environnement Serenity actif** (propriété `environment`, D19) — source unique. |
| **BF-FAIL-06** | Le **format de sortie** (emplacement, nommage `KO__…`, trois fichiers, séparation `ERROR_`/`FAIL_`) constitue un **contrat versionné** : son implémentation interne peut changer librement, mais **changer le format est un breaking change** (SemVer). |
| **BF-FAIL-07** | La répartition des responsabilités **doit** être respectée : **Surefire** = exécution/répertoire/parallélisme ; **Serenity** = historique des steps ; **Logback** = log live ; **`TestFailureManager`** = détection KO + collecte + dump HTML + nommage + écriture des 3 fichiers + masquage. |

**Critères d'acceptation** (étape 8) : un test KO produit exactement les 3 fichiers au bon emplacement,
nommés selon D8, masqués, indépendamment de tout `logback.xml` local ; un test OK ne produit rien ;
deux échecs simultanés (parallèle) n'entrent pas en collision (chemins uniques).

**⚠️ Point ouvert (étape 6)** : figer les **noms exacts** des clés + valeurs par défaut et **graver le
contrat de sortie** ; le composant est aujourd'hui une coquille vide (aucun type public à geler).

---

### 3.7 Domaine LOG — Journalisation (log live)

**Objectif** : fournir un **log live uniforme par défaut** (console/fichier pendant le run), activé sans
configuration, surchargeable par projet. **Composant** : `LogbackConfigurator` (`internal.log`).
**Réf.** : D14, D16-bis, D17, D19.

| ID | Besoin fonctionnel |
|---|---|
| **BF-LOG-01** | Le socle **doit** fournir une configuration Logback par défaut, **activée par la seule présence du jar** (`Configurator` via ServiceLoader, chargeant `logback-socle.xml`). |
| **BF-LOG-02** | La config par défaut **doit** cibler le **namespace de log stable `"qa"`**, **indépendant du base package** (D17), avec un niveau réglable par la clé `qa.logger.level` (défaut **`WARN`**, discret). |
| **BF-LOG-03** | L'ordre de résolution **doit** être : `logback-test.xml` → `logback.xml` → **Configurator du socle** → défaut nu. Un `logback.xml` local **gagne** (opt-out explicite) ; le supprimer fait **retomber sur le default du socle**, jamais sur rien. |
| **BF-LOG-04** | Il **ne doit pas** exister de façade de log publique maison (`QaLogger` supprimée). `WebSync`/`MobileSync` **doivent** logguer leurs actions via un `org.slf4j.Logger` natif sous le namespace `"qa"`. |
| **BF-LOG-05** | Le log live **doit** être strictement distinct des artefacts `KO__` (écrits en Java, BF-FAIL-04) : la config Logback n'influence **pas** ces artefacts. |

**Règles de gestion** : initialisation **one-time** au démarrage JVM (D19). Le binding Logback est
fourni en scope `runtime` (transmis à l'exécution sans polluer la compilation des consommateurs).

**⚠️ Point ouvert (étape 6)** : arbitrage technique — `LogbackConfigurator` devra implémenter
`ch.qos.logback.classic.spi.Configurator`, ce qui exige Logback en **compile** pour cette classe alors
que le scope actuel est **runtime**. Décider comment compiler le configurator sans exposer Logback aux
consommateurs (cf. D16-bis), **avant** d'implémenter (resources `logback-socle.xml` +
`META-INF/services/...` créées en étape 8).

---

### 3.8 Domaine ERR — Gestion transverse des erreurs

**Objectif** : une hiérarchie d'exceptions maison **unchecked**, support des signatures publiques et de
l'outillage de maintenance. **Composants** : `QaToolkitException` + `SyncException`, `DataFileException`,
`SecretException`, `ReportingException` (`api.exception`). **Réf.** : D18, D9.

| ID | Besoin fonctionnel |
|---|---|
| **BF-ERR-01** | Toutes les exceptions du socle **doivent** être **unchecked** (étendre `RuntimeException` via la racine), pour ne pas polluer le code de test de `throws`/`try-catch`. |
| **BF-ERR-02** | La racine **doit** être `QaToolkitException` **concrète** : un seul `catch (QaToolkitException)` attrape **toute** erreur d'origine socle (clé pour l'outillage de maintenance D9). |
| **BF-ERR-03** | Le socle **doit** offrir **4 sous-types par domaine** (`SyncException`, `DataFileException`, `SecretException`, `ReportingException`) et **aucun** sous-type par *nature* d'erreur (la nature voyage dans le **message + la `cause`**). |
| **BF-ERR-04** | Les exceptions tierces ***checked*** (I/O fichier, HTTP) **doivent** être **converties en unchecked à la frontière** (exception translation), l'originale conservée en **`cause`** (stacktrace complète dans `FAIL_*.log`). |
| **BF-ERR-05** | Chaque exception **doit** offrir les constructeurs `(String message)` et `(String message, Throwable cause)`. |
| **BF-ERR-06** | `SyncException` **doit** déduire son message différencié de la `cause` du `TimeoutException` Selenium (dernière exception ignorée) — cf. BF-SYNC-15. |
| **BF-ERR-07** | Le message d'une `SecretException` **ne doit jamais** contenir de secret en clair. |

**Politique des clauses `throws` (tranchée, D18)** : les exceptions étant unchecked, **aucune signature
ne déclare `throws`** ; les exceptions sont documentées en `@throws` Javadoc uniquement. Les
`throws SyncException`/`DataFileException` qui subsistaient sur `sync`/`data` ont été retirés (idiome
standard Selenium/Serenity/JUnit).

---

### 3.9 Domaine CONF — Configuration

**Objectif** : un socle **agnostique du fichier de config**, lisant 100 % via Serenity, toutes les clés
ayant un défaut. **Réf.** : D19, D16, D13.

| ID | Besoin fonctionnel |
|---|---|
| **BF-CONF-01** | Le socle **ne doit créer aucune couche de config maison** : il lit ses clés via l'API de config Serenity (`EnvironmentSpecificConfiguration` / system properties), qui fusionne `serenity.conf` + `serenity.properties` + system props. |
| **BF-CONF-02** | Toute clé propre au socle **doit** avoir une **valeur par défaut** : si le consommateur ne configure rien, le socle fonctionne. |
| **BF-CONF-03** | Les clés **doivent** fonctionner indifféremment depuis `serenity.conf` **ou** `serenity.properties`, selon la précédence Serenity (system props > `serenity.conf` > `serenity.properties`, **à confirmer** contre Serenity 4.2.22). |
| **BF-CONF-04** | Le registre des clés du socle comprend au moins : `qa.failure.artefacts.{enabled,outputDir,dumpHtml}`, `qa.reporting.enabled`, `qa.alm.*` + `qa.alm.login`, `qa.logger.level`, `qa.archunit.basePackage`. Le `{ENV}` du nommage `KO__` n'est pas une clé socle (lu de l'`environment` Serenity, D19). |

---

### 3.10 Domaine VER — Versioning & gouvernance des dépendances

**Objectif** : un versioning **imposé par le socle, centralisé, reproductible et compatible Maven 4**.
**Composants** : `qa-parent` (Parent POM), Maven Wrapper, `maven-enforcer-plugin`. **Réf.** : D6, D6-bis,
D7, roadmap étapes 2 et 10.

| ID | Besoin fonctionnel |
|---|---|
| **BF-VER-01** | Les projets consommateurs **doivent** hériter du **Parent POM `qa-parent`** avec une **version épinglée explicite** ; ils ne gèrent **aucune** version (dépendance ou plugin) eux-mêmes. |
| **BF-VER-02** | Les métaversions `RELEASE`/`LATEST` **doivent** être **interdites** (supprimées en Maven 4) ; une montée de version = **une ligne** à changer par projet, idéalement par outil (`versions:update-parent` / Renovate / Dependabot). |
| **BF-VER-03** | Tous les builds **doivent** utiliser **exactement Maven 3.9.9** via le **Wrapper** (`./mvnw`), indépendamment du Maven global. |
| **BF-VER-04** | L'**enforcer** **doit** imposer : `requireMavenVersion [3.9.9,5.0.0)`, `requireJavaVersion [17,)`, `banDynamicVersions` (snapshots et ranges tolérés), `bannedDependencies` (un **seul** binding SLF4J : Logback). |
| **BF-VER-05** | Le socle **doit** imposer les versions de **Selenium et Serenity** (non négociables côté consommateur) et les transmettre en scope `compile` (réexport, y compris JUnit 5). |
| **BF-VER-06** | Le versioning **doit** être **SemVer strict** : jamais de breaking change en mineur/patch, ni sur l'API publique, ni sur le **contrat de sortie** (BF-FAIL-06). |
| **BF-VER-07** | À partir de la 1ʳᵉ release, un **garde-fou de compatibilité** (japicmp/revapi) **doit** faire échouer le build sur breaking change non intentionnel, baseline = dernière version publiée (périmètre = packages `api`). |
| **BF-VER-08** | Un **mécanisme d'échappement** **doit** permettre de figer ponctuellement un projet (non-merge de la PR de bump / `git revert`). |

**⚠️ Points ouverts (étape 2, pour clôture)** : règle de dépréciation (`@Deprecated` + maintien sur
**N** versions — fixer N) ; **qui** publie une version et **sur quel critère** ; **comment** la
non-régression **bloque** la release (gate, lié à la CI Jenkins D7).

---

## 4. Exigences non-fonctionnelles (rappel fonctionnellement structurant)

| ID | Exigence | Réf. |
|---|---|---|
| **ENF-01 — Thread-safety** | Aucun `static` mutable partagé (`static` ⇒ `final` + immuable/thread-safe/`ThreadLocal`) ; confinement du driver au thread courant ; chemins d'artefacts uniques. Portées par composant : `WebSync`/`MobileSync` par instance (driver du thread) ; `DataFileManager` par usage ; `SecretManager`/`ReportingManager` singleton stateless ; `TestFailureManager` sans état. | D19 |
| **ENF-02 — Garde-fou thread-safety** | Règle **ArchUnit** « pas de champ `static` non-`final` » embarquée dans le jar `qa-socle`, imposée aux 17 consommateurs via Surefire `dependenciesToScan` + `qa.archunit.basePackage` dans le Parent POM. | D19 (option α) |
| **ENF-03 — Neutralité de l'adoption** | Le base package `com.example.qa` et le `groupId` sont des **placeholders** personnalisés à l'adoption (1 refactor IDE + 2 `<groupId>`) ; les configs runtime (`"qa"`, `serenity.conf`) sont **package-agnostiques** et ne changent pas à l'adoption. | D17 |
| **ENF-04 — Compatibilité CI** | Le socle et ses conventions restent compatibles avec la config Jenkins (autre équipe) ; en cas de divergence, compromis. | D7 |
| **ENF-05 — Robustesse synchro** | Le polling `fluentWait` (~250 ms, timeout configurable) absorbe les exceptions transitoires sans faire échouer prématurément le test. | D3 |

---

## 5. Stratégie de validation (besoins de test du socle)

| ID | Besoin | Réf. |
|---|---|---|
| **BF-VAL-01** | Le test principal **doit** être un **projet consommateur dédié** exerçant le socle contre un **site web bidon** (vrai navigateur : absences d'éléments, locators changés, timeouts → valide messages différenciés, artefacts `KO__`, masquage). | D20 |
| **BF-VAL-02** | Les clients HTTP (`CyberArkApiClient`/`AlmApiClient`) **doivent** être testés via **WireMock** (pas de serveur réel). | D20 |
| **BF-VAL-03** | Les formats data (`Excel`/`CSV`) **doivent** être testés sur **fixtures** de fichiers (dont un `.xlsx` chiffré). | D20 |
| **BF-VAL-04** | `TestFailureManager` **doit** être validé sur la production des 3 artefacts + masquage depuis un `TestOutcome` Serenity. | D20 |
| **BF-VAL-05** | La **non-régression CI** = tests unitaires socle + projet consommateur dédié, exécutés contre **chaque version avant publication** (pas de dispositif isolé). | D20, D6 |

---

## 6. Matrice de traçabilité (synthèse)

| Domaine | Besoins | Composants | Décisions | Étape roadmap | État signatures |
|---|---|---|---|---|---|
| SYNC | BF-SYNC-01…15 | `WebSync`, `MobileSync`, `AbstractSyncManager`, `SwipeDirection` | D3, D4 | 6 → 8 | ✅ figées |
| DATA | BF-DATA-01…09 | `DataFileManager`, `DataFiles`, `Abstract…`, `Excel`/`Csv…` | D5 | 6 → 8 | ✅ figées |
| SECRET | BF-SEC-01…06 | `SecretManager`, `CyberArkApiClient` | D12 | 6 → 8 | ✅ figées |
| MASK | BF-MASK-01…06 | `Secret` (+ `TestFailureManager`) | D12, D14, D16-bis | 6/7 → 8 | ✅ signatures + format acté (D12) |
| REPORTING | BF-REP-01…10 | `ReportingManager`, `TestExecutionResult`, `ExecutionStatus`, `AlmApiClient` | D13 | 6 → 8 | ✅ figées |
| FAIL | BF-FAIL-01…07 | `TestFailureManager` | D8, D16, D16-bis | 6 → 8 | ⚠️ à cadrer |
| LOG | BF-LOG-01…05 | `LogbackConfigurator` | D14, D16-bis, D17 | 6 → 8 | ✅ constantes / ⚠️ arbitrage scope |
| ERR | BF-ERR-01…07 | `QaToolkitException` + 4 sous-types | D18 | 3 (✅) | ✅ figées (sans `throws`) |
| CONF | BF-CONF-01…04 | (transverse, Serenity) | D19 | 4 (✅) → 8 | n/a |
| VER | BF-VER-01…08 | `qa-parent`, Wrapper, Enforcer | D6, D6-bis, D7 | 2, 10 | 🟡 partiel |
| VAL | BF-VAL-01…05 | projet consommateur, WireMock, fixtures | D20 | 5 (✅) → 8/9 | n/a |

---

## 7. Points ouverts consolidés (à clore avant gel / implémentation)

1. **Frontière API `sync`** (BF-SYNC) — `AbstractSyncManager` `internal` vs surface publique.
2. **Arbitrage scope Logback** (BF-LOG) — `compile` vs `runtime` pour le `Configurator`.
3. **Contrat de sortie `failure`** (BF-FAIL-06) — graver le format `KO__` comme contrat versionné
   (les clés `qa.failure.artefacts.*` sont, elles, déjà figées).
4. **Factories `secret`/`reporting`** (BF-SEC, BF-REP) — confirmer ou écarter.
5. **Gouvernance versioning** (BF-VER) — N de dépréciation, critère de publication, gate de release.
6. **Confirmations externes** — précédence config Serenity 4.2.22, version ALM (~18.4), format du
   fichier de mapping ALM.
