# Revue complète du socle QA — structure, classes, signatures, pièges d'implémentation

> **Posture de cette revue** : les 22 décisions d'architecture ([decisions.md](decisions.md)) sont
> prises comme **acquises**. La revue porte sur (1) la **fidélité** entre le code/structure et
> l'intention, et (2) les **pièges d'implémentation** de l'étape 8. Une décision n'est rouverte que si
> elle crée un risque réellement critique.
>
> **État du socle au moment de la revue** : squelette **post-gel d'API (2026‑06‑24)**, signatures figées,
> corps volontairement vides (`return null` / `void`). Les corps vides **ne sont pas** comptés comme des
> défauts.
>
> **Vérifié factuellement** : `./mvnw clean test` → **BUILD SUCCESS**, **10 tests verts** (~1,8 s).
> `grep` : 0 `throws` en signature, 0 `static` mutable, collision `TestExecutionResult` confirmée —
> **résolue le 2026-06-25** (C3 : type socle renommé `TestExecutionReport`).

---

## 1. Intention — métier et opérationnel

**Métier.** Le socle est une **librairie** (`qa-parent` + jar `qa-socle`) qui industrialise l'outillage
QA transverse aux ~17 projets de tests : synchronisation web/mobile robuste aux SPA, lecture/écriture de
données Excel/CSV, récupération de secrets masqués, remontée automatique des résultats dans ALM, et
capture d'échec produisant des artefacts de diagnostic uniformes (`KO__…`). Il **compose** la stack
(Selenium / Serenity / JUnit 5 / POI) plutôt que de la réécrire.

**Opérationnel.** Le socle est **consommé comme dépendance Maven** ; les projets portent les tests et
sont joués via **Jenkins** (autre équipe). Le public cible est une **équipe peu mature**, surtout en
**maintenance/exécution de TNR** et occasionnellement en conception. Cela explique — et **justifie** —
les choix « invisibles » du socle : activation native par ServiceLoader (rien à câbler), factories
(pas de `new`), neutralité des contrats (l'outil sous-jacent n'apparaît jamais), masquage à la source,
et `void`/exceptions unchecked (pas de cérémonie `throws`/`try-catch`).

**Verdict global.** La conception est **solide, traçable et cohérente**. Le code actuel est un squelette
**fidèle** aux décisions (frontière api/internal, nommage, no-leak, unchecked, thread-safety hygiène
toutes respectées et vérifiées). Il n'y a **aucun défaut critique dans le squelette lui-même** : les
points « ultra-critiques » de cette revue sont des **pièges d'implémentation** (étape 8) à désamorcer
**avant** d'écrire les corps — pas des bugs présents.

---

## 2. Vue macro — structure du projet

```
qa-parent (pom, agrégateur, versioning centralisé, enforcer, wrapper 3.9.9)
└── qa-socle (jar)
    ├── api/         ← contrat public consommé par les 17 projets (frozen, périmètre japicmp)
    │   ├── sync       WebSync, MobileSync, SwipeDirection
    │   ├── data       DataFileManager (interface), DataFiles (factory)
    │   ├── secret     SecretManager (interface), SecretManagers (factory), Secret (valeur)
    │   └── exception  QaToolkitException + 4 sous-types
    └── internal/    ← moteurs & impls, non contractuels (libres de changer*)
        ├── sync       AbstractSyncManager
        ├── data       AbstractDataFileManager, ExcelFileReaderWriter, CsvFileReaderWriter
        ├── secret     CyberArkApiClient
        ├── reporting  ReportingManager (interface), AlmApiClient, TestExecutionReport, ExecutionStatus
        ├── failure    TestFailureManager (+ META-INF/services)
        └── log         LogbackConfigurator
```
> *exception D15 : les **méthodes publiques** d'`AbstractSyncManager`, héritées par `WebSync`/`MobileSync`,
> sont gelées malgré le package `internal` (garde-fou japicmp élargi).

**La répartition des packages est cohérente avec l'intention** : un découpage **par visibilité** (api /
internal) **puis par domaine**, ce qui est exactement la frontière que japicmp surveillera (étape 10) et
qu'ArchUnit pourra protéger. Les noms de packages indiquent sans ambiguïté la responsabilité. Le
classement réel des 25 types **correspond au tableau D15 à 100 %** (vérifié).

