# Feuille de route — construction du socle QA

Suivi de la séquence de construction du squelette du socle (dépendance Maven consommée par ~17 projets).

**Fil conducteur** : figer les règles du contrat (1→5) → écrire le contrat (6→7) → le remplir et le
tester (8) → le valider sur un pilote (9) → le protéger et le livrer (10). **Les étapes 1→7 restent
SANS implémentation** (coquilles typées).

## Légende des statuts

| Statut | Signification |
| --- | --- |
| ⬜ À faire | Non démarré |
| 🟡 En cours | Démarré, partiel |
| ✅ Fait | Terminé et validé |
| ⛔ Bloqué | En attente d'une décision / dépendance externe |

## Tableau de bord

| # | Étape | Dépend de | Statut |
| --- | --- | --- | --- |
| 1 | Décider la convention de visibilité (public vs interne) | — | ✅ Fait (D15) |
| 2 | Politique de versioning & compatibilité (D6 historique remplacée par D6-bis) | 1 | 🟡 En cours (D6-bis actée, Wrapper + Enforcer en place) |
| 3 | Stratégie transverse d'erreurs | — (avant 6) | ✅ Fait (D18) |
| 4 | Configuration & portée/thread-safety des composants | — (avant 6) | ✅ Fait (D19) |
| 5 | Stratégie de test du socle | — (avant 8) | ✅ Fait (D20) |
| 6 | Signatures d'API par composant (coquilles typées) + gel de l'API | 1→5 | 🟡 En cours (sync/data/secret/reporting/log signés ; failure, factories, arbitrages API/Logback + gel restent) |
| 7 | Cas sécurité : masquage des secrets | 6 | 🟡 En cours (politique/signatures cadrées ; impl/tests non faits) |
| 8 | Implémentation, composant par composant (+ tests) | 5→7 | ⬜ À faire |
| 9 | Validation pilote sur SNAPSHOT/RC + ajustements | 8 | ⬜ À faire |
| 10 | Garde-fou compat (japicmp/revapi) + version 1.0.0 + doc + généralisation | 9 | ⬜ À faire |

> **Pourquoi cette séquence** (points d'ordonnancement non triviaux) :
> - le **garde-fou de compatibilité** (japicmp/revapi, étape 10) est **après la 1ʳᵉ release** : il
>   compare l'API courante à une **baseline = dernière version publiée**, qui n'existe pas avant ;
> - la **validation pilote** (étape 9) se fait sur **SNAPSHOT/RC avant** de figer `1.0.0` : on n'ajuste
>   pas une release déjà figée ;
> - la **stratégie de test** (étape 5) précède l'implémentation : sans elle, « + tests » (étape 8)
>   n'est pas actionnable ;
> - le **gel de l'API** est en fin d'étape 6, jamais avant : on ne date pas le gel d'une API pas écrite.

---

## Étape 1 — Décider la convention de visibilité (public vs interne)

**Objectif** : choisir **où** vivra le code exposé aux 17 projets vs le code interne, et **comment** on
marque cette séparation. C'est une décision de **structure d'accueil** (au niveau des **types/packages**),
PAS le contenu des classes ni les signatures — donc légitime **avant** d'écrire le moindre corps de
méthode. La placer ici évite un déplacement coûteux de toutes les classes une fois remplies.

> ⚠️ Ce n'est **pas** un « gel » : on ne fige aucune signature ici (l'API n'existe qu'à l'étape 6, où
> se fait le vrai gel). On pose seulement le **plan des zones** public/interne.

**Statut : ✅ Fait** — actée en **[D15](decisions.md)**, build vert après déplacement des 13 types.

- [x] Convention retenue : sous-arbres `com.example.qa.api.<domaine>` / `internal.<domaine>`, frontière
      contrôlée par outil (japicmp étape 10), **sans JPMS**.
