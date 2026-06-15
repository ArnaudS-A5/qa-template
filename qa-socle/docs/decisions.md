# Décisions d'architecture actées

> Registre des décisions prises lors de la conception des agents/skills QA.
> Sert de référence aux agents et de mémoire d'équipe.

## D1 — Tests sans Cucumber/Gherkin

Le socle et les tests sont écrits **en Java pur**, avec **JUnit 5** et **Serenity BDD**,
**sans** Cucumber ni fichiers `.feature`. Architecture **Page Object Model**.

## D2 — JUnit 5 directement (pas de migration)

Le socle part **directement sur JUnit 5**. La migration depuis JUnit 4 se fait à la transition
des projets et reste invisible. Aucune skill de migration n'est nécessaire.

## D3 — Synchronisation : `WebSync` + `fluentWait` + flag JS

- Hiérarchie : **`AbstractSyncManager`** (moteur commun `fluentWait` + flag JS) →
  **`WebSync`** (web, WebDriver) / **`MobileSync`** (mobile, Appium).
- **`fluentWait` = le cœur de la synchro** (pièce maîtresse, pas une simple signature). C'est une
  **vraie méthode d'attente** dont le contrat d'implémentation (étape 8) est :
  - un **timeout** (délai maximal d'attente) ;
  - un **polling court** (~250 ms) ;
  - une **résolution dans le lambda** (la condition/action à satisfaire) ;
  - une **boucle qui ignore** les exceptions transitoires de re-render :
    `NoSuchElementException`, `StaleElementReferenceException`,
    `ElementNotInteractableException`, `ElementClickInterceptedException` *(liste à compléter si
    besoin à l'implémentation)*.
- **Règle absolue** : **toute** action/lecture/état rattaché à un élément (le périmètre `WebElement`)
  passe **systématiquement** par `fluentWait`. Aucune interaction directe hors de ce moteur.
- Méthode complémentaire : **flag JavaScript injecté** (un simple drapeau, pas du code applicatif).
  Sa disparition signale un chargement/re-render en cours ; on poll l'élément cible jusqu'au timeout.
- Justification : SPA Angular/React/Vue avec mutations du DOM **sans** changement d'URL ni de state.
  Attendre l'état stable de l'élément cible en absorbant les exceptions est plus robuste que de
  chasser un spinner/overlay.

### Mise à jour (étape 6) — composition sur Serenity `WebElementFacade`

Acté lors de l'écriture des signatures (étape 6), **sans renier le `fluentWait` maison** :

- **`fluentWait` reste le chef d'orchestre** de la synchro (moteur SPA « relou », testé/approuvé).
  Il vit dans `AbstractSyncManager`, est **`protected`** (hérité, donc plus « privé » comme prévu
  initialement) et **n'est jamais réimplémenté** par les sous-classes — écrit **une seule fois**.
  *État actuel : coquille typée renvoyant `null` ; le corps réel (spec ci-dessus) est l'étape 8.*
- **À l'intérieur** du `fluentWait`, l'action ne manipule **pas** du `WebElement` Selenium brut : elle
  **délègue à l'API publique Serenity `WebElementFacade`** (résolue par `By` via `$(By)` /
  `wrapWebElement`). On **réutilise** ainsi, sans réinventer ni forker : le retry intégré des éléments
  « pas prêts » (`NUMBER_OF_TRIES_FOR_UNREADY_ELEMENTS = 12`), l'absorption de
  `Stale/NoSuch/NotInteractable`, les `waitUntil*`, le chaînage imbriqué et le **reporting Serenity**.
  *Le fork de `WebElementFacadeImpl` a été écarté (dépend de types internes Serenity → couplage fragile).*
- **No-leak (D3)** : **aucune** méthode ne rend de `WebElement`/`WebElementFacade` vivant. Tout passe
  par `By` et renvoie des **valeurs**. La recherche imbriquée (ex-`findElement` contextuel) passe par
  **`within(By)`** (retour covariant `WebSync`/`MobileSync`), qui ré-résout des `By` relatifs (jamais
  stale) — remplace l'exposition d'un handle.
- **Couverture** : `AbstractSyncManager` porte **toute** la surface commune `WebElement` +
  `WebElementFacade`/`WebElementState` (actions, lectures, états, assertions `should*` → `void`,
  géométrie, collections `count`/`getTexts`, attentes). `WebSync` n'ajoute que le **spécifique web**
  (`submit`, double/contextClick, `<select>` HTML, CSS, focus fenêtre…). Les `WebElement`/`Select`
  natifs restent **encapsulés** (pas de `WebElementManager`).

### Messages d'erreur différenciés (alimentent `TestFailureManager` et l'auto-fix)

- Élément **absent** au timeout → « mauvaise page / page non chargée ».
- Élément **présent mais jamais interactable** → « application instable ».

## D4 — Abstraction de la synchronisation (web + mobile)

- `AbstractSyncManager` (moteur `fluentWait` + flag JS **et** tout le contrat commun par `By`) →
  `WebSync` (web) / `MobileSync` (mobile, Appium).
- L'abstraction est justifiée par **deux drivers** (Selenium ↔ Appium) partageant **un moteur et une
  surface d'interaction communs**, écrits **une seule fois** dans l'abstract. Sans le mobile, une
  classe unique aurait suffi (aucune duplication dans une même classe).
- **Mobile pleinement câblé (étape 6)** : c'est possible **sans dépendance POM supplémentaire** —
  `io.appium:java-client` est déjà **transitif `compile` de `serenity-core`** (vérifié via
  `dependency:tree`). Comme **`AppiumDriver implements WebDriver`** et **`AppiumBy extends By`**, toute
  la surface commune fonctionne à l'identique en mobile ; `MobileSync` n'ajoute que les **gestes**
  qu'aucune brique ne couvre (tap/doubleTap/longPress/swipe/scroll/pinch/zoom/dragAndDrop, clavier,
  orientation, contextes natif/webview, cycle de vie app, push/pull de fichiers) + le type neutre
  `SwipeDirection`.