**Performance structurelle vs rigidité.** La structure est **plate et lisible** (1 niveau api/internal
+ 1 niveau domaine), sans sur-ingénierie : pas de JPMS, pas de package `spi`, pas de couche de config
maison. C'est le bon niveau d'abstraction pour une équipe peu mature. Les seuls points de rigidité sont
**assumés et documentés** (surface `sync` gelée même en `internal`, contrats de sortie versionnés).

---

## 3. Vue macro — inventaire des types et de leur nature

| Type | Package | Nature / modificateurs | Fidélité décision | État |
|---|---|---|---|---|
| `WebSync` | api.sync | `public class extends AbstractSyncManager`, méthodes non-`final` | D3/D4/D15 ✅ | coquille |
| `MobileSync` | api.sync | `public class extends AbstractSyncManager`, non-`final` | D4 ✅ | coquille (impl différée) |
| `SwipeDirection` | api.sync | `public enum` (UP/DOWN/LEFT/RIGHT) | D4 ✅ neutre | complet |
| `AbstractSyncManager` | internal.sync | `public abstract class`, `fluentWait` **`protected`** | D3 ✅ | coquille |
| `DataFileManager` | api.data | `public interface` (sans marqueur `I`) | D5/D12 ✅ | signé |
| `DataFiles` | api.data | `public final class`, ctor `private`, factory `static` | D15 ✅ | coquille |
| `AbstractDataFileManager` | internal.data | `public abstract class implements DataFileManager`, primitives `protected abstract` | D5 ✅ template method | coquille |
| `ExcelFileReaderWriter` / `CsvFileReaderWriter` | internal.data | `public class extends Abstract…` | D5/D14 ✅ | coquille |
| `SecretManager` | api.secret | `public interface` neutre | D12 ✅ | signé |
| `SecretManagers` | api.secret | `public final class`, ctor `private`, `get()` neutre `static` | D12/D15 ✅ idiome `Executors` | coquille |
| `Secret` | api.secret | `public final class`, champ `private final`, ctor `private`, `of()` | D12/MASK ✅ | partiel |
| `CyberArkApiClient` | internal.secret | `public class implements SecretManager` | D12/D14 ✅ | coquille |
| `ReportingManager` | internal.reporting | `public interface` (**internal**, seam de swap) | D13/D15 ✅ | signé |
| `AlmApiClient` | internal.reporting | `public class implements ReportingManager` | D13/D14 ✅ | coquille |
| `TestExecutionReport` | internal.reporting | `public final class`, champs `private final`, `of()` | D13 ✅ immuable extensible (renommé depuis `TestExecutionResult`, C3) | partiel |
| `ExecutionStatus` | internal.reporting | `public enum` (IN_PROGRESS/PASSED/FAILED) | D13 ✅ | complet |
| `TestFailureManager` | internal.failure | `public class implements TestExecutionListener`, constantes `static final` | D5/D16 ✅ | coquille + clés |
| `LogbackConfigurator` | internal.log | `public final class`, constantes `static final` | D16-bis/D17 ✅ | coquille |
| `QaToolkitException` | api.exception | `public class extends RuntimeException` (concrète) | D18 ✅ | **complet** |
| `SyncException` / `DataFileException` / `SecretException` / `ReportingException` | api.exception | `public class extends QaToolkitException` | D18 ✅ | **complet** |

**Lecture transverse de la nature des types** : l'usage des modificateurs Java est **discipliné et
correct** — `abstract` réservé aux moteurs/templates ; `final` sur les classes qui ne doivent pas être
étendues (factories, valeurs) et **jamais** sur les façades `sync` (échappatoire « bris de glace »
voulue, D15) ; constructeurs `private` partout où une factory est le seul point d'entrée ; champs
`private final` sur les valeurs immuables ; `protected` réservé au seul `fluentWait` (hérité, jamais
réimplémenté). C'est exactement ce que la doctrine impose.

---

## 4. Vue détaillée par domaine — signatures, héritage, polymorphisme, fidélité

