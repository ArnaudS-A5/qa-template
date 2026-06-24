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
- **But produit** : exposer aux projets consommateurs une surface familière proche de `WebElement`
  / `WebElementFacade`, mais avec une **synchronisation intégrée à chaque méthode**. Un appel
  `webSync.click(by)`, `selectByLabel(by, label)` ou `getText(by)` ne doit jamais dépendre d'un
  `WebElement` capturé à l'instant T : le locator est réévalué dans une boucle de polling jusqu'à ce
  que l'élément soit réellement présent/interactable/lisible, puis l'opération native est déléguée à
  Selenium/Serenity.
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
  passe **systématiquement** par `fluentWait`. Aucune interaction directe hors de ce moteur, aucun
  `WebElement` vivant conservé entre deux steps.
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
- **À l'intérieur** du `fluentWait`, l'action ne manipule **pas** durablement un `WebElement` Selenium
  brut : elle ré-résout le `By`, puis
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
- **Non-objectif** : réimplémenter Selenium/Serenity. Le socle les **compose** pour fournir une API
  de test plus stable : mêmes opérations attendues par les PageObjects, mais synchronisées,
  masquées et diagnostiquées de façon uniforme.

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
- Une seule implémentation, pas d'alternative prévue → **classe simple** (ex. `TestFailureManager`).
  Une classe simple ne porte PAS le suffixe de contrat de façon obligatoire : `TestFailureManager`
  garde `Manager` parce qu'il décrit bien son rôle (orchestrer la production des artefacts), pas par
  convention d'interface.

> NB : `WebSync` n'est PAS une classe simple — elle étend `AbstractSyncManager` (cas D3/D4 :
> abstract justifiée par l'arrivée du mobile, sans interface car les impls ne sont pas
> interchangeables entre elles).

## D6 — Dépendance & versioning : Parent POM + RELEASE (historique, remplacée par D6-bis)

> **Statut** : décision conservée pour mémoire. Elle est **remplacée par D6-bis**, qui abandonne le
> mot-clé Maven `RELEASE` au profit d'une version épinglée explicite et d'une montée par outil.

- Les projets consommateurs **héritent d'un Parent POM** (`qa-parent`).
- La version du socle était référencée en **`RELEASE`** (dernière version stable) avec l'objectif
  initial de ne modifier aucun projet lors d'une montée de version.
- Garde-fous qui étaient **obligatoires** dans ce scénario pour compenser la non-reproductibilité du `RELEASE` :
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
contenant **toujours** `ERROR.log` (synthétique) + `FAIL.log` (trace complète) + dump HTML
de la step en erreur. Aucun artefact pour un test OK.

**Gravé comme contrat de sortie versionné (étape 6)** : modifier ce format (nom de dossier ou des 3
fichiers) est un **breaking change** SemVer, traité comme l'API publique vis-à-vis du garde-fou (D16,
étape 10) — `qa-maintenance` (D9) et la collecte CI en dépendent. Précisions actées :
- **Noms de fichiers simplifiés** : `ERROR.log` / `FAIL.log` (sans répéter `ENV`/timestamp, déjà portés
  par le dossier) ; le HTML porte le nom de la step en erreur.
- **Délimiteur `__`** (double) entre les champs du dossier : volontaire — les champs contiennent des `_`
  (noms de méthodes de test ; timestamp `yyyy-MM-dd_HH-mm-ss`), un simple `_` rendrait le parsing ambigu.
- **Répertoire** réglable par `qa.failure.artefacts.outputDir` (cf. D16) ; défaut `target/qa-results`
  (sous `target/` → supprimé par `mvn clean` ; le pointer ailleurs pour persister).
- *Étape 8* : sanitization du nom de step pour un nom de fichier `.html` valide.

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
  - `interface SecretManager` — contrat **neutre** : `Secret getSecret(String name)` (nom logique,
    cas courant) et `Secret getSecret(String safe, String object)` (cas multi-coffre piloté depuis le
    code). Seule dépendance des projets appelants ; retourne un `Secret` (contrat de valeur sensible,
    masquage à implémenter en étape 7), jamais un `String` nu.
  - `CyberArkApiClient implements SecretManager` — implémentation concrète : reconstruction de l'URL
    de l'API REST CyberArk à partir des paramètres, appel HTTP, extraction du mot de passe de la
    charge JSON retournée.
  - Les **coordonnées d'infrastructure** du fournisseur (CyberArk AppID, Safe, Folder...) sont de la
    **configuration** (`serenity.conf`, D19), résolues par l'implémentation : le test ne fournit que ce
    qui **identifie** le secret voulu (un `name`, ou un couple `safe`/`object`).
- Le contrat de l'interface reste **neutre** : aucun type spécifique au fournisseur dans sa signature
  (sinon CyberArk fuirait dans le contrat).