- [x] Types existants classés api/internal (cf. tableau D15) : interfaces + `WebSync`/`MobileSync` en
      `api` ; abstraits, impls et `TestFailureManager` en `internal`.
- [x] Décision actée dans `decisions.md` (D15).

---

## Étape 2 — Politique de versioning & compatibilité (D6 amendée par D6-bis)

**Objectif** : compléter ce qui n'est pas déjà tranché par **D6 + D6-bis** (Parent POM + **version
épinglée explicite** — abandon de `RELEASE`, supprimé en Maven 4 — + SemVer + garde-fous + mécanisme
d'échappement). Découle de l'étape 1 (on ne versionne que ce qui est public).

**Statut : 🟡 En cours** — *cœur acté en [D6-bis](decisions.md) ; D6 est historique/remplacée ; Wrapper +
Enforcer en place ; ne reste que ci-dessous*

- [x] SemVer + Parent POM + **version épinglée** (abandon du mot-clé Maven `RELEASE`) + mécanisme d'échappement → **acté en D6-bis**.
- [x] Maven Wrapper 3.9.9 + `maven-enforcer-plugin` (`requireMavenVersion`, `requireJavaVersion`,
      `banDynamicVersions`, `bannedDependencies`) → **en place (D6-bis)**.
- [ ] Règle de dépréciation : `@Deprecated` + maintien sur **N** versions (fixer N).
- [ ] Qui publie une version et **sur quel critère** (cf. `artifactory-jfrog-settings`).
- [ ] Non-régression : **quels tests** = tests unitaires socle + projet consommateur dédié (acté
      **D20**) ; reste **comment elle bloque la release** (gate, lié à la CI Jenkins — D7).

> La **date de gel** de l'API n'est PAS ici : l'API n'existe qu'à l'étape 6 → gel rattaché à sa fin.

---

## Étape 3 — Stratégie transverse d'erreurs