### 4.1 SYNC (le cœur)
- **Héritage** : `AbstractSyncManager` (moteur + surface commune ~60 méthodes par `By`) →
  `WebSync` (spécifique web) / `MobileSync` (gestes mobiles). Écrit **une seule fois** dans l'abstract.
- **Polymorphisme** : `within(By)` est `abstract` dans le parent et **redéfini avec retour covariant**
  (`WebSync within(...)` / `MobileSync within(...)`) — élégant et conforme no-leak (D3). C'est le seul
  point de polymorphisme réel, et il est bien posé.
- **No-leak vérifié** : toutes les méthodes prennent un `By` et renvoient une **valeur** (`String`,
  `boolean`, `int`, `Point/Dimension/Rectangle`, `List<String>`) ou `void`. Aucune ne rend de
  `WebElement`/`WebElementFacade`. ✅
- **Assertions** : `should…` → `void` (D22), prédicats `is…`/`contains…` → `boolean`. Séparation nette
  « vérifie ou lève » vs « interroge ». ✅
- **Saisie sensible** : `type(By, Secret)` est bien le **point unique** de déballage prévu.

### 4.2 DATA
- **Patron Template Method** correctement structuré : l'abstract implémente `readRows`/`getValue`/
  `writeRows` ; les formats ne fournissent que `readRawRows`/`writeRawRows` (`protected abstract`).
- Factory `DataFiles` avec 4 fabriques (`excel(Path)`, `excel(Path, String)`, `csv(Path)`,
  `csv(Path, char)`) — couvre chiffré et délimiteur. Contrat « jamais `null`, cellule vide → `""` »
  (BF-DATA-04) **documenté** côté interface — à honorer à l'impl.

### 4.3 SECRET + MASK
- `Secret` : `toString()` **délègue déjà** à `masked()`. La **prévention de fuite est garantie
  structurellement** dès maintenant (même si `masked()` renvoie `null` tant que l'étape 8 n'est pas
  faite) : un secret loggué passera toujours par `toString()`. Bon design.
- `SecretManagers.get()` neutre, cache `CyberArkApiClient`. Le swap d'outil = 1 ligne interne. ✅

### 4.4 REPORTING (entièrement internal — AUTO)
- Tout le domaine est `internal`, **aucun type public** : conforme à la décision « reporting AUTO »
  (D13/D15). Le consommateur ne pose que `@WithTag` + config. `ReportingManager` reste le seam de swap.
- `TestExecutionReport` immuable et **extensible** (champs ajoutables sans casser `publishEnd`). ✅

### 4.5 FAILURE
- `TestFailureManager implements TestExecutionListener` (SPI JUnit Platform), déclaré dans
  `META-INF/services/org.junit.platform.launcher.TestExecutionListener` — **fichier présent, FQCN
  correct** (vérifié). Hook + écriture en une classe simple (D5/D16). Clés de config gelées en
  constantes `static final` avec défauts. ✅

### 4.6 LOG / EXCEPTION / VERSIONING
- `LogbackConfigurator` : namespace `"qa"` et clé `qa.logger.level` portés en constantes ; activation
  par `Configurator` ServiceLoader (à implémenter étape 8). ✅
- **EXCEPTION = le seul domaine réellement terminé** (étape 3) : racine concrète unchecked, 4 sous-types,
  2 constructeurs chacun, `cause` conservée, **testé**. Rien à redire.
- POM : `dependencyManagement` centralisé, enforcer (`requireMavenVersion`, `requireJavaVersion`,
  `banDynamicVersions`, `bannedDependencies` un seul binding SLF4J), wrapper 3.9.9. ✅

---

## 5. Tableau récapitulatif

### 5.1 Points forts

