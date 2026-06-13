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
  **`WebSync`** (web, WebDriver) / **`MobileSync`** (mobile, squelette créé — implémentation à venir).
- Classe centrale d'attente côté web : **`WebSync`**.
- Méthode **privée** `fluentWait(Function<WebDriver, T>)` :
  - `FluentWait` avec polling court (~250 ms) ;
  - `ignoring(...)` : `NoSuchElementException`, `StaleElementReferenceException`,
    `ElementNotInteractableException`, `ElementClickInterceptedException`.
- **Toutes** les actions (`click`, `type`, `selectByLabel`, `getText`, ...) passent par `fluentWait`.
- Méthode complémentaire : **flag JavaScript injecté** (un simple drapeau, pas du code applicatif).
  Sa disparition signale un chargement/re-render en cours ; on poll l'élément cible jusqu'au timeout.
- Justification : SPA Angular/React/Vue avec mutations du DOM **sans** changement d'URL ni de state.
  Attendre l'état stable de l'élément cible en absorbant les exceptions est plus robuste que de
  chasser un spinner/overlay.
- `WebElement`/`Select` natifs Selenium sont **encapsulés dans** `WebSync` (pas de `WebElementManager`).

### Messages d'erreur différenciés (alimentent `TestFailureManager` et l'auto-fix)

- Élément **absent** au timeout → « mauvaise page / page non chargée ».
- Élément **présent mais jamais interactable** → « application instable ».

## D4 — Abstraction de la synchronisation (anticipation mobile)

- `AbstractSyncManager` (contient `fluentWait`) → `WebSync` (web) / `MobileSync` (implémentation à venir).
- L'abstraction est justifiée **uniquement** par l'arrivée future du mobile (Appium) :
  deux classes, deux drivers, mais un **moteur d'attente commun** écrit une seule fois.
- Sans le mobile, une classe unique aurait suffi (aucune duplication dans une même classe).

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

`QaLogger` est une **façade** au-dessus de SLF4J (qui reste le moteur d'écriture via Logback) :

1. **Format uniforme** pour les 17 projets : un point d'entrée unique, mêmes conventions de message.
2. **Accumulation de la trace par test** : la matière première des artefacts `FAIL_*.log` /
   `ERROR_*.log` produits par `TestFailureManager` (un logger standard ne sait pas « rejouer »
   l'historique des steps d'un test).
3. **Masquage des secrets** avant toute écriture (roadmap étape 7, OWASP) — impossible à imposer
   si chaque projet appelle SLF4J directement.

Sans ces trois besoins, la classe n'aurait pas de raison d'être (on utiliserait SLF4J directement).

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

### Classement des types existants

| Type | Package | Pourquoi |
|---|---|---|
| `WebSync`, `MobileSync` | `api.sync` | types **déclarés** dans les PageObjects consommateurs (`private final WebSync`) |
| `DataFileManager` | `api.data` | interface = contrat |
| `SecretManager` | `api.secret` | interface = contrat |
| `ReportingManager` | `api.reporting` | interface = contrat |
| `QaLogger` | `api.log` | façade appelée directement par le consommateur |
| `AbstractSyncManager` | `internal.sync` | moteur, extension non prévue |
| `AbstractDataFileManager` | `internal.data` | code commun interne |
| `ExcelFileReaderWriter`, `CsvFileReaderWriter` | `internal.data` | impls, instanciées via factory (étape 6) |
| `CyberArkApiClient` | `internal.secret` | impl cachée derrière `SecretManager` |
| `AlmApiClient` | `internal.reporting` | impl cachée derrière `ReportingManager` |
| `TestFailureManager` | `internal.failure` | moteur activé par un **hook natif auto-découvert** (cf. D16) ; **aucun type public, aucune annotation**, jamais référencé par le consommateur |

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