**Objectif** : hiérarchie d'exceptions maison du socle, checked vs unchecked. Préalable aux signatures
(toute méthode publique déclare ce qu'elle lève). À figer avant l'étape 6.

**Statut : ✅ Fait** — actée en [D18](decisions.md) ; 5 classes en `api.exception` + test de
hiérarchie, build vert.

- [x] Exception racine `QaToolkitException` (concrète, `api.exception`) — **unchecked**.
- [x] Sous-types par domaine : `SyncException`, `DataFileException`, `SecretException`, `ReportingException`.
- [x] Unchecked tranché et justifié (stack unchecked + conversion des exceptions tierces *checked* à la
      frontière ; traduction en `cause`) — cf. D18.

---

## Étape 4 — Configuration & portée/thread-safety des composants

**Objectif** : où chaque composant lit sa config et quelle est sa **portée/durée de vie** à l'exécution
(par test / thread / global), d'où la **thread-safety** vu le parallélisme Serenity. Conditionne la forme
des constructeurs, donc les signatures. (« Cycle de vie » ≠ publication, cf. D6.)

**Statut : ✅ Fait** — actée en [D19](decisions.md).

- [x] Source de config : **100 % Serenity** (`serenity.conf` / `serenity.properties` + system props),
      socle **agnostique du fichier** ; aucune couche maison.
- [x] Portée par composant (WebSync par instance/driver du thread ; data par usage ; secret/reporting
      singleton stateless ; log d'action sans état — SLF4J natif ; TestFailureManager sans état + dossier unique).
- [x] Thread-safety : règles actées (aucun `static` mutable partagé, état par test délégué à Serenity —
      pas de buffer maison, immuabilité, confinement driver) + choix de l'**option α** acté
      (ArchUnit dans `qa-socle`, Surefire `dependenciesToScan` piloté par le Parent POM). Mise en place
      technique prévue en étape 8.

---

## Étape 5 — Stratégie de test du socle

**Objectif** : décider **comment** on teste des composants à dépendances externes, **avant** d'écrire
l'implémentation — sinon « + tests » (étape 8) n'est pas actionnable. Définit aussi la base de la
non-régression exigée par D6 (étape 2).

**Statut : ✅ Fait** — actée en [D20](decisions.md).

- [x] **Projet consommateur dédié** comme test principal : exerce le socle contre un **site bidon**
      (vrai navigateur pour `WebSync` : absences d'éléments, locators changés, timeouts).
- [x] Stratégie retenue pour `CyberArkApiClient` / `AlmApiClient` : **WireMock** (pas de serveur réel).
- [x] Stratégie retenue pour `ExcelFileReaderWriter` / `CsvFileReaderWriter` : **fixtures** de fichiers
      (Excel chiffré inclus).
- [x] Stratégie retenue pour `TestFailureManager` (depuis le `TestOutcome` Serenity) : production des
      artefacts + **masquage** (type `Secret`) vérifiés.
- [x] Stratégie retenue pour la **CI de non-régression** = tests unitaires socle + projet consommateur dédié, contre chaque
      version avant publication (réf. étape 2) — pas de socle isolé.

---

## Étape 6 — Signatures d'API par composant (coquilles typées) + gel de l'API

**Objectif** : passer des classes vides aux vrais contrats. Méthodes, paramètres, types de retour,
exceptions — **sans corps**. Le vrai moment de conception. S'appuie sur 1→5.

**Statut : 🟡 En cours** — *coquilles typées en place pour `sync`, `data`, `secret`, `reporting` et
`log` ; restent `failure`, les factories `secret`/`reporting` si retenues, l'arbitrage de frontière
API autour d'`AbstractSyncManager`, l'arbitrage technique Logback `compile`/`runtime`, puis le gel.*

- [x] **Factory publique `data`** : `DataFiles` existe comme coquille (`api.data`) et porte le point
      d'accès prévu vers `ExcelFileReaderWriter` / `CsvFileReaderWriter`.
- [x] **Factories publiques `secret` / `reporting`** — **tranché (D15)** : `secret` → factory
      **`SecretManagers`** créée (`api.secret`, `get()` neutre renvoyant le `SecretManager`, cache CyberArk) ;
      `reporting` → **pas de factory** (reporting **AUTO**, tout le domaine passe `internal`, cf. D13).
- [x] `WebSync` (+ `AbstractSyncManager` / `MobileSync`) — signatures de synchro/interaction.
- [x] `DataFileManager` (+ `AbstractDataFileManager` / `ExcelFileReaderWriter` / `CsvFileReaderWriter`) —
      signatures lecture/écriture data posées, corps volontairement en coquille.
- [x] ~~`QaLogger`~~ — **classe supprimée** (étape 6) : aucune valeur ajoutée vs SLF4J natif. Le log
      d'action de synchro est émis par `WebSync`/`MobileSync` via un `org.slf4j.Logger` natif sous le
      **namespace stable `"qa"`** (cf. D17/D14 corrigé). Masquage assuré par le type `Secret`.
- [x] **Log live (default socle)** — coquille de **`LogbackConfigurator`** (`internal.log`) existante,
      avec namespace `"qa"` + clé `qa.logger.level`.
- [x] **Arbitrage Logback** — **tranché (cf. D16-bis)** : `logback-classic` passe en scope **`compile`**
      (pour que `LogbackConfigurator` implémente `ch.qos.logback.classic.spi.Configurator`), et le couplage
      au binding côté consommateur est interdit par une **règle ArchUnit** (D19/α : pas de dépendance à
      `ch.qos.logback..` hors `internal.log`) plutôt que par l'isolation de scope. Application étape 8
      (scope + `Configurator` + resources `logback-socle.xml` / `META-INF/services/...` + règle ArchUnit).
- [ ] `TestFailureManager` — capture d'échec (**classe simple**, D5 ; cf. **D16** + **D16-bis**) :
  - [x] activation **native** via ServiceLoader **JUnit** : `TestFailureManager` implémente lui-même
        `TestExecutionListener` (hook + écriture en une classe simple), déclaré dans
        `META-INF/services/...` — **aucun type public, aucune annotation**. D16 corrigée (Serenity ne
        découvre pas les `StepListener` par ServiceLoader).
  - [ ] **écrire les 3 fichiers en Java** (`ERROR.log`/`FAIL.log` + dump HTML) depuis le **`TestOutcome`
        Serenity** (`getTestSteps()` + `TestStep.getException()`), en appliquant le masquage des
        valeurs sensibles — format/nommage identiques sur les 17 projets (cf. **D16-bis**) ; **Logback**
        ne gère que le *log live*, **Surefire** l'exécution/répertoire ;
  - [x] **clés de config figées** (constantes `TestFailureManager`, défauts inclus), unifiées sous
        `qa.failure.artefacts.*` : `.enabled` (opt-out), `.outputDir`, `.dumpHtml`. Le `{ENV}` du
        nommage `KO__` est lu de l'environnement Serenity actif (`environment`, D19) — pas de clé dédiée ;
  - [x] **contrat de sortie gravé** (versionné, cf. D8) : `KO__{ENV}__{NomDuTest}__{ts}/` (délimiteur
        `__`) + `ERROR.log` / `FAIL.log` / dump HTML. Modifier le format = breaking change.
- [x] `SecretManager` (+ `CyberArkApiClient`) — signatures de récupération de secrets posées.
- [x] `Secret` — contrat public de **valeur sensible** posé (`of`, `value`, `masked`,
      `sha256Prefix`, `toString`) ; implémentation du masquage en étape 7/8.
- [x] `ReportingManager` (+ `AlmApiClient`, `ExecutionStatus`, `TestExecutionResult`) — signatures
      arrêtées : `publishStart(String almTestId)` + `publishEnd(TestExecutionResult)`. **Reporting AUTO**
      (listener du socle) → **tout le domaine en `internal.reporting`**, aucun type public (cf. D13/D15).
- [x] **Frontière API `sync`** — **tranché (option A, cf. D15)** : `AbstractSyncManager` reste
      `internal`, mais ses méthodes publiques (héritées par `WebSync`/`MobileSync`) sont **gelées** →
      garde-fou japicmp **élargi** à l'étape 10. Façades **sous-classables / méthodes non-`final`** →
      échappatoire « bris de glace » (mode opératoire dans le Javadoc des façades).
- [ ] **Fixer la date de gel de l'API** une fois toutes les signatures arrêtées — **y compris les
      signatures de masquage du type `Secret`** (réf. étapes 2 et 7).

---

## Étape 7 — Cas sécurité : masquage des secrets

**Objectif** : la **politique** de masquage des secrets (jamais de secret en clair dans logs/dumps),
son **intégration interne** et ses **tests**. Isolé car risque OWASP. ⚠️ **Aucune nouvelle signature
publique ici** : la seule signature concernée (contrat « valeur sensible » du type `Secret`) est
figée en **étape 6, avant le gel**.

**Statut : 🟡 En cours** — la politique et les signatures publiques sont cadrées ; l'implémentation et
les tests ne sont pas faits.

- [x] Définir la **règle de masquage** : quoi masquer, comment (**format acté D12** : 2 premiers
      caractères + masque + 16 hexa SHA-256), à quel moment (en amont de toute écriture) — `Secret` / D12 / D16-bis.
- [ ] Implémenter le **masquage à la source** dans le type `Secret` (`masked()` / `sha256Prefix()` /
      `toString()`) — **sans changer sa signature publique** (figée en 6).
- [ ] Intégrer le masquage dans `TestFailureManager` (`internal`, dumps/logs d'échec).
- [ ] **Tests** : aucun secret en clair dans les 3 artefacts `KO__` ni dans le log live.

---

## Étape 8 — Implémentation, composant par composant

**Objectif** : remplacer chaque squelette typé par sa logique, avec de vrais tests comportementaux
(selon la stratégie de l'étape 5) qui remplacent les tests de hiérarchie triviaux actuels.

**Statut : ⬜ À faire** — *première étape AVEC implémentation*

- [ ] `WebSync` (cœur : fluentWait + flag JS) + tests comportementaux.
- [ ] `DataFileManager` (Excel/CSV) + tests.
- [ ] `TestFailureManager` (depuis le `TestOutcome` Serenity) + tests.
- [ ] `LogbackConfigurator` (`internal.log`) implémente `Configurator` + **passer `logback-classic` en
      scope `compile`** (cf. D16-bis) + resources `logback-socle.xml` /
      `META-INF/services/ch.qos.logback.classic.spi.Configurator` (default socle) + test :
      default appliqué sans `logback.xml` local, surcharge locale prioritaire.
- [ ] **Garde-fous ArchUnit (D19/α)** dans `qa-socle` + config Surefire `dependenciesToScan` dans le
      Parent POM (héritée par les consommateurs) : (1) « pas de champ `static` non-`final` » ;
      (2) **interdiction de dépendre de `ch.qos.logback..` hors `internal.log`** (cf. D16-bis).
- [ ] `CyberArkApiClient` + tests ; **factory `SecretManagers.get()`** branchée sur l'impl.
- [ ] `AlmApiClient` + tests ; **listener reporting AUTO** (`TestExecutionListener` lisant `@WithTag`
      → `publishStart`/`publishEnd`, 1er tag si plusieurs, non remonté si aucun).

> **Mobile différé (hors périmètre étape 8)** — `MobileSync` n'est **pas** implémenté ici : on ne se
> fait pas ralentir par un pilote mobile inexistant. Ses **signatures sont déjà gelées** (étape 6) et sa
> surface publique s'appuie largement sur celle de `WebSync` (moteur commun `AbstractSyncManager`) ;
> seule l'implémentation des **gestes spécifiques mobile** reste à faire, **reportée** au démarrage d'un
> pilote mobile (étape ultérieure). `WebSync` ci-dessus = **web uniquement**.

---

## Étape 9 — Validation pilote sur SNAPSHOT/RC + ajustements

**Objectif** : valider l'API et l'implémentation sur **1 projet pilote** parmi les 17 **avant** de
figer une release. On reste sur **SNAPSHOT (ou Release Candidate)** pour pouvoir ajuster l'API sans
brûler de numéro de version.

**Statut : ⬜ À faire**

- [ ] Publier un **SNAPSHOT / RC** sur Artifactory (cf. `artifactory-jfrog-settings`).
- [ ] Intégrer sur **1 projet pilote** et exécuter des tests réels.
- [ ] Recueillir le retour terrain et **ajuster l'API** tant qu'elle n'est pas figée.
- [ ] Verrouiller l'API quand le pilote est concluant (entrée de l'étape 10).

---

## Étape 10 — Garde-fou compat + version 1.0.0 + doc + généralisation

**Objectif** : verrouiller la compatibilité, publier la **première release figée**, documenter pour
les consommateurs, puis généraliser. Le garde-fou de compatibilité n'a de sens qu'**à partir** d'une
baseline publiée — d'où sa place ici (et non avant la 1ʳᵉ release).

**Statut : ⬜ À faire**

- [ ] Brancher **japicmp/revapi** (périmètre = API publique de l'étape 1) et faire **échouer le build**
      sur breaking change non intentionnel ; baseline = la release publiée ci-dessous.
- [ ] Publier la **version 1.0.0** sur Artifactory.
- [ ] Fournir la **doc consommateur** : guide de démarrage + Javadoc de l'API publique (au-delà de
      `decisions.md`).
- [ ] **Généraliser** aux autres consommateurs (encore en 1.x, retour terrain absorbé en mineur/patch).