| # | Point fort | Pourquoi ça compte pour cette équipe |
|---|---|---|
| F1 | Frontière **api/internal** nette, classement des 25 types fidèle à D15 | Périmètre de compat clair ; l'équipe ne touche que `api` |
| F2 | **Nommage** rigoureux et homogène (`Abstract*`, pas de `Impl`, `ApiClient`, interfaces neutres) | Lisibilité immédiate concept ↔ impl |
| F3 | **Hiérarchie d'exceptions terminée et testée** (unchecked, racine concrète, `cause`) | Un seul `catch (QaToolkitException)` ; pas de `throws` à gérer |
| F4 | **No-leak** respecté partout (tout par `By`, retours valeur, `within` covariant) | Pas de `StaleElement` possible côté test |
| F5 | **Hygiène thread-safety** vérifiée : 0 `static` mutable, valeurs immuables `final` | Sûr en exécution parallèle Serenity |
| F6 | **Activation native** (ServiceLoader) correctement déclarée | Rien à câbler ⇒ pas d'« annotation oubliée » |
| F7 | **Masquage garanti structurellement** (`toString()`→`masked()`) | Fuite de secret improbable même par log accidentel |
| F8 | **Versioning centralisé** + enforcer + wrapper en place | Reproductibilité, M4-safe, 1 ligne à bumper |
| F9 | **Traçabilité documentaire exceptionnelle** (BF-* ↔ D* ↔ roadmap ↔ code) | Onboarding et maintenance facilités |
| F10 | **Build vert, rapide, déterministe** (10 tests) | Base saine pour démarrer l'étape 8 |

### 5.2 Points faibles (non bloquants — à corriger au fil de l'eau)

| # | Point faible | Correction |
|---|---|---|
| W1 | `AlmApiClient` Javadoc dit ALM **« ~18.4 »** alors que D13/README ont acté **v24** | Aligner le Javadoc sur v24 |
| W2 | **README** liste `ReportingManager` en colonne **« api (public) »** alors qu'il est **internal** (D13/D15) | Corriger le tableau README (sinon induit l'équipe en erreur) |
| W3 | `AlmApiClient` Javadoc mode-fichier (« nom qualifié de la classe ») diverge de D13 (mapping **2 colonnes** test Serenity ↔ instance ALM) | Aligner sur D13 |
| W4 | Répertoire **`api/reporting` vide** (vestige : tout reporting est internal) | Supprimer le dossier |
| W5 | `sfd.md` ligne 1 : coquille `tell#` en tête de titre | Nettoyer |
| W6 | Aucun **exemple `serenity.conf`/`serenity.properties`** fourni pour les consommateurs | Ajouter un fichier d'exemple commenté (aide à l'adoption, équipe peu mature) |
| W7 | POM commente encore `logback-classic` « runtime… sans polluer compile » alors que la direction actée (D16-bis) est **compile** | Le commentaire doit refléter le basculement prévu (cf. P8) |

### 5.3 Points « ultra-critiques » = pièges d'implémentation à désamorcer AVANT l'étape 8

> Aucun n'est un bug du squelette actuel. Ce sont des **chausse-trappes** qui, si on les découvre en
> codant, peuvent bloquer ou faire perdre des jours. Détail et « faire / ne pas faire » en §6.

| # | Risque | Gravité |
|---|---|---|
| C1 | **Soft-assert (D22) impossible à faire échouer un test depuis un `TestExecutionListener`** : ce hook **observe**, il ne **vote** pas le verdict | 🔴 Bloquant si non anticipé |
| C2 | **Lecture du `TestOutcome` Serenity depuis le listener JUnit** : `StepEventBus` est lié au thread du test ; en parallèle / au mauvais instant, l'outcome peut être absent | 🔴 Make-or-break de tout le domaine FAILURE |
| C3 | ~~**Collision de nom `TestExecutionResult`** (JUnit vs socle `internal.reporting`)~~ — ✅ **résolu** : type socle renommé `TestExecutionReport` (2026-06-25) | ✅ Levé |
| C4 | **Messages différenciés de synchro** (« mauvaise page » / « application instable ») = **contrat de fait** consommé par `qa-maintenance` (D9) | 🔴 Si écrits en texte libre, la classification d'échec casse silencieusement |
| C5 | **Second listener (reporting AUTO) à enregistrer dans le MÊME fichier services** | 🟠 Oubli ⇒ no-op silencieux (le piège que D16 voulait éviter) |
| C6 | **Scope Logback `runtime`→`compile` + règle ArchUnit = un duo indissociable** | 🟠 Basculer le scope sans le garde-fou expose le binding aux 17 projets |

---

## 6. Pièges d'implémentation (étape 8) — faire / ne pas faire