> **Mise à jour (étape 6)** : l'**objet requête** neutre initialement envisagé (`SecretRequest`, et un
> `CyberArkRequest` concret côté impl) est **abandonné**. Identifier un secret par un `name` logique (ou
> un couple `safe`/`object`) suffit ; les coordonnées d'infrastructure étant de la configuration (D19),
> un type requête n'apportait qu'une indirection sans valeur. `SecretRequest` a été supprimée.
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
- **Squelettes créés** : `SecretManager` (interface, `api.secret`) + `CyberArkApiClient` (impl,
  `internal.secret`) + **`SecretManagers`** (factory publique, `api.secret`), cf. D15. Le consommateur
  obtient le manager via `SecretManagers.get()` (tenu une fois) puis `secrets.getSecret("…")` — il ne
  nomme jamais l'outil. Méthode **neutre** (`get()`, pas `cyberArk()`) : l'outil est de la config (D19).
- **Aucune implémentation pour l'instant** (phase backlog / nommage).

### Format de masquage du type `Secret` (acté étape 6 — contrat de sortie versionné)

Le rendu masqué est **figé** ; toute modification ultérieure est un **breaking change** (même statut
que le contrat de sortie `KO__`, cf. D16) :

- `masked()` / `toString()` → **2 premiers caractères en clair** + masque fixe + **16 hexa (8 octets de
  tête) du SHA-256** de la valeur. Ex. `Bo******** (sha256:0a1b2c3d4e5f6a7b)`.