- **Neutralité du contrat (D12/D15)** : le driver mobile est obtenu via le `WebDriver` Serenity du
  thread courant, **casté en interne** en `AppiumDriver` — **aucun type Appium n'apparaît dans l'API
  publique** `MobileSync`.

## D5 — Gestion des fichiers de données : interface + abstract

- `interface DataFileManager` (contrat : `readRows`, `writeRows`, `getValue`).
- `AbstractDataFileManager implements DataFileManager` (code commun : validation des colonnes,
  mapping en `List<Map<String, String>>`, gestion des en-têtes).
- `ExcelFileReaderWriter` (Apache POI) et `CsvFileReaderWriter` (texte) en héritent.
  Suffixe `ReaderWriter` : le contrat couvre lecture **et** écriture, le nom l'assume
  (un simple `Reader` qui écrit serait trompeur).
- Les tests dépendent de l'**interface**, pas de l'implémentation → bascule Excel↔CSV sans toucher au code.

### Règle de décision interface / abstract / classe

- Plusieurs implémentations interchangeables → **interface**.
- Code commun entre elles → **abstract** (qui implémente l'interface).
- Les deux → **interface + abstract** (cas Excel/CSV).
- Une seule implémentation, pas d'alternative prévue → **classe simple** (ex. `QaLogger`,
  `TestFailureManager` aujourd'hui). Une classe simple ne porte PAS le suffixe de contrat
  de façon obligatoire : `TestFailureManager` garde `Manager` parce qu'il décrit bien son
  rôle (orchestrer la production des artefacts), pas par convention d'interface.

> NB : `WebSync` n'est PAS une classe simple — elle étend `AbstractSyncManager` (cas D3/D4 :
> abstract justifiée par l'arrivée du mobile, sans interface car les impls ne sont pas
> interchangeables entre elles).

## D6 — Dépendance & versioning : Parent POM + RELEASE

- Les projets consommateurs **héritent d'un Parent POM** (`qa-parent`).
- La version du socle est référencée en **`RELEASE`** (dernière version stable) → **zéro modification**
  dans les projets lors d'une montée de version.
- Garde-fous **obligatoires** pour compenser la non-reproductibilité du `RELEASE` :
  - **SemVer strict** : jamais de breaking change en mineur/patch.
  - **CI de non-régression** du socle **avant** toute publication de release.
  - **Mécanisme d'échappement** : possibilité de figer ponctuellement un projet sur une version précise en cas d'incident.
- Alternative documentée (non retenue) : **BOM** (1 ligne à bumper par projet) — plus reproductible mais ne supprime pas totalement les modifications.

## D7 — CI Jenkins gérée par une autre équipe

Jenkins est la cible mais piloté par une autre équipe. Pas de skill de pipeline Jenkins dédiée.
Le socle et les conventions doivent rester **compatibles** avec leur configuration ; en cas de divergence, on cherche un compromis.

## D8 — Convention de nommage des résultats