### C1 — Soft-assert : le listener ne peut pas faire échouer un test 🔴
Un `org.junit.platform.launcher.TestExecutionListener.executionFinished(...)` est appelé **après** que
le moteur a déjà figé le verdict du test. On **ne peut pas** transformer un test vert en rouge depuis ce
hook. Or D22 demande, en mode soft, d'agréger les échecs puis de **faire tomber le test en fin de test**.
- **Ne pas faire** : implémenter le « flush + échec » du collecteur soft-assert dans
  `TestFailureManager#executionFinished`. Ça écrira les artefacts mais **ne fera pas échouer** le test.
- **Faire** : trancher le mécanisme **avant de coder**. Le verdict doit être créé là où il peut encore
  l'être — une **extension JUnit Jupiter** (`afterEach` / `TestWatcher` / `InvocationInterceptor`) ou la
  sémantique `SoftAssertions.assertAll()`. Le `TestExecutionListener` reste cantonné à **observer**
  l'échec et **écrire** les artefacts. D22 note déjà « mécanisme exact à arrêter à l'impl » : le trancher
  est un **prérequis**, pas un détail.

### C2 — Lire le `TestOutcome` Serenity au bon endroit / bon thread 🔴
D16/D19 prévoient `StepEventBus…getCurrentTestOutcome()`. Mais `StepEventBus` est **ThreadLocal** au
thread du test ; le `executionFinished` JUnit peut s'exécuter sur un autre thread ou après que Serenity
a purgé l'outcome — surtout en **parallèle**. D16 marque déjà l'instant de lecture comme « à confirmer ».
- **Ne pas faire** : bâtir l'écrivain des 3 fichiers en supposant l'outcome toujours disponible dans
  `executionFinished`.
- **Faire** : **valider d'abord** sur un vrai test KO (séquentiel **et** parallèle) que l'outcome est
  bien lisible à l'instant choisi. Si `executionFinished` est trop tard, basculer sur un point Serenity
  (`testFinished`) ou capturer l'outcome plus tôt. **C'est le tout premier jalon de l'étape 8 pour le
  domaine FAILURE** — tout le reste (nommage, masquage, 3 fichiers) s'appuie dessus.

### C3 — Collision `TestExecutionResult` ✅ RÉSOLU (2026-06-25)
Le listener reporting (étape 8) sera lui-même un `TestExecutionListener`, dont le callback reçoit
`org.junit.platform.engine.TestExecutionResult` **et** qui doit construire le type valeur du socle.
Deux types **homonymes** dans une même classe auraient forcé des noms pleinement qualifiés.
- **Décidé & appliqué** : le type socle (`internal`, hors périmètre du gel) a été **renommé
  `TestExecutionResult` → `TestExecutionReport`** — la collision disparaît à la source. Aucun impact de
  compatibilité (type interne) ; références Java et docs (D13 / D15 / SFD / roadmap) répercutées.

### C4 — Messages de synchro = contrat de fait pour la maintenance 🔴
Au timeout, `SyncException` doit porter « mauvaise page / page non chargée » (élément absent) vs
« application instable » (présent mais jamais interactable). Ces chaînes alimentent
`failure-classification` et l'agent `qa-maintenance` (D9). Ce sont des **données contractuelles**, au
même titre que le format `KO__`.
- **Ne pas faire** : écrire ces messages en texte libre dispersé dans `fluentWait`/`WebSync`.
- **Faire** : les **centraliser en constantes** (un seul endroit), les **figer par un test** et les
  traiter comme un contrat de sortie versionné. Tout changement de libellé doit être conscient (il casse
  la classification en aval).

### C5 — Enregistrer le second listener dans le même fichier services 🟠
Le fichier `META-INF/services/org.junit.platform.launcher.TestExecutionListener` liste **une FQCN par
ligne**. Le reporting AUTO ajoutera un second listener.
- **Ne pas faire** : créer un nouveau fichier services (il écraserait / n'ajouterait pas) ou oublier
  d'y déclarer la classe ⇒ **aucune remontée, aucune erreur** (exactement l'« annotation oubliée » que
  D16 rejette).
- **Faire** : **ajouter la ligne** dans le fichier existant. Et rappeler que le **rename de package à
  l'adoption** (D17) doit mettre à jour ces FQCN — le refactor IDE le fait, mais à **vérifier** (le
  fichier services est du texte, pas du code).