- `sha256Prefix()` → les **16 hexa** seuls — pour **comparer** deux secrets sans les révéler.
- **Décision de sécurité assumée** : révéler les **2 premiers caractères** est un compromis **accepté**
  (lisibilité / diagnostic à l'œil dans les logs et dumps). Le risque — fuite partielle sur un secret
  **très court** — est **connu et jugé acceptable** ; la comparaison stricte passe de toute façon par le
  préfixe SHA-256, jamais par les caractères visibles. Implémentation du masquage : étape 7/8.

## D13 — Remontée des résultats dans ALM (à venir)

- **Interface neutre** (même principe que D12) : `interface ReportingManager` — contrat de remontée
  des résultats vers un outil de gestion de tests, seule dépendance des appelants.
- `AlmApiClient implements ReportingManager` — implémentation concrète qui pousse les résultats
  dans **ALM (OpenText/HP ALM)** via son **API REST**. Changer d'outil de reporting = nouvelle
  impl, zéro changement chez les appelants.
- **Ne pas réinventer les annotations** : réutiliser les métadonnées **Serenity** existantes pour porter
  le mapping cas de test ↔ ALM, en priorité **`@WithTag`** (ex. `@WithTag("alm.testId:1042")`),
  lues par réflexion (`TestAnnotations`). `@Title` complète si besoin.
- Version ALM cible : **24** (implémentation basée sur la **doc technique de la version 24**). Choix
  assumé : suffisamment récente pour qu'il n'y ait **pas de rupture majeure** avec les versions 25/26,
  sans être la toute dernière. (Remplace l'estimation initiale ~18.4.)
- **Squelettes créés** : `ReportingManager` + `AlmApiClient` + `TestExecutionResult` + `ExecutionStatus`,
  tous en **`internal.reporting`** (cf. **Activation AUTO** ci-dessous) — remplacent la classe unique
  `AlmReportingManager` qui mariait l'outil au concept. `ReportingManager` reste le **seam de swap**
  (ALM → autre), désormais interne.

### Activation AUTO — reporting invisible du consommateur (acté étape 6)

La remontée est **automatique**, comme la capture d'échec : un **listener interne** du socle
(`TestExecutionListener` JUnit, même mécanisme que `failure`) appelle `publishStart` au **début** et
`publishEnd` à la **fin** de chaque test. **Le consommateur ne fait que** poser `@WithTag` + configurer
`qa.alm.*` — **aucune ligne d'appel, aucune factory**. Conséquence : **tout le domaine reporting est
`internal`** (aucun type public — cf. D15) ; le consommateur ne référence jamais `ReportingManager` /
`TestExecutionResult` / `ExecutionStatus`.

**Résolution de l'ID ALM depuis `@WithTag` (lu par réflexion) :**
- **aucun** tag `alm.testId` → test **non remonté** (opt-out **silencieux** par test) ;
- **un** tag → remonté pour cet ID ;
- **plusieurs** tags `alm.testId` → le **premier** est retenu (les autres ignorés).

### Mise à jour (étape 6) — API de publication, mapping et authentification

Signatures arrêtées lors de l'écriture des coquilles typées (étape 6).

#### API de publication (deux appels par test, émis par le listener)

- `publishStart(String almTestId)` → appelé en **début de test** ; pousse le statut « In Progress » dans ALM.
- `publishEnd(TestExecutionResult result)` → appelé en **fin de test** (quel que soit le résultat) ; pousse le statut final.

`TestExecutionResult` est un **objet valeur neutre** (package `internal.reporting`) : `almTestId` (String) + `status` (ExecutionStatus).
Conçu extensible : des champs complémentaires (durée, message d'erreur…) pourront être ajoutés sans rompre la signature de `publishEnd`.

`ExecutionStatus` est une **enum maison** (package `internal.reporting`) : `IN_PROGRESS`, `PASSED`, `FAILED`.

#### Clé de maintenance

`qa.reporting.enabled` (défaut `true`). Mettre à `false` dans `serenity.conf` pour désactiver sans toucher au code (même principe que `qa.failure.artefacts.enabled`, D16).

#### Mapping test ↔ ALM (deux modes alternatifs)

La résolution de l'identifiant ALM est **interne à `AlmApiClient`** ; le code de test ne manipule jamais de coordonnées ALM brutes.

- **Mode annotation** (`@WithTag("alm.testId:1042")`) : lu par réflexion (`TestAnnotations` Serenity). Suffisant si l'ID seul identifie le cas de test dans la campagne. À privilégier tant que la structure ALM est simple et stable.
- **Mode fichier** : fichier de mapping externe. **Format** : un **mapping pur et simple à deux colonnes** — l'**adresse complète du test côté Serenity** (identité qualifiée du cas de test) en face de l'**adresse complète de l'instance de test côté ALM** (dans le scénario de test). Une ligne = une correspondance. Seul reste à figer à l'**étape 8** le **nom des colonnes** (le fonctionnement, lui, est arrêté). Privilégié quand les coordonnées sont multiples ou fréquemment changeantes (pas de recompilation).

Les deux modes sont **alternatifs** (ni complémentaires, ni en surcharge). Le choix est piloté par la configuration. Un outil de génération du fichier de mapping sera produit si la structure ALM impose ce mode de façon systématique.

> **`@AlmTest` maison non retenue** : le couple annotation/fichier couvre tous les cas identifiés.
> La décision initiale (« créer `@AlmTest` uniquement si `@WithTag` est trop limité ») est close dans ce sens.

#### Coordonnées ALM (répartition)

Deux profils, selon l'organisation des campagnes ALM :

| Profil | Coordonnées globales (`serenity.conf`) | Par test (annotation ou fichier) |
|---|---|---|
| **Défaut** (campagnes par application) | domain, project, test plan, test set | test instance ID uniquement |
| **Tout fichier** (campagnes par thématique) | — | toutes les coordonnées dans le fichier |

Le profil est sélectionné par configuration (`serenity.conf`). En profil « tout fichier », les clés globales `qa.alm.*` sont ignorées.

#### Authentification ALM

- **Login** : clé `qa.alm.login` dans `serenity.conf`.
- **Mot de passe** : récupéré via `SecretManager` (CyberArk) — jamais en clair dans la config.

#### Stateless par publication (D19)

Aucune session ALM partagée entre threads : chaque appel `publishStart`/`publishEnd` ouvre et ferme sa propre session. Garantit la sûreté en exécution parallèle Serenity.

## D14 — Renommages de classes actés (avant gel de l'API)

Renommages appliqués pendant la phase squelette (coût nul : rien d'implémenté, rien de publié) :

| Avant | Après | Raison |
|---|---|---|
| `Logger` | ~~`QaLogger`~~ **(classe supprimée, cf. ci-dessous)** | renommée pour éviter la collision SLF4J, puis **supprimée** étape 6 (aucune valeur ajoutée vs SLF4J natif) |
| `TestFailManager` | `TestFailureManager` | anglais correct (« failure » = l'échec, « fail » = verbe) |
| `ExcelFileReader` / `CsvFileReader` | `ExcelFileReaderWriter` / `CsvFileReaderWriter` | le contrat `DataFileManager` couvre lecture ET écriture ; un nom `Reader` qui écrit serait trompeur |
| `AlmReportingManager` | `ReportingManager` (interface) + `AlmApiClient` (impl) | l'outil (ALM) fuitait dans le concept (reporting) ; alignement sur le pattern D12 |
| — | `CyberArkApiClient` (au lieu de `CyberArkClient` prévu) | « Client » seul évoque le côté client applicatif (UI) ; `ApiClient` dit le rôle réel : consommer l'API de l'outil |

### Rôle de `QaLogger` → **classe supprimée (étape 6)**

> **`QaLogger` a été supprimée.** La ligne de renommage `Logger → QaLogger` du tableau ci-dessus est
> donc **caduque** (conservée pour l'historique). Récit de la décision, par étapes :

La classe avait été créée comme **façade SLF4J** pour porter trois besoins. Tous ont disparu un par un :

1. **Buffer `ThreadLocal` / « rejouer les steps »** pour bâtir `FAIL_`/`ERROR_` → **abandonné** :
   Serenity expose déjà nativement `StepEventBus.getBaseStepListener().getCurrentTestOutcome()` →
   `TestOutcome.getTestSteps()` (historique) et `TestStep.getException()`/`getErrorMessage()` (erreur).
   `TestFailureManager` lit **directement** ces structures (même principe que la réutilisation de
   `WebElementFacade`). Pas de buffer maison.
2. **Masquage des secrets** → **déplacé** : porté par **prévention à la source** via le type
   `Secret` (`api.secret`, signatures posées ; implémentation du masquage en étape 7) + application
   du masquage par `TestFailureManager` à la sérialisation des `TestStep`. Plus besoin d'un point
   d'interception dans un logger.
3. **Log d'action de synchro en live** (dernier rôle restant) → ne justifie **pas** une classe : une
   façade qui ne fait que ré-exposer `LoggerFactory.getLogger(...)` + les niveaux SLF4J est de la
   **réinvention**. `WebSync`/`MobileSync` loggent leurs actions via un **`org.slf4j.Logger` natif**.

**Où vivent les éléments conservés** : le **namespace de log stable « qa »** (D17) et la **clé de
niveau `qa.logger.level`** (défaut `WARN`, discret pour ne pas polluer Serenity) sont désormais portés
par **`LogbackConfigurator`** (`internal.log`) et le `logback-socle.xml` — **valeurs par défaut au
socle, surchargeables par projet** via un `logback.xml` local (D16-bis). Aucune façade publique de log.

> **Uniformité du format / console** : assurée par la config Logback partagée (`LogbackConfigurator`),
> jamais par une classe maison. Sans les besoins initiaux (log d'action réglable + masquage), désormais
> tous disparus, `QaLogger` n'a plus de raison d'être : on utilise **SLF4J directement**.

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
- **Implémentations** : **cachées derrière une factory** quand un point d'entrée public est nécessaire
  (`DataFiles` existe déjà pour `data` ; `secret`/`reporting` restent à confirmer à l'étape 6). Le
  consommateur dépend des **interfaces** (`api`), jamais des impls concrètes (`internal`).
- **Nommage** : `api` + `internal` uniquement (pas de package `spi`, puisque l'extension n'est pas un
  cas d'usage de premier plan).

### Classement des types (existants et planifiés)

| Type | Package | Pourquoi |
|---|---|---|
| `WebSync`, `MobileSync` | `api.sync` | types **déclarés** dans les PageObjects consommateurs (`private final WebSync`) |
| `DataFileManager` | `api.data` | interface = contrat |
| `SecretManager` | `api.secret` | interface = contrat |
| `SecretManagers` | `api.secret` | **factory publique** : seul accès au `SecretManager`, cache `CyberArkApiClient` (idiome JDK `Executors`) |
| `ReportingManager`, `TestExecutionResult`, `ExecutionStatus` | `internal.reporting` | reporting **AUTO** (listener du socle) → **aucun type public** ; `ReportingManager` reste le **seam de swap** interne (D13) |
| `Secret` | `api.secret` | type « valeur sensible » manipulé par le consommateur ; signatures de masquage posées, corps à fournir en étape 7/8 |
| `QaToolkitException` + `SyncException` / `DataFileException` / `SecretException` / `ReportingException` | `api.exception` | hiérarchie d'erreurs **publique** (contrat), unchecked — cf. D18 ; dans le périmètre japicmp (étape 10) |
| `AbstractSyncManager` | `internal.sync` | moteur, extension non prévue |
| `AbstractDataFileManager` | `internal.data` | code commun interne |
| `ExcelFileReaderWriter`, `CsvFileReaderWriter` | `internal.data` | impls, instanciées via factory (étape 6) |
| `CyberArkApiClient` | `internal.secret` | impl cachée derrière `SecretManager` |
| `AlmApiClient` | `internal.reporting` | impl cachée derrière `ReportingManager` |
| `TestFailureManager` | `internal.failure` | moteur activé par un **hook natif auto-découvert** (cf. D16) ; **aucun type public, aucune annotation**, jamais référencé par le consommateur |
| `LogbackConfigurator` *(à venir, étape 6/8)* | `internal.log` | mécanisme Logback **auto-activé** (ServiceLoader), **aucun type public**, jamais référencé par le consommateur (cf. D16-bis) |

### Conséquences

- **Factories publiques** (`api`) — **tranché étape 6** : `DataFiles` pour `data` ; **`SecretManagers`**
  pour `secret` (créée — seul accès au `SecretManager`, cache CyberArk). **Pas de factory `reporting`** :
  le reporting est **AUTO** (listener du socle, cf. D13) → tout le domaine reporting est `internal`, le
  consommateur ne l'instancie jamais.
- Pour `failure` **et `reporting`** : **ni factory ni type public**. Activation **native** (listener
  `TestExecutionListener` JUnit), invisible du consommateur — cf. **D16** (failure) et **D13** (reporting).
- `WebSync`/`MobileSync` (`api`) étendent `AbstractSyncManager` (`internal`) : dépendance
  `api → internal` **interne au socle**, invisible du consommateur (il ne voit que `WebSync`).
- Le périmètre `api` = exactement ce que japicmp surveillera (étape 10).

### Frontière `sync` : moteur interne + surface gelée + échappatoire (acté étape 6 — option A)

`WebSync`/`MobileSync` (`api`) héritent **toute** leur surface commune (actions/lectures/états/attentes
par `By`) d'`AbstractSyncManager` (`internal`). Choix retenu (option A, après arbitrage) :

- **`AbstractSyncManager` reste `internal`** — pas de déplacement en `api`, pas d'interface publique de
  surface commune. Justifié par : **pas d'extension prévue** côté consommateur (utilisation seule) et
  **pas de besoin de type commun** web/mobile (PageObjects séparés par plateforme, état de l'art).
  L'option B (moteur en `api`) exposerait inutilement le moteur ; l'option C (interface ~60 méthodes)
  ajouterait une surface à maintenir en double, sans bénéfice ici.
- **Mais ses méthodes publiques font partie du contrat gelé** : le **garde-fou japicmp (étape 10) est
  élargi** pour couvrir la surface publique héritée par `WebSync`/`MobileSync`. **Exception explicite à
  « `internal` = libre de changer »** : les méthodes publiques du moteur ne sont **pas** libres (leur
  retrait/modification casserait les 17 projets).
- **Échappatoire « bris de glace »** : `WebSync`/`MobileSync` restent **sous-classables** et leurs
  méthodes **non-`final`** (cohérent avec D19, qui n'impose `final` que sur les **champs**, pas sur les
  méthodes/classes). En cas de bug bloquant de synchro, un projet surcharge la méthode fautive dans une
  sous-classe, puis le correctif stable est **promu dans le socle** (changement de corps = non-breaking).
  Le **mode opératoire** (sous-classe nommée + point d'instanciation unique) est documenté dans le
  **Javadoc de `WebSync`/`MobileSync`**, pas ici.

## D16 — Capture d'échec : activation native + artefacts écrits par le socle (roadmap étape 6)

Comment `TestFailureManager` (package `internal.failure`) est **déclenché**, **qui porte quelle
part de la mécanique**, et **comment le consommateur le règle**. À implémenter à l'étape 6.

### Activation : native et invisible (option A, sans annotation)

- Le hook s'active par la **seule présence du jar** `qa-socle` au classpath, via le **ServiceLoader de
  JUnit Platform** : le jar livre un `META-INF/services/org.junit.platform.launcher.TestExecutionListener`
  déclarant **`TestFailureManager`** (`internal.failure`), qui **implémente lui-même**
  `TestExecutionListener` (hook + écriture en une seule classe simple). Le `Launcher` JUnit
  auto-enregistre ce listener — c'est le moteur que **Surefire** lance à chaque `mvn test`/`verify`
  (local et CI), et c'est exactement ainsi que **serenity-junit5** s'active lui-même.
  - ⚠️ **Correction (vérifié sur Serenity 4.2.22)** : la version initiale de D16 parlait d'un
    **ServiceLoader d'un `StepListener` Serenity** — **inexact**. Serenity **ne découvre pas** les
    `StepListener` par ServiceLoader (un `META-INF/services/...StepListener` serait **ignoré**) ; ils
    s'enregistrent **programmatiquement** (`StepEventBus.getEventBus().registerListener(...)`). Le seul
    service auto-découvert par présence du jar dans cette stack est le `TestExecutionListener` JUnit.
  - À la fin d'un test (`executionFinished`), `TestFailureManager` lit le résultat et, **uniquement en cas d'échec**,
    récupère le `TestOutcome` Serenity
    (`StepEventBus.getEventBus().getBaseStepListener().getCurrentTestOutcome()` → steps + exception)
    puis délègue à `TestFailureManager#writeArtefacts`. *(L'instant exact de lecture — `executionFinished`
    JUnit vs `testFinished` Serenity — est à confirmer à l'implémentation, étape 8.)*
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
| Exécution, répertoire de travail, propriétés système, parallélisme | **Maven Surefire** | c'est lui qui lance les tests et fixe l'environnement |
| Détection de l'échec + collecte des steps + déclenchement du dump HTML + nommage du dossier `KO__` | **`TestFailureManager`** (mince) | logique spécifique au socle, non couverte nativement |
| Écriture des 3 fichiers `ERROR_` / `FAIL_` / HTML, format et séparation des contenus | **`TestFailureManager` en Java** | contrat de sortie identique sur les 17 projets, indépendant d'un `logback.xml` local |
| Historique des steps + erreur (source du `FAIL_*.log`/`ERROR_*.log`) | **Serenity** (`TestOutcome`/`TestStep`) | déjà fourni nativement — lu par `TestFailureManager`, pas de buffer maison (cf. D14 corrigé) |
| Log live pendant le run | **Logback** | rôle natif du logging ; le default socle est porté par `LogbackConfigurator` / D16-bis |

> Principe : tout ce qui est **déjà fourni nativement** (cycle de test Serenity, lancement Surefire,
> log live Logback) est réutilisé. Le socle code uniquement le contrat spécifique : dossier `KO__`,
> trois fichiers, format stable, masquage et dump HTML.

### Paramétrage par le consommateur (clés + valeurs par défaut)

Le hook lit des **clés de configuration** depuis **`serenity.conf`** ou les **system properties**
(priorité property > conf > défaut). **Toutes ont une valeur par défaut** → si le consommateur ne
configure rien, ça marche. Noms **figés** (étape 6, cf. constantes `TestFailureManager`) :

| Clé | Rôle | Défaut |
|---|---|---|
| `qa.failure.artefacts.enabled` | produire / non les artefacts d'échec (opt-out global) | `true` |
| `qa.failure.artefacts.outputDir` | racine des dossiers de résultats | `target/qa-results` |
| `qa.failure.artefacts.dumpHtml` | produire ou non le dump HTML de la step | `true` |

> **Noms figés** dans `TestFailureManager` (constantes `ENABLED_KEY`, `OUTPUT_DIR_KEY`, `DUMP_HTML_KEY`
> + leurs `*_DEFAULT`), unifiés sous le préfixe **`qa.failure.artefacts.*`** (`failure` = contexte « sur
> échec », `artefacts` = la production gérée ; leaf `.enabled` parallèle à `qa.reporting.enabled`).
> **`{ENV}` du nommage `KO__` n'est PAS une clé du socle** : il est lu de l'**environnement Serenity
> actif** (propriété `environment`, D19) — source unique, pas de double définition (sinon divergence si
> modifié à deux endroits).

> L'**opt-out** (`qa.failure.artefacts.enabled=false`) est l'échappatoire pour un projet qui ne veut pas
> du mécanisme, sans toucher au code — il remplace le « ne pas mettre l'annotation » de l'option B.

### Contrat de SORTIE = second contrat versionné (⚠️)

L'emplacement, le **nommage** (`KO__{ENV}__{Test}__{ts}/`) et les **3 fichiers** (cf. D8) forment un
**contrat à part entière**, car l'**agent `qa-maintenance`** et la **récupération d'artefacts CI** en
dépendent. Donc :

- l'**implémentation interne** de `TestFailureManager` peut changer librement (zéro impact
  consommateur, grâce à D15 + activation native) ;
- **mais** changer le **format de sortie** est un **breaking change** (SemVer), même si aucune ligne
  de code consommateur ne change. À traiter comme l'API publique vis-à-vis du garde-fou (étape 10).

## D6-bis — Versioning : fin de `RELEASE`, Maven Wrapper + Enforcer

**Amende D6** : remplace le mot-clé `RELEASE` par une **version épinglée explicite, bumpée par outil**.

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

### Escape mechanism (hérité de D6)

Si un projet doit rester sur une ancienne version du socle : simple non-merge de la PR de bump ou
`git revert` si déjà merged. C'est mille fois plus clair et traçable que d'inventer un override.

---

## D16-bis — Logs : socle maîtrise la sortie, le type `Secret` maîtrise le masquage

**Amende D16** : le socle ne délègue *pas* à Logback l'écriture des artefacts d'échec ; il les écrit lui-même en Java.

### Correction des scopes

- **qa-socle** : déclare `slf4j-api` en `compile` (le contrat SLF4J, transmis aux consommateurs) et
  `logback-classic` en **`compile`** (le binding, transmis aux consommateurs aux runtime **et** compile).
- **Enforcer** (D6-bis) : banni les bindings concurrent (slf4j-simple, log4j-slf4j) → un seul Logback.

> **Évolution (étape 6) — de `runtime` à `compile` + garde-fou ArchUnit.** `logback-classic` était
> initialement en `runtime` pour ne **pas** exposer le binding au **compile** des consommateurs (qui ne
> doivent dépendre que de SLF4J). Mais `LogbackConfigurator` (livré par le socle, cf. plus bas) **doit
> implémenter** le type Logback `ch.qos.logback.classic.spi.Configurator` → le socle a besoin de Logback
> **au compile**, ce que `runtime` interdit. **Choix retenu : `compile`** (POM simple, 1 ligne). La règle
> « ne pas coder contre le binding » n'est plus assurée par l'**absence** de Logback au compile, mais par
> une **règle ArchUnit** (cf. **D19/α**) : *aucune classe hors `internal.log` ne dépend de
> `ch.qos.logback..`* → **échoue le build** en cas de violation. Garde-fou **plus robuste** (il attrape
> l'usage quelle que soit la façon dont Logback arrive au classpath) et **POM plus simple** que l'option
> `provided` + Parent. Application : étape 8 (avec l'implémentation du `Configurator`).

**Effet** : uniformité du moteur (tous reçoivent Logback au runtime), le `Configurator` compile, et le
couplage au binding reste **interdit côté consommateur** — non plus par scope mais par règle ArchUnit
vérifiée au build.

### Hiérarchie des responsabilités (révision)

| Responsabilité | Porté par | Avant (D16) | **Après (D16-bis, corrigé étape 6)** |
|---|---|---|---|
| Historique des steps + erreur (source FAIL_/ERROR_) | `QaLogger` (buffer ThreadLocal) | `QaLogger` | **Serenity** — `TestOutcome.getTestSteps()` + `TestStep.getException()`, lus par `TestFailureManager` (plus de buffer maison) |
| Masquage secrets avant écriture | `QaLogger` (amont) | — | **Type `Secret`** (signatures posées, impl étape 7) **+ `TestFailureManager`** (à la sérialisation des `TestStep`) — plus de `QaLogger` |
| Écriture des 3 fichiers (`ERROR_`, `FAIL_`, HTML) | Logback appenders | Logback appenders | **TestFailureManager en Java** (depuis le `TestOutcome` Serenity, avec masquage à la sérialisation) |
| Format / séparation ERROR_/FAIL_ | Logback config | Logback config | **TestFailureManager** (codé en Java, identique partout) |
| Log live (console/fichier pendant le run) | **Logback (config du consommateur)** | Logback | **Logback** — *default fourni par le socle* (`Configurator` ServiceLoader, cf. ci-dessous), **surchargeable** par un `logback.xml` local |
| Exécution / répertoire / parallélisme | Surefire | Surefire | **Surefire** (inchangé) |

### Bénéfices

- **Uniformité garantie** : les artefacts d'échec sont identiques sur les 17 projets (générés par Java,
  pas par logback.xml qui peut diverger).
- **Zéro dépendance à la config du consommateur** : qu'il ait un logback.xml ou pas, ça n'impacte
  pas les artefacts contractuels.
- **Masquage garanti à l'implémentation** : les artefacts seront bâtis par `TestFailureManager` en
  **appliquant le masquage à la sérialisation** des `TestStep` Serenity (jamais depuis la sortie SLF4J
  brute d'un appender Logback qui verrait la sortie non masquée).
- **Contrat de sortie stable** (cf. D16 « contrat versionné ») : le format `KO__{ENV}__{Test}__{ts}/`
  + les 3 fichiers ne peuvent changer que si le socle le décide (breaking change), jamais par une
  divergence de config.

### Log live (console) : default imposé par le socle, surchargeable

Le *log live* (console / fichier courant pendant le run) n'est plus laissé au seul consommateur : le
socle en fournit un **default uniforme, activé par la simple présence du jar** — même philosophie que
l'activation native de `TestFailureManager` (D16).

**Mécanisme retenu — Logback `Configurator` (ServiceLoader)** :

- Le socle livre **`LogbackConfigurator`** (package **`internal.log`** — mécanisme caché, **aucun
  type public**, cf. D15 ; il n'y a **pas** de façade de log publique, `QaLogger` ayant été supprimée)
  implémentant `ch.qos.logback.classic.spi.Configurator`, déclarée dans
  `META-INF/services/ch.qos.logback.classic.spi.Configurator` (interne au jar). Elle **charge
  `logback-socle.xml`** (livré dans le jar) → la config reste en **XML lisible**, pas codée en Java.
  Il porte aussi les constantes du **namespace « qa »** et de la **clé `qa.logger.level`** (défaut
  `WARN`). Nom **sans préfixe `Qa`** : un mécanisme `internal` jamais importé n'en a pas besoin, et
  `LogbackConfigurator` dit exactement son rôle.
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

Le log d'action de synchro (`WebSync`/`MobileSync` via SLF4J natif) écrit sous un **namespace de log
stable** (`"qa"`, porté par `LogbackConfigurator`), **indépendant du base package** ; `logback-socle.xml`
cible `"qa"` (pas le package) et `serenity.conf` ne contient aucun FQCN package-dépendant → **ces
resources ne changent jamais à l'adoption**.

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
- **Aucune clause `throws` dans les signatures** (harmonisé étape 6) : les exceptions étant unchecked,
  l'API publique **ne déclare pas** `throws <Exception>` — idiome standard (Selenium/Serenity/JUnit n'en
  déclarent pas non plus). Les exceptions susceptibles d'être levées restent **documentées via `@throws`
  en Javadoc**, jamais dans la signature. *(Correctif : les `throws SyncException` / `throws
  DataFileException` qui subsistaient sur `sync` et `data` ont été retirés pour cet alignement ; les
  imports d'exception devenus inutiles ont été supprimés.)*
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
- Précédence exacte **vérifiée contre Serenity 4.2.22** (bytecode `PropertiesLocalPreferences`) :
  **propriétés système `-D…` / variables d'environnement > `serenity.conf` (HOCON) > `serenity.properties`
  (legacy)**. Mécanisme : la map de préférences est amorcée avec les propriétés système/env, puis
  `setUndefinedSystemPropertiesFrom(...)` ne remplit que les clés **non déjà définies** (le premier qui
  pose une clé gagne) ; dans `loadPreferences`, `serenity.conf` est chargé **avant** `serenity.properties`.
  Les blocs `environments { <name> { … } }` sont sélectionnés par la propriété **`environment`** (la même
  que celle d'où `TestFailureManager` lit `{ENV}`, D16). **Conséquence pratique** : un override par run en
  CI se fait en `-Dqa.xxx=…`, sans toucher au fichier.

### Portée par composant

| Composant | Portée | Sûreté parallèle |
|---|---|---|
| `WebSync` / `MobileSync` | **par instance**, encapsule le driver du **thread courant** | confinement (jamais de driver partagé) |
| `DataFileManager` (Excel/CSV) | **par usage** (1 par fichier/opération) | rien de partagé |
| `SecretManager` / `ReportingManager` | **singleton** (client HTTP réutilisé), **stateless par requête** | pas d'état par requête (cache de token = thread-safe si présent) |
| Log d'action (`WebSync`/`MobileSync` via SLF4J natif) | **sans état propre** (pas de façade ni buffer maison) | SLF4J/Logback thread-safe ; niveau du logger « qa » |
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

- Règle **ArchUnit** (« pas de champ `static` non-`final` ») à **intégrer au jar `qa-socle`** (ArchUnit en
  scope `compile`, **cohérent avec JUnit déjà en `compile`** → **un seul artefact**, pas de 2ᵉ jar).
- Règle **ArchUnit complémentaire — interdiction de coder contre le binding Logback** (cf. **D16-bis**) :
  *aucune classe hors `com.example.qa.internal.log` ne doit dépendre de `ch.qos.logback..`* — le
  consommateur passe par **SLF4J**, jamais par Logback directement. Échoue le build en cas de violation.
  Remplace l'isolation par scope (Logback est en `compile`) par un garde-fou explicite, plus robuste.
- **NB (à spécifier étape 8) — clé d'activation par règle** : chaque règle ArchUnit doit exposer **sa
  propre clé de config** (défaut `true`), permettant à un projet de **désactiver une règle en urgence**
  sans changer de version du socle (échappatoire de débogage). Risque **assumé** car **visible** : un
  scan des clés d'un projet révèle immédiatement les règles bypassées (à remettre en ordre). Plus souple
  qu'un blocage dur. *(Noté ; non implémenté à ce stade.)*
- À appliquer aux tests du socle **et à imposer aux 17 consommateurs** via le **Parent POM** (`<build>
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
  - `TestFailureManager` (depuis le `TestOutcome` Serenity) → production des 3 artefacts + **masquage**
    (type `Secret`) vérifiés.
- **CI de non-régression** : **pas de dispositif isolé du socle**. Elle = **tests unitaires du socle** +
  **projet consommateur dédié**, exécutés contre **chaque nouvelle version du socle avant publication**
  (réf. D6/étape 2). Seuil de couverture : à définir si besoin (non bloquant).

---

## D21 — Gouvernance des releases : dépréciation, publication, gate (roadmap étape 2)

Complète D6/D6-bis (versioning, montée par outil) et D20 (CI de non-régression) en fixant **les trois
règles de gouvernance** d'une release du socle.

### Politique de dépréciation : retrait après **≥ 3 versions mineures**

- Un élément d'API retiré du contrat (méthode, type, format de sortie D8/D16) est d'abord marqué
  **`@Deprecated`** (+ Javadoc indiquant le remplacement) en version **mineure**, **jamais supprimé dans
  la foulée**.
- Le **retrait effectif** n'est autorisé qu'après **au moins 3 versions mineures** de coexistence
  (déprécié en `x.y` → retirable au plus tôt en `x.(y+3)`).
- **Rationale** : le socle est **conçu pour très peu bouger** ; les versions **MAJEURES seront rares**.
  Attendre un MAJOR pour nettoyer (SemVer pur) laisserait du code déprécié traîner **des années** — ou
  forcerait à publier un MAJOR à la moindre modification. Le délai de grâce en **mineures** est le bon
  compromis : il permet de nettoyer à un rythme raisonnable sans imposer un MAJOR.
- **Entorse assumée au SemVer strict** : ce retrait en mineure est l'**unique** exception, **bornée** (≥ 3
  mineures) et **annoncée** (`@Deprecated` + japicmp signale la rupture, réf. étape 10). Tout autre
  breaking change reste réservé au MAJOR.

### Publication : **mainteneur, manuel, sur CI verte**

- La release est **déclenchée manuellement par le mainteneur**, **uniquement** une fois la **CI au vert**
  (non-régression D20 passée).
- **Pas de publication automatique** sur tag/branche : le moment du release reste un **geste humain
  contrôlé** (lib socle consommée par ~17 projets → on garde la main sur le « quand »).
- Coût assumé : dépend de la disponibilité du mainteneur (acceptable vu la fréquence faible des releases).

### Gate de non-régression : **gate Maven (`deploy` après `verify`)**

- Le blocage vit dans le **build Maven**, pas dans Jenkins (D7, CI gérée par une autre équipe) : `deploy`
  n'est atteignable **que si `verify` passe** (tests + japicmp + ArchUnit).
- **Portable et reproductible** : même garantie en local et en CI, **indépendant de l'outil d'intégration**
  (la logique de gating reste dans le projet, pas externalisée dans le pipeline).