Voir [README](../../README.md#convention-de-nommage-des-résultats-de-tests).
Rappel du contrat : dossier `KO__{ENV}__{NomDuTest}__{ts}/` créé **uniquement** pour un test KO,
contenant **toujours** `ERROR_*.log` (synthétique) + `FAIL_*.log` (trace complète) + dump HTML
de la step en erreur. Aucun artefact pour un test OK.

## D9 — Auto-correction : agent autonome `qa-maintenance`

L'auto-correction est un **agent** (comportement orchestré + état persistant), pas une simple skill.
Il consomme les skills `auto-fix-failed-test`, `failure-classification`, `maintenance-run-tracking`,
`serenity-reporting-triage`, `maven-local-run-debug`.

**Règle absolue** : l'agent ne corrige **que** le cas « locator obsolète ». Toutes les autres causes
donnent lieu à un **rapport**, jamais à une correction silencieuse. Analyse **obligatoire** avant toute
proposition (log + HTML + **toutes** les steps du test).

## D10 — PR : phase 1 = branche + commit + push

L'agent prépare **branche + commit + push** selon le Git flow, mais ne crée **pas** encore la PR/MR
automatiquement (phase ultérieure).

## D11 — Supports de présentation

PPT et supports de formation livrés en **Markdown (Marp)**, convertibles en `.pptx`.
Le Git flow est documenté en Markdown pour être consommable par l'agent.

## D12 — Gestion des secrets : interface neutre + impl CyberArk (à venir)

- **Encapsulation par interface neutre** (règle D5) pour anticiper un changement d'outil de gestion
  des secrets sans impacter les projets appelants :
  - `interface SecretManager` — contrat **neutre** (ex. `String getSecret(SecretRequest request)`),
    seule dépendance des projets appelants.
  - `CyberArkApiClient implements SecretManager` — implémentation concrète : reconstruction de l'URL
    de l'API REST CyberArk à partir des paramètres, appel HTTP, extraction du mot de passe de la
    charge JSON retournée.
  - Objet de requête portant **tous** les paramètres à transmettre au serveur CyberArk
    (AppID, Safe, Folder, Object/Query, ...), probablement via Builder. Nom à confirmer
    (`CyberArkRequest`, et éventuellement un `SecretRequest` neutre côté interface).
- Le contrat de l'interface doit rester **neutre** : ne pas exposer `CyberArkRequest` dans sa signature
  (sinon CyberArk fuit dans le contrat). À trancher à l'implémentation : requête neutre + héritage,
  ou traduction interne.
- **Convention de nommage (état de l'art Java / Selenium / Serenity / JDK)** :
  - **Interface** : **aucun marqueur** (ni préfixe `I`, ni suffixe `Interface`). Elle porte le nom du
    concept pur. Réfs : `WebDriver`, `WebElement` (Selenium), `List`, `Map` (JDK), `DataFileManager` (socle).
    Le préfixe `I` est une convention C#/.NET, **pas** Java.
  - **Classe abstraite** : **préfixe `Abstract`** (`AbstractDataFileManager`, `AbstractList`, `AbstractSyncManager`).
  - **Implémentation** : nom **descriptif/spécifique** par son rôle réel (`CyberArkApiClient`,
    `CsvFileReaderWriter`, `ExcelFileReaderWriter`, `ChromeDriver`).
  - **Ne pas répéter le suffixe thématique de l'interface dans les impls** : le suffixe (`...Manager`)
    marque la **thématique** et reste sur l'interface ; l'impl porte **son propre rôle**
    (`ReaderWriter`, `ApiClient`, ...). Ex. interface `DataFileManager` → impls `ExcelFileReaderWriter` /
    `CsvFileReaderWriter` ; interface `SecretManager` → impl `CyberArkApiClient`. On évite
    `XxxManager` → `YyyManager` redondant.
  - **Suffixe `ApiClient` (pas `Client` seul)** pour les consommateurs d'API distantes
    (`CyberArkApiClient`, `AlmApiClient`) : « Client » seul peut s'entendre comme le côté client
    de l'application (UI) ; `ApiClient` dit exactement ce que fait la classe — consommer l'API
    de l'outil. Précédents : `ApiClient` est le nom standard des classes générées par
    OpenAPI/Swagger, convention répandue côté Java.
  - Lisibilité du type assurée par la paire concept/impl : `SecretManager` (contrat) vs `CyberArkApiClient` (impl).
  - **Pas de suffixe `Impl`** : l'implémentation est nommée par **ce qui la spécialise** (outil/format/navigateur),
    pas par le mot `Impl` (pis-aller à éviter). `CyberArkApiClient` joue le rôle d'`Impl` mais en plus parlant.
    Cas piège : quand l'interface s'appelle déjà `XxxManager`, l'impl prend un **nom de rôle propre**
    (`ApiClient`, `ReaderWriter`, ...), jamais `XxxManagerImpl` ni `XxxManager` répété.
  - **`Strategy` : seulement si c'est vraiment un pattern Strategy** = N implémentations interchangeables
    choisies **au runtime** pour faire la même chose (ex. CyberArk **ou** vault local **ou** env var
    sélectionnés dynamiquement → `SecretRetrievalStrategy` + `CyberArkStrategy` / `EnvVarStrategy`).
    Tant qu'il n'y a **qu'une seule** source prévue (cas actuel), ce n'est PAS un Strategy : on reste
    sur `SecretManager` (interface) + `CyberArkApiClient` (impl).
- Bénéfice : remplacer CyberArk = nouvelle impl + 1 ligne d'injection, **zéro changement** dans les appelants.
- Tests d'intégration manuels déjà faits → mécanisme connu comme fonctionnel ; reste à industrialiser proprement.
- **Squelettes créés** : `SecretManager` (package `api.secret`) + `CyberArkApiClient` (package
  `internal.secret`), cf. D15. Le consommateur ne dépend que de `SecretManager`.
- **Aucune implémentation pour l'instant** (phase backlog / nommage).

## D13 — Remontée des résultats dans ALM (à venir)

- **Interface neutre** (même principe que D12) : `interface ReportingManager` — contrat de remontée
  des résultats vers un outil de gestion de tests, seule dépendance des appelants.
- `AlmApiClient implements ReportingManager` — implémentation concrète qui pousse les résultats
  dans **ALM (OpenText/HP ALM)** via son **API REST**. Changer d'outil de reporting = nouvelle
  impl, zéro changement chez les appelants.
- **Ne pas réinventer les annotations** : réutiliser les métadonnées **Serenity** existantes pour porter
  le mapping cas de test ↔ ALM, en priorité **`@WithTag`** (ex. `@WithTag("alm.testId:1042")`),
  lues par réflexion (`TestAnnotations`). `@Title` complète si besoin.
- Créer une annotation maison (`@AlmTest`) **uniquement si** `@WithTag` est trop limité (plusieurs champs
  structurés : testId + domain + folder...). À challenger à l'implémentation.
- Version ALM cible : **à confirmer** (probablement ~18.4 — l'API varie selon la version, à figer avant impl).
- **Squelettes créés** : `ReportingManager` (package `api.reporting`) + `AlmApiClient` (package
  `internal.reporting`), cf. D15 — remplacent la classe unique `AlmReportingManager` qui mariait
  l'outil au concept. Le consommateur ne dépend que de `ReportingManager`.
- **Aucune implémentation pour l'instant** (phase backlog / nommage).

## D14 — Renommages de classes actés (avant gel de l'API)

Renommages appliqués pendant la phase squelette (coût nul : rien d'implémenté, rien de publié) :

| Avant | Après | Raison |
|---|---|---|
| `Logger` | `QaLogger` | collision d'auto-import avec `org.slf4j.Logger` (sur le classpath, transmis aux 17 projets) et `java.util.logging.Logger` |
| `TestFailManager` | `TestFailureManager` | anglais correct (« failure » = l'échec, « fail » = verbe) |
| `ExcelFileReader` / `CsvFileReader` | `ExcelFileReaderWriter` / `CsvFileReaderWriter` | le contrat `DataFileManager` couvre lecture ET écriture ; un nom `Reader` qui écrit serait trompeur |
| `AlmReportingManager` | `ReportingManager` (interface) + `AlmApiClient` (impl) | l'outil (ALM) fuitait dans le concept (reporting) ; alignement sur le pattern D12 |
| — | `CyberArkApiClient` (au lieu de `CyberArkClient` prévu) | « Client » seul évoque le côté client applicatif (UI) ; `ApiClient` dit le rôle réel : consommer l'API de l'outil |

### Rôle de `QaLogger` (pourquoi une classe à nous au-dessus de SLF4J)

`QaLogger` est une **façade** au-dessus de SLF4J (qui reste le moteur d'écriture via Logback).

**Périmètre précis (recentré, étape 6)** : `QaLogger` n'est **pas** un logger généraliste. C'est le
**logger des actions de synchronisation** : il est utilisé **exclusivement** par les méthodes
encapsulant `WebElement` (`WebSync` / `MobileSync`). Le modèle mental : on a encapsulé les méthodes
`WebElement` pour y intégrer une **synchro custom** (`fluentWait`, D3) **et**, en plus, un **log de
l'action** afin de suivre **en direct dans le terminal** ce qui est exécuté (très pratique au
diagnostic). Pour tout le reste (logs métier hors actions élément), le consommateur utilise le
**`org.slf4j.Logger` classique** — on ne réinvente pas la roue.

**Niveau réglable par config** : le verbiage de ces logs d'action est piloté par la clé
**`qa.logger.level`** (lue via Serenity, D19), **défaut `WARN`**. Le défaut est **discret par
exprès** : ces logs ne doivent **pas polluer le rapport Serenity**, qui apporte déjà ses propres logs
via les steps. On monte à `INFO`/`DEBUG`/`TRACE` pour diagnostiquer. Niveaux exposés **iso-SLF4J**
(`trace`/`debug`/`info`/`warn`/`error`, placeholders `{}`) pour ne pas brider l'outil enveloppé.

**Correction (étape 6) — la source des artefacts d'échec est Serenity, pas un buffer maison** :
l'idée initiale d'un *buffer `ThreadLocal` accumulant les steps* dans `QaLogger` est **abandonnée**.
Vérification faite sur les sources Serenity, l'historique des steps et l'erreur sont **déjà exposés
nativement** au runtime :
- `StepEventBus.getBaseStepListener().getCurrentTestOutcome()` → le **`TestOutcome`** du test courant ;
- `TestOutcome.getTestSteps()` → l'**historique complet** des steps (matière du `FAIL_*.log`) ;
- `TestStep.getException()` (un `FailureCause` avec stacktrace) / `getErrorMessage()` → l'**erreur
  directe** (matière du `ERROR_*.log`).

`TestFailureManager` lit **directement** ces structures (cohérent avec son activation par
`StepListener`, D16) → **pas de `TestLogBuffer`**, on ne duplique pas ce que Serenity conserve déjà
(même principe que la réutilisation de `WebElementFacade` côté synchro).

**Ce qui justifie donc encore la façade `QaLogger`** — deux raisons, **aucune n'étant le buffer** :

1. **Log d'action de synchro en live** (le rôle recentré ci-dessus) : Serenity loggue les *steps*, pas
   les actions `WebSync`/`MobileSync` fines ; ce verbiage réglable (`qa.logger.level`) est notre valeur
   ajoutée, **distincte** du rapport Serenity.
2. **Masquage des secrets** avant toute écriture (roadmap étape 7, OWASP) : impossible à imposer si
   chaque projet appelle SLF4J directement — il faut un **point d'interception**. Aucune **signature
   publique de masquage** sur `QaLogger` (le contrat « valeur sensible » vit sur `SecretManager`) :
   le masquage est un **comportement interne**, en amont de l'écriture du log d'action **et** appliqué
   par `TestFailureManager` lorsqu'il sérialise les `TestStep` Serenity vers les artefacts `KO__`.

> L'**uniformité du format / de la console** n'est PAS une raison de `QaLogger` : elle est assurée par
> la **config Logback partagée** (`LogbackConfigurator`, D16-bis). Sans les besoins ci-dessus (log
> d'action réglable + masquage), `QaLogger` n'aurait pas de raison d'être (on utiliserait SLF4J
> directement).

## D15 — Convention de visibilité : packages `api` / `internal` (roadmap étape 1)

Frontière entre le **contrat public** (consommé par les ~17 projets) et l'**interne** (impls, moteurs).

### Choix actés (arbitrage utilisateur)

- **Mécanisme** : deux sous-arbres `com.example.qa.api.<domaine>` et `com.example.qa.internal.<domaine>`.
  Frontière **contrôlée par outil** (japicmp pour le périmètre — roadmap étape 10 ; ArchUnit possible
  pour interdire les imports `internal` côté test). **Pas de JPMS** : `internal` reste techniquement
  accessible (les 17 projets et Serenity/Selenium ne sont pas modulaires), c'est la convention + l'outil
  qui isolent, sans rien imposer au classpath des consommateurs.
- **Extension** : les consommateurs **utilisent** surtout ; l'extension est rare et encadrée. Donc les
  classes `Abstract*` et les moteurs **ne sont PAS du SPI public** → `internal`.
- **Implémentations** : **cachées derrière une factory** (à créer à l'étape 6). Le consommateur dépend
  des **interfaces** (`api`), jamais des impls concrètes (`internal`).
- **Nommage** : `api` + `internal` uniquement (pas de package `spi`, puisque l'extension n'est pas un
  cas d'usage de premier plan).

### Classement des types (existants et planifiés)

| Type | Package | Pourquoi |
|---|---|---|
| `WebSync`, `MobileSync` | `api.sync` | types **déclarés** dans les PageObjects consommateurs (`private final WebSync`) |
| `DataFileManager` | `api.data` | interface = contrat |
| `SecretManager` | `api.secret` | interface = contrat |
| `ReportingManager` | `api.reporting` | interface = contrat |
| `QaLogger` | `api.log` | façade appelée directement par le consommateur |
| `QaToolkitException` + `SyncException` / `DataFileException` / `SecretException` / `ReportingException` | `api.exception` | hiérarchie d'erreurs **publique** (contrat), unchecked — cf. D18 ; dans le périmètre japicmp (étape 10) |
| `AbstractSyncManager` | `internal.sync` | moteur, extension non prévue |
| `AbstractDataFileManager` | `internal.data` | code commun interne |
| `ExcelFileReaderWriter`, `CsvFileReaderWriter` | `internal.data` | impls, instanciées via factory (étape 6) |
| `CyberArkApiClient` | `internal.secret` | impl cachée derrière `SecretManager` |
| `AlmApiClient` | `internal.reporting` | impl cachée derrière `ReportingManager` |
| `TestFailureManager` | `internal.failure` | moteur activé par un **hook natif auto-découvert** (cf. D16) ; **aucun type public, aucune annotation**, jamais référencé par le consommateur |
| `LogbackConfigurator` *(à venir, étape 6/8)* | `internal.log` | mécanisme Logback **auto-activé** (ServiceLoader), **aucun type public**, jamais référencé par le consommateur (cf. D16-bis) |

### Conséquences

- **Factories publiques** (`api`) à créer à l'étape 6 comme **seul** point d'accès aux impls
  (`data`, `secret`, `reporting`).
- Pour `failure` : **ni factory ni type public**. Activation **native** (ServiceLoader), invisible
  du consommateur — cf. **D16**.
- `WebSync`/`MobileSync` (`api`) étendent `AbstractSyncManager` (`internal`) : dépendance
  `api → internal` **interne au socle**, invisible du consommateur (il ne voit que `WebSync`).
- Le périmètre `api` = exactement ce que japicmp surveillera (étape 10).

## D16 — Capture d'échec : activation native + délégation Logback/Surefire (roadmap étape 6)

Comment `TestFailureManager` (package `internal.failure`) est **déclenché**, **qui porte quelle
part de la mécanique**, et **comment le consommateur le règle**. À implémenter à l'étape 6.

### Activation : native et invisible (option A, sans annotation)

- Le hook s'active par la **seule présence du jar** `qa-socle` au classpath, via **ServiceLoader**
  (fichier `META-INF/services/...` interne au jar). Candidat naturel : un **`StepListener` Serenity**
  (il voit **toutes les steps** → matière du `FAIL_*.log`) ; éventuellement complété d'un
  `TestExecutionListener` JUnit pour le cycle de vie.
- **Rejet explicite de l'option annotation** (ex. `@ExtendWith(QaFailureExtension.class)`) : une
  annotation **oubliée** dans un projet ne produirait **aucun artefact, sans erreur** → personne ne
  comprendrait l'absence des fichiers de sortie. L'activation automatique supprime ce piège.
- Conséquence : **aucun type public**, **aucune annotation**, **aucun `import`** côté consommateur.
  `TestFailureManager` et son listener restent entièrement `internal` (cohérent avec D15).

### Répartition de la mécanique (la classe ne porte PAS tout)

On s'appuie au maximum sur ce qui existe **nativement** dans la stack ; `TestFailureManager` reste un
**orchestrateur mince**.

| Responsabilité | Porté par | Pourquoi |
|---|---|---|
| Écriture fichier, format des lignes, rotation, séparation `ERROR_` vs `FAIL_` | **Logback** (`logback.xml` + appenders/markers/filtres) | c'est le rôle natif de Logback via SLF4J ; ne pas réimplémenter un moteur de log |
| Exécution, répertoire de travail, propriétés système, parallélisme | **Maven Surefire** | c'est lui qui lance les tests et fixe l'environnement |
| Détection de l'échec + collecte des steps + déclenchement du dump HTML + nommage du dossier `KO__` | **`TestFailureManager`** (mince) | logique spécifique au socle, non couverte nativement |
| Accumulation de la trace par test (source du `FAIL_*.log`) | **`QaLogger`** (déjà acté D14) | déjà sa responsabilité |

> Principe : tout ce qui est **configurable nativement** (Logback, Surefire) n'est PAS recodé dans la
> classe. La classe se limite à ce qu'aucun outil ne sait faire à notre place.

### Paramétrage par le consommateur (clés + valeurs par défaut)

Le hook lit des **clés de configuration** depuis **`serenity.conf`** ou les **system properties**
(priorité property > conf > défaut). **Toutes ont une valeur par défaut** → si le consommateur ne
configure rien, ça marche. Exemples indicatifs (noms exacts à figer à l'étape 6) :

| Clé (indicative) | Rôle | Défaut |
|---|---|---|
| `qa.failure.enabled` | activer / désactiver tout le mécanisme (opt-out global) | `true` |
| `qa.failure.outputDir` | racine des dossiers de résultats | `target/qa-results` |
| `qa.failure.dumpHtml` | produire ou non le dump HTML de la step | `true` |
| `qa.failure.env` | valeur `{ENV}` du nommage (`RECETTE`, `INTEG`...) | déduite / `LOCAL` |

> L'**opt-out** (`enabled=false`) est l'échappatoire pour un projet qui ne veut pas du mécanisme,
> sans toucher au code — il remplace le « ne pas mettre l'annotation » de l'option B.

### Contrat de SORTIE = second contrat versionné (⚠️)

L'emplacement, le **nommage** (`KO__{ENV}__{Test}__{ts}/`) et les **3 fichiers** (cf. D8) forment un
**contrat à part entière**, car l'**agent `qa-maintenance`** et la **récupération d'artefacts CI** en
dépendent. Donc :

- l'**implémentation interne** de `TestFailureManager` peut changer librement (zéro impact
  consommateur, grâce à D15 + activation native) ;
- **mais** changer le **format de sortie** est un **breaking change** (SemVer), même si aucune ligne
  de code consommateur ne change. À traiter comme l'API publique vis-à-vis du garde-fou (étape 10).

## D6-bis — Versioning : fin de `RELEASE`, Maven Wrapper + Enforcer

**Amende D6** : remplace le mot-clé `RELEASE` par une **version épinglée explicite, bumée par outil**.

### Rationale

`RELEASE`/`LATEST` sont **dépréciés et supprimés dans Maven 4**. Les utiliser aujourd'hui crée une
bombe à retardement : quand l'org bascule à Maven 4, les 17 consommateurs cassent tous en même temps.
Il faut abandonner `RELEASE` **pendant qu'on choisit le moment**, pas à la date imposée par l'org.

La vraie promesse de D6 n'était pas « zéro modif » (impossible avec reproductibilité), mais
« versioning imposé par le socle, centralisé ». On la tient en passant à une **version épinglée +
montée par outil**, qui est **reproductible, traçable et M4-proof**.

### Décision

- **Abandon de `RELEASE`.**
- **Parent POM version épinglée** : les consommateurs déclarent `<parent>qa-parent</parent>` avec une
  `<version>` explicite (ex. `1.0.0`). Une montée de version du socle = une ligne à changer par projet.
- **Maven Wrapper 3.9.9** : généré via `mvn wrapper:wrapper -Dmaven=3.9.9`. Fichiers `mvnw`/`mvnw.cmd` +
  `.mvn/wrapper/maven-wrapper.properties`. Tous les builds utilisent **exactement** 3.9.9, indépendant
  de Maven global (protège contre divergences et la bascule M4 de l'org).
- **`maven-enforcer-plugin`** dans `qa-parent` (`<build><plugins>`, exécuté par tous) :
  - `requireMavenVersion [3.9.9,5.0.0)` : enforcer la version basse et permettre M4 ;
  - `requireJavaVersion [17,)` : enforcer Java 17 minimum ;
  - `banDynamicVersions` (`allowSnapshots` + `allowRanges`) : bannit les métaversions `RELEASE`/`LATEST`
    (les **seules** supprimées en Maven 4) → réintroduction impossible. Les ranges restent autorisés :
    ils restent valides en Maven 4 et sont imposés transitivement par des tiers (Serenity →
    `io.cucumber:query [12.2.0,13.0.0)`), un ban global serait donc impossible et hors objectif ;
  - `bannedDependencies` : bannir tous les bindings SLF4J sauf Logback (voir D16-bis).

### Workflow de montée de version

Remplace le flottement silencieux de `RELEASE` par :
```bash
mvn versions:update-parent -DnewVersion=X.Y.Z -DgenerateBackupPoms=false
```
ou automatisé via **Renovate/Dependabot** (ouvre PR, CI teste, merge). Bénéfices :
- **Reproductible** : version figée dans git, identique pour tout le monde ;
- **Traçable** : chaque bump est un commit, revertable ;
- **Contrôlé** : montée par repo à ton rythme, pas globale imposée ;
- **M4-safe** : quand l'org bascule, zéro pb (on utilise une version explicite, jamais de métaversion).

### Escape mechanism (D6)

Si un projet doit rester sur une ancienne version du socle : simple non-merge de la PR de bump ou
`git revert` si déjà merged. C'est mille fois plus clair et traçable que d'inventer un override.

---

## D16-bis — Logs : socle maîtrise la sortie, façade maîtrise le masquage

**Amende D16** : le socle ne délègue *pas* à Logback l'écriture des artefacts d'échec ; il les écrit lui-même en Java.

### Correction des scopes

- **Parent POM (dependencyManagement)** : `logback-classic` en `<scope>runtime</scope>`.
- **qa-socle** : déclare `slf4j-api` en `compile` (le contrat SLF4J, transmis aux consommateurs) et
  `logback-classic` en `runtime` (le binding, pour l'exécution, pas polluant la compilation).
- **Enforcer** (D6-bis) : banni les bindings concurrent (slf4j-simple, log4j-slf4j) → un seul Logback.

**Effet** : uniformité du moteur (tous reçoivent Logback au runtime) sans imposer la compilation,
pas de conflit avec d'autres bindings, libraire n'impose rien au consommateur côté dépendances
de compilation.

### Hiérarchie des responsabilités (révision)

| Responsabilité | Porté par | Avant (D16) | **Après (D16-bis, corrigé étape 6)** |
|---|---|---|---|
| Historique des steps + erreur (source FAIL_/ERROR_) | `QaLogger` (buffer ThreadLocal) | `QaLogger` | **Serenity** — `TestOutcome.getTestSteps()` + `TestStep.getException()`, lus par `TestFailureManager` (plus de buffer maison) |
| Masquage secrets avant écriture | `QaLogger` (amont) | — | **`QaLogger`** (log d'action) **+ `TestFailureManager`** (à la sérialisation des `TestStep`) |
| Écriture des 3 fichiers (`ERROR_`, `FAIL_`, HTML) | Logback appenders | **TestFailureManager en Java** (depuis le `TestOutcome` Serenity, masqué) |
| Format / séparation ERROR_/FAIL_ | Logback config | **TestFailureManager** (codé en Java, identique partout) |
| Log live (console/fichier pendant le run) | **Logback (config du consommateur)** | Logback | **Logback** — *default fourni par le socle* (`Configurator` ServiceLoader, cf. ci-dessous), **surchargeable** par un `logback.xml` local |
| Exécution / répertoire / parallélisme | Surefire | Surefire | **Surefire** (inchangé) |

### Bénéfices

- **Uniformité garantie** : les artefacts d'échec sont identiques sur les 17 projets (générés par Java,
  pas par logback.xml qui peut diverger).
- **Zéro dépendance à la config du consommateur** : qu'il ait un logback.xml ou pas, ça n'impacte
  pas les artefacts contractuels.
- **Masquage garanti** : les artefacts sont bâtis par `TestFailureManager` en **appliquant le masquage
  à la sérialisation** des `TestStep` Serenity (jamais depuis la sortie SLF4J brute d'un appender
  Logback qui verrait la sortie non masquée).
- **Contrat de sortie stable** (cf. D16 « contrat versionné ») : le format `KO__{ENV}__{Test}__{ts}/`
  + les 3 fichiers ne peuvent changer que si le socle le décide (breaking change), jamais par une
  divergence de config.

### Log live (console) : default imposé par le socle, surchargeable

Le *log live* (console / fichier courant pendant le run) n'est plus laissé au seul consommateur : le
socle en fournit un **default uniforme, activé par la simple présence du jar** — même philosophie que
l'activation native de `TestFailureManager` (D16).

**Mécanisme retenu — Logback `Configurator` (ServiceLoader)** :

- Le socle livre **`LogbackConfigurator`** (package **`internal.log`** — mécanisme caché, **aucun
  type public**, cf. D15 ; `QaLogger` reste la seule façade publique en `api.log`) implémentant
  `ch.qos.logback.classic.spi.Configurator`, déclarée dans
  `META-INF/services/ch.qos.logback.classic.spi.Configurator` (interne au jar). Elle **charge
  `logback-socle.xml`** (livré dans le jar) → la config reste en **XML lisible**, pas codée en Java.
  Nom **sans préfixe `Qa`** : le préfixe ne se justifie que pour la façade publique `QaLogger`
  (anti-collision `org.slf4j.Logger` + marque côté consommateur, D14) ; un mécanisme `internal`
  jamais importé n'en a pas besoin, et `LogbackConfigurator` dit exactement son rôle.
- Ordre de résolution Logback : `logback-test.xml` → `logback.xml` → **`Configurator` du socle** →
  défaut nu. Le Configurator n'agit donc **que** si le projet n'a aucun `logback.xml` local.
- **Conséquences** :
  - projet sans `logback.xml` (cas normal) → **reçoit le default du socle**, rien à écrire ;
  - le default **vit dans le jar** → **rien à supprimer côté projet** : impossible de l'annuler par
    inadvertance ;
  - un `logback.xml` local **gagne** (surcharge totale = opt-out explicite) ; le supprimer fait
    **retomber sur le default du socle**, jamais sur rien.

**Mécanisme « fragment includable » (`<include resource=...>`) explicitement ÉCARTÉ (YAGNI)** : il
n'apporterait qu'un raccourci « base + tweak » pour un projet voulant partir du default puis l'ajuster,
au prix d'un `logback.xml` local **obligatoire et fragile** (mêmes risques que l'annotation oubliée
rejetée en D16 : un fichier supprimé = config perdue silencieusement). Non retenu tant qu'aucun besoin
concret ne le justifie ; réintroductible plus tard **sans rupture**.

**À vérifier à l'implémentation (étape 6+)** : qu'aucune dépendance transitive (Serenity…) ne livre un
`logback.xml`/`logback-test.xml` masquant le Configurator. *Observation actuelle* : les bannières DEBUG
de Serenity dans le build = défaut nu actif → aucun `logback.xml` concurrent présent.

> Scope : ceci ne concerne **que le log live**. Les 3 fichiers `KO__` restent **écrits en Java**
> (ci-dessus), indépendants de toute config Logback.

---

## D17 — Package & groupId : placeholders volontaires de la template

`qa-template` est un **squelette réutilisable**. Le base package et le `groupId` Maven
`com.example.qa` sont des **placeholders volontaires** (pas un oubli), personnalisés **à l'adoption
(au clone)**, **avant** tout gel d'API (étape 6) ou release. La personnalisation précédant toujours le
gel, le placeholder ne « fuit » jamais dans une API figée sous le namespace de l'adoptant.

### Pas de centralisation en une constante (impossible, et inutile)

Les références au package vivent à des **couches résolues à des moments différents** → aucune property
unique ne peut toutes les alimenter :

| Référence | Résolue à | Variable possible ? |
|---|---|---|
| `package` / `import` Java | compilation | ❌ littéral (= arborescence de dossiers) |
| `<groupId>` POM | modèle de build | ❌ littéral (coordonnées + `<parent>` ne prennent pas de property) |
| `META-INF/services/...` | runtime (ServiceLoader) | ❌ FQCN en texte |
| `logback` / `serenity.conf` | runtime | ✅ `${...}` |

On n'introduit **pas** de resource filtering Maven (machinerie pour un gain partiel qui ne couvre ni le
Java ni le groupId).

### Plutôt : configs runtime *package-agnostiques* par design

`QaLogger` loggue sous un **namespace de log stable** (`"qa"`), **indépendant du base package** ;
`logback-socle.xml` cible `"qa"` (pas le package) et `serenity.conf` ne contient aucun FQCN
package-dépendant → **ces resources ne changent jamais à l'adoption**.

### Résidu réel à l'adoption (checklist, cf. README « Adoption »)

1. **Refactor IDE « Rename package »** `com.example.qa` → `<votre.package>` (déclarations + imports +
   dossiers + fichier `META-INF/services/...`).
2. **2 `<groupId>`** : `pom.xml` et `qa-socle/pom.xml` (`<parent>`).

= **1 action IDE + 2 lignes**. Surface large, opération triviale, zéro impact logique.

---

## D18 — Stratégie d'exceptions : hiérarchie unchecked + traduction en `cause` (roadmap étape 3)

- **Unchecked** : toutes les exceptions du socle étendent `RuntimeException` (via la racine). Le code
  de test n'est pas pollué de `throws`/`try-catch` ; cohérent avec Selenium/Serenity/JUnit. Corollaire
  **important** : les exceptions tierces ***checked*** (`IOException`/`FileNotFoundException` des I/O
  fichier, HTTP) **doivent être converties en unchecked à la frontière** — sinon l'API publique
  repartirait en `throws` checked, ce qu'on refuse.
- **Racine concrète `QaToolkitException`** (package `api.exception`) : un seul `catch` attrape toute
  erreur d'origine socle (clé pour l'outillage de maintenance, D9). Concrète → levable directement
  pour une erreur transverse ne relevant d'aucun domaine.
- **4 sous-types par domaine** : `SyncException`, `DataFileException`, `SecretException`,
  `ReportingException`. **Pas de sous-types par *nature*** d'erreur (jamais `DataFileNotFoundException`…) :
  la nature précise voyage dans le **message + la `cause`**, pas dans le type.
- **Traduction d'exception** (pattern *exception translation*) : on **réutilise** les exceptions des
  libs (Selenium, POI, client HTTP) et on les **re-exprime** au point de sortie en exception socle, en
  **conservant l'originale en `cause`** (la stacktrace complète reste dans `FAIL_*.log`). On ne
  réimplémente rien.
- **Justifications non symétriques** — chaque sous-type gagne sa place pour ≥ 1 raison :

  | Exception | Diagnostic à produire | Cacher un tiers *swappable* | Convertit checked→unchecked |
  |---|---|---|---|
  | `SyncException` | **oui** (D3) | oui (Selenium ↔ Appium, D4) | — (Selenium déjà unchecked) |
  | `SecretException` | non | oui (CyberArk, D12) + message **sans secret** | oui (HTTP/IO) |
  | `ReportingException` | non | oui (ALM, D13) | oui (HTTP/IO) |
  | `DataFileException` | non | partiel (POI) | **oui (I/O fichier)** ← raison principale |

- **`SyncException`, cas particulier** : avec `FluentWait` (D3), Selenium lève au timeout un
  `TimeoutException` dont la **`cause` est la dernière exception ignorée**. WebSync la lit et en déduit
  le **message différencié** (« mauvaise page / page non chargée » si `NoSuchElementException` ;
  « application instable » si `ElementNotInteractableException`/`ElementClickInterceptedException`), qui
  alimente `TestFailureManager` et l'auto-fix (D9).
- **Capture d'échec** (`TestFailureManager`, `internal`) : aucune exception publique nécessaire.
- **Écrites** : `QaToolkitException` + 4 sous-types en `api.exception` (+ `ExceptionHierarchyTest`),
  build vert. Constructeurs `(String)` et `(String, Throwable)` (le second porte la traduction).

---

## D19 — Configuration & portée/thread-safety des composants (roadmap étape 4)

> Ici « cycle de vie » = **durée de vie / portée des objets** à l'exécution — **pas** la publication
> (ça, c'est D6/D6-bis). Le socle étant stable, l'étape se réduit à : où lire la config + thread-safety.

### Source de configuration — 100 % Serenity, socle agnostique du fichier

Le socle **ne crée aucune couche de config maison**. Il lit ses clés via l'API de config Serenity
(`EnvironmentSpecificConfiguration` / system properties), qui **fusionne** `serenity.conf` +
`serenity.properties` + system properties selon la précédence de Serenity → le socle est **agnostique
du fichier** (peu importe lequel le consommateur utilise).

- **Convention recommandée (non imposée)** : `serenity.properties` = clés simples / **legacy**
  (transition douce des anciens projets) ; `serenity.conf` = config **structurée / par environnement**
  (HOCON, format moderne, greenfield).
- Les clés propres au socle (`qa.failure.*`, D16) fonctionnent depuis l'un **ou** l'autre.
- Précédence exacte (en principe system props > `serenity.conf` > `serenity.properties`) **à confirmer
  contre Serenity 4.2.22** à l'implémentation.

### Portée par composant

| Composant | Portée | Sûreté parallèle |
|---|---|---|
| `WebSync` / `MobileSync` | **par instance**, encapsule le driver du **thread courant** | confinement (jamais de driver partagé) |
| `DataFileManager` (Excel/CSV) | **par usage** (1 par fichier/opération) | rien de partagé |
| `SecretManager` / `ReportingManager` | **singleton** (client HTTP réutilisé), **stateless par requête** | pas d'état par requête (cache de token = thread-safe si présent) |
| `QaLogger` | **sans état propre** (log d'action + masquage ; pas de buffer maison) | confinement Serenity (le `TestOutcome` est par-test/par-thread côté Serenity) |
| `TestFailureManager` | **sans état**, lit le `TestOutcome` Serenity du thread + écrit un **dossier unique** `KO__` | chemins uniques → 2 échecs simultanés ne collisionnent pas |
| `LogbackConfigurator` | **one-time** au démarrage JVM (ServiceLoader) | init unique |

### État « par test » + règles de thread-safety

- **État « par test » délégué à Serenity** : l'historique (`TestOutcome`/`TestStep`) est géré par
  Serenity, confiné au test/thread courant ; le socle ne maintient **aucun buffer maison** ni son
  `remove()` (corrigé étape 6 — cf. D14).
- **Aucun `static` mutable partagé** (`static` ⇒ `final` + immuable / thread-safe / `ThreadLocal`).
- Immuabilité par défaut (`final`) ; **confinement du driver** (toujours celui du thread courant) ;
  **chemins d'artefacts uniques** (D8).

### Imposition des règles — option α (Surefire + Parent POM, 1 seul jar)

- Règle **ArchUnit** (« pas de champ `static` non-`final` ») **intégrée au jar `qa-socle`** (ArchUnit en
  scope `compile`, **cohérent avec JUnit déjà en `compile`** → **un seul artefact**, pas de 2ᵉ jar).
- Appliquée aux tests du socle **et imposée aux 17 consommateurs** via le **Parent POM** (`<build>
  <plugins>` actif, hérité) : Surefire **`dependenciesToScan`** sur `qa-socle` + system property
  **`qa.archunit.basePackage=${project.groupId}`**.
- ⚠️ Le `dependenciesToScan` est **obligatoire** : Surefire ne scanne que le module courant, la **seule
  présence du jar / d'ArchUnit ne déclenche rien**. Mais il est écrit **une fois dans le parent**, hérité
  → **zéro ligne côté projet**. Désactivation = override **visible** de la config parent.
- **β (`TestEngine` via ServiceLoader) écarté** : seul à marcher par pure présence du jar, mais SPI
  maison à écrire/maintenir + logique de périmètre embarquée → disproportionné pour un socle simple.

---

## D20 — Stratégie de test du socle (roadmap étape 5)

- **Test principal = un projet consommateur dédié.** Le socle existe pour être consommé → on le teste
  **comme un consommateur** : un projet qui exerce toutes ses fonctions contre un **site web de test
  bidon** (vrai navigateur/DOM pour `WebSync` : absences d'éléments, locators changés, timeouts → valide
  les **messages différenciés D3**, les artefacts `KO__` et le **masquage**).
- **Compléments légers** là où un navigateur est inutile :
  - clients HTTP (`CyberArkApiClient` / `AlmApiClient`) → **WireMock** (pas de serveur réel) ;
  - data (`ExcelFileReaderWriter` / `CsvFileReaderWriter`) → **fixtures** de fichiers (dont un `.xlsx`
    chiffré) ;
  - `QaLogger` / `TestFailureManager` → production des 3 artefacts + **masquage** vérifiés.
- **CI de non-régression** : **pas de dispositif isolé du socle**. Elle = **tests unitaires du socle** +
  **projet consommateur dédié**, exécutés contre **chaque nouvelle version du socle avant publication**
  (réf. D6/étape 2). Seuil de couverture : à définir si besoin (non bloquant).