### C6 — Scope Logback compile + ArchUnit = un duo 🟠
Pour que `LogbackConfigurator implements ch.qos.logback.classic.spi.Configurator` compile, `logback-classic`
doit passer **`runtime`→`compile`** (D16-bis). Mais en `compile`, le binding devient visible au compile
des 17 projets.
- **Ne pas faire** : basculer le scope **sans** poser simultanément la règle ArchUnit « aucune classe
  hors `internal.log` ne dépend de `ch.qos.logback..` ».
- **Faire** : livrer les deux **dans le même lot** (scope + règle ArchUnit + `dependenciesToScan` du
  Parent POM). Sinon le garde-fou contre le couplage au binding n'existe nulle part.

### Autres pièges ciblés (moindre gravité, mais coûteux si manqués)
- **Driver mobile/web résolu paresseusement** : `WebSync`/`MobileSync` n'ont pas de driver en champ
  (bien). À l'impl, résoudre le `WebDriver` Serenity du **thread courant à chaque appel** (dans
  `fluentWait`), **jamais** le stocker en champ au constructeur ⇒ sinon rupture du confinement parallèle
  (ENF-01/D19).
- **`within(By)`** : renvoyer une **nouvelle vue bornée** ré-résolvant des `By` relatifs, **sans**
  capturer de `WebElement` (sinon viol no-leak / `StaleElement`). C'est la pièce la plus subtile du
  domaine sync après `fluentWait`.
- **`Secret.value()`** : c'est le seul trou de masquage. Le test étape 7 doit prouver l'absence de clair
  dans les 3 artefacts **et** la console. Envisager une **règle ArchUnit** interdisant `Secret.value()`
  hors socle (le seul appel légitime est `type(By, Secret)` interne).
- **Excel chiffré (POI)** : le déchiffrement `.xlsx` (`EncryptionInfo`/`Decryptor`) est un point fragile.
  Tester **tôt** avec une fixture chiffrée réelle (BF-VAL-03) avant de bâtir le mapping commun par-dessus.
- **`getValue` 0-based hors en-têtes + jamais `null`** (BF-DATA-04) : off-by-one et `null` classiques ;
  couvrir par un test dédié.
- **`ExecutionStatus.IN_PROGRESS`** : valeur de l'enum que `TestExecutionReport` ne porte jamais
  (`publishStart` l'envoie « sans passer par l'enum »). Sans danger, mais à garder cohérent à l'impl pour
  ne pas semer le doute.

---

## 7. Checklist d'implémentation (ordre conseillé pour l'étape 8)

1. **FAILURE en premier** : valider C2 (lecture de l'outcome, séquentiel + parallèle) **avant** tout le
   reste du domaine ; c'est le socle de la capture d'échec et du masquage.
2. **Trancher C1 (soft-assert)** sur le papier avant de coder. ~~C3 (renommage)~~ ✅ fait : `TestExecutionReport`.
3. **SYNC** : `fluentWait` (moteur + absorption des exceptions transitoires) puis la surface ; figer les
   messages C4 en constantes + test.
4. **Logback** : scope compile + `Configurator` + `logback-socle.xml` + règle ArchUnit (duo C6) **en un
   lot**.
5. **DATA** : Excel/chiffré (fixture réelle tôt) + CSV + `getValue`.
6. **SECRET/MASK** : `masked()`/`sha256Prefix()` + test « zéro clair » + garde-fou éventuel sur `value()`.
7. **REPORTING AUTO** : second listener **ajouté au fichier services existant** (C5), résolution
   `@WithTag` (1er tag, opt-out silencieux), auth mot de passe via `SecretManager`.
8. Corriger en passant les **dérives doc** W1–W7.

---

## 8. Ce qu'il ne faut PAS conclure de cette revue
- Les `return null` / corps vides **ne sont pas** des défauts : c'est l'état attendu d'un squelette
  post-gel.
- La structure **n'a pas besoin d'être retouchée** avant l'étape 8 : packages, visibilités, héritage,
  modificateurs et signatures sont sains. Les actions sont **comportementales** (impl) et **éditoriales**
  (doc), pas structurelles.
