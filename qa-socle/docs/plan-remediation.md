# Plan de remédiation — actions correctives ordonnées (socle)

> **Rôle de ce document** : plan d'action **fin**, ordonné et **suivi dans le temps**, des corrections à
> mener sur le socle. Il se distingue de `roadmap.md` :
>
> | Document | Niveau | Contenu |
> |---|---|---|
> | `roadmap.md` | **macro** | les 10 étapes du projet (structure, gel d'API, pilote, release) |
> | **`plan-remediation.md`** *(ce fichier)* | **micro** | les corrections **issues d'analyses en profondeur** (campagne navigateur `qa-test`, revue externe, lecture du code) |
>
> **À tenir à jour au fil de l'eau** : ce fichier est la **référence de reprise de travail**, y compris
> depuis une conversation neuve. Toute session qui avance sur un item met à jour sa colonne **Statut**.
> Il ne remplace ni `decisions.md` (le *pourquoi*) ni `points-attention.md` (le *registre des risques*) :
> il dit **quoi faire, dans quel ordre, et où on en est**.

## D'où l'on part (contexte, 2026-07-16)

Le socle est en **étape 8**. Les mécanismes transverses (capture d'artefacts `KO__`, dump DOM brut au
`stepFailed`, masquage `password`, soft-assert, activation par ServiceLoader) sont **implémentés et
validés au navigateur**. Le domaine **SYNC web** est écrit (78 méthodes) mais sa **validation bout-en-bout
a révélé un défaut central**.

Trois sources ont alimenté ce plan :

1. **Campagne PHASE 1 de `qa-test`** (2026-07-15, Chrome + Firefox) : **25 tests, 8 KO identiques sur les
   deux navigateurs** → écarts **de niveau socle**, indépendants du navigateur. Après recoupement,
   **5 sont de vrais défauts socle**, 3 relèvent du test (T6, T10, T16 — traités côté `qa-test`).
2. **Revue externe** (`critique-technique-qa-template-qa-test.md`, analyse ChatGPT) : angles morts retenus
   → promus en **PA-10 à PA-14** dans `points-attention.md`. Points **écartés en connaissance de cause** :
   format de masquage `Secret` (D12 — env. de test + rotation CyberArk), collision d'artefacts à la
   seconde (probabilité nulle, doublon = même erreur), classification volontairement large.
3. **Lecture du code** (`AbstractSyncManager`, `SyncCondition`).

**Cause racine identifiée** — elle explique à elle seule 4 des 5 défauts : le moteur a été écrit contre la
sémantique **Selenium brute**, mais délègue en production à `WebElementFacade` (Serenity), qui **(a)**
impose sa propre attente interne (~4-5 s) masquant les profils `SyncTimeout`, et **(b)** lève ses **propres
types d'exception** que `SyncCondition.classify` ne connaît pas → remontée brute, sans polling ni
diagnostic. C'est l'exigence **C8 « une seule horloge »** qui n'est pas atteinte.

> ⚠️ **Angle mort de la suite unitaire** : `FluentWaitPollingTest` est **vert** parce qu'il nourrit `poll`
> avec des exceptions Selenium **brutes** simulées — jamais un vrai `WebElementFacade`. Le moteur est donc
> validé **contre un contrat que la production ne respecte pas**. Aucune couverture unitaire n'aurait
> attrapé ces défauts : seule la validation navigateur les révèle.

## Légende des statuts

- ⬜ **À faire** — non démarré
- 🔄 **En cours** — démarré, non terminé
- ✅ **Fait** — implémenté **et** testé
- 🔍 **À vérifier** — implémenté, en attente de validation (typiquement une campagne `qa-test`)
- ⏸️ **Bloqué / en attente** — dépend d'une décision ou d'un apport externe
- ❌ **Abandonné** — écarté (motif en note)

## Plan d'action ordonné

| # | Action | Criticité | Statut | Dépend de / note |
|---|---|---|---|---|
| **BLOC A — Rendre le moteur SYNC correct** *(chantier unique : même classe, même passe)* | | | | |
| A.1 | **Reprise « une seule horloge » (C8)** : neutraliser *réellement* l'attente interne de `WebElementFacade`. Débloque **L4, L5**. | 🔴 P0 structurant | ✅ | **D-1 tranchée : option 2-pragmatique.** Trois gestes : `NO_FACADE_CLOCK = 1L` (le `0` était réinterprété en 5 s) ; les **8 actions mutantes** passent par `actRaw` sur `WebElement` **brut** ; neutralisation via **`ConfigurableTimeouts`** (cf. §Découvertes, 1). Reporting Serenity **conservé** (`notifyScreenChange` réémis). |
| A.2 | **Étendre `SyncCondition.classify`** aux exceptions **Serenity** (`ElementShouldBeEnabledException`…) → absorption transitoire + diagnostic `[ABSENT]`/`[DISABLED]`. Débloque **T2, T8**. | 🔴 P0 | ✅ | Table `SERENITY_CONDITIONS` (`ElementShouldBePresent` → `ABSENT`, `ElementShouldBeEnabled` → `DISABLED`). **Jamais `TimeoutException` nu.** Couvert par `SyncConditionClassifyTest` (8 cas). |
| A.3 | **Uniformiser les prédicats par `query()`** (contrat BF-SYNC-08 « ne lève jamais »). Corrige **T14**. | 🟠 P0 (facile) | ✅ | 9 prédicats routés ; `query` enveloppé dans `withoutImplicitWait` → **T14 : 12 124 ms → 25 ms**. |
| A.4 | **Injecter `Clock` / `Sleeper`** (PA-14) : tests moteur déterministes et instantanés. | 🟡 P2 (fort levier) | ⬜ | **Reporté après campagne** (décision : ne pas retoucher `poll` avant d'avoir prouvé A.1–A.3). Effet de bord utile : tests rapides → exécutables en CI → **débloque la couverture Sonar**. |
| A.5 | **Revalidation navigateur** L3 / L4 / L5 / T2 / T8 / T14 (campagne `qa-test`). | — | ✅ | **Chrome, 2026-08-02** : les 6 défauts socle corrigés (détail §Résultats). Firefox non rejoué — écarts précédemment identiques sur les deux navigateurs. |
| A.15 | **Écarts T43 / T76 révélés par `qa-test`** : (1) `count` / `getTexts` / `within(…)` subissaient l'implicit wait sur un élément absent (~3 s au lieu de quelques ms) → `findAll` passe sous `withoutImplicitWait`, comme `query` ; (2) `setWindowFocus(By)` ne résolvait jamais son locator (faux succès sur absent) → résolution forcée dans `fluentWait`. | 🟠 P1 | 🔍 | **2026-10-18.** Pour (2), option A retenue : signature inchangée, le `By` est une condition de présence (`SyncException [ABSENT]` au timeout) ; le focus porte sur la fenêtre (Javadoc réécrite). Écartées : `setWindowFocus()` sans `By`, retrait de la méthode (changements d'API). Couvert par `CollectionsTest` (3 cas) et `SetWindowFocusTest` (2 cas), via le faux driver `RecordingDriver` injecté par `ThucydidesWebDriverSupport.useDriver`. **Reste** : T42–T44, T75, T76 dans `qa-test`, Chrome et Firefox. |
| **BLOC B — `pageLoadTimeout`** | | | | |
| B.6 | **Corriger la portée du marqueur** (PA-10) **et trancher le point d'application** (PA-8). | 🟠 P1 | ✅ | **D-2 tranchée : option (a).** Le marqueur retient désormais **l'identité du driver** (`pageLoadTimeoutAppliedTo`) → replacé si Serenity recrée le driver. Limite assumée et documentée : navigation antérieure au 1ᵉʳ contact `WebSync` non couverte (défaut W3C 300 s). |
| **BLOC C — Lot 2c restant** | | | | |
| C.7 | **Sentinelle flag JS** de détection de re-render (emplacement réservé dans `act()`) **+ faire passer toutes les mutations par `act()`** (PA-12). | 🟠 P1 | ⏸️ | Mécanisme de référence **compris** (archives ancien socle) : drapeau `window.__f` + hook réseau `__activeCalls` (XHR/fetch) → détecte l'async malgré `readyState=complete`. **Non systématique** (pas de coût à vide). PA-12 partiellement absorbé : les 8 mutantes passent déjà par le point unique `actRaw`. |
| **BLOC D — Durcissements moteur** | | | | |
| D.8 | **Purger le `ThreadLocal` de capture DOM même non consommé** (PA-11). | 🟠 P1 | ✅ | Purge à l'**ouverture** de chaque test (3 surcharges `testStarted` + `testRetried`) — **pas** en fin de test : l'ordre `testFinished` (Serenity) / `executionFinished` (JUnit) n'est pas contractuel, purger là risquerait d'effacer le DOM avant écriture. Couvert par `RawDomCaptureLifecycleTest`. |
| D.9 | **Validation d'entrées** (PA-13) : `parseDuration` (`NumberFormatException` brute), `null`, durées ≤ 0 → exception métier lisible. | 🟡 P2 | ⬜ | — |
| **BLOC E — Domaine DATA** | | | | |
| E.10 | **Implémenter `DataFileManager`** : CSV `;` (`ID;nom;prenom;num_contrat;date_naissance;date_debut_contrat;sexe;ville`), tokens de date relatifs (`m+1`, `j+0`, `a-34`), **ID auto-incrémenté** si plusieurs lignes ; Excel POI (try-with-resources, chiffré, traduction en `DataFileException`). | 🟠 P1 | ⏸️ | **Référence** : dossier local d'archives de l'ancien socle (`CSVFile.java`, `ExcelFile.java`, `FonctionsCommunes*.java`) — **JAMAIS commité** (gitignoré) |
| **BLOC F — Hygiène & garde-fous** | | | | |
| F.11 | **Réalignement documentaire** (statuts « squelette » périmés, roadmap, niveau de preuve réel) **+ ArchUnit** (garde-fous D19/α). | 🟡 P2 | 🔄 | Passe documentaire **faite** le 2026-08-03, **refaite le 2026-10-17** (réorganisation par fonctionnalité, F.13). **Reste ArchUnit**, dont une règle « un PageObject n'appelle pas `$()`/`find()`/`element()` » — seul moyen de *détecter* le contournement que F.12 rend seulement moins tentant. |
| F.12 | **`QaPageObject`** : classe de base fournissant `web` (MEDIUM) + `web(SyncTimeout)` aux PageObjects consommateurs. | 🟡 P2 | ✅ | Non prévu initialement. Supprime ~9 lignes de *boilerplate* par page, dont une **init paresseuse inutile** (`WebSync` ne capture aucun driver). Oubli **impossible en silence** : sans l'héritage, `web` n'existe pas → erreur de compilation (≠ piège D16). Validé au navigateur. **Élargit l'API publique** → périmètre japicmp. |
| F.13 | **Réorganisation par fonctionnalité** + resserrement des modificateurs Java (D15 réécrite). | 🟠 P1 (avant 1.0.0) | ✅ | **2026-10-17**, branche `feature/package-by-feature` : 46 fichiers déplacés (`git mv`), implémentations package-private, `AbstractDataFileManager` `sealed`, exceptions réparties par fonctionnalité (racine abstraite, sous-types `final`), soft-assert fusionné dans `sync`, plus aucun membre `protected` dans le moteur. Build vert, **70 tests**. **À faire côté `qa-test`** : mettre à jour les imports. Résidu à arbitrer : **PA-18** (contournement du package-private). Façades `WebSync` / `MobileSync` rendues `final` (pas de surcharge côté projet). |
| F.14 | **Actions JavaScript** `clickViaJS` / `typeViaJS` sur `WebSync` (D3 maj, BF-SYNC-17). | 🟡 P2 | ✅ | **2026-10-17.** Pour les composants dont l'action native est sans effet. Passent par `actRaw` (10 actions mutantes). Vérifiées dans Chrome sur une page jetable ; **reste à valider sur de vrais frameworks dans `qa-test`** (non modifié pour l'instant). |
| **BLOC G — Backlog différé** | | | | |
| G.12 | **CyberArk**, **reporting AUTO**, **Logback**, gestes **MobileSync**. | 🟡 P2 | ⏸️ | Différé par décision. **Arbitrage à ressortir** au moment du reporting (voir §Décisions, D-3) |
| **BLOC H — Clôture** | | | | |
| H.13 | **Étapes 9-10** : pilote, **japicmp élargi** (C7 : activer `includeSynthetic` et **valider sur un retrait volontaire** d'une méthode du moteur ; repli : inclure explicitement `AbstractSyncManager`), release **1.0.0**. | 🟠 P1 avant release | ⬜ | En fin de séquence |

## Ce que la campagne a mis au jour (2026-08-02)

### 1. La cause racine réelle : Serenity a son propre implicit wait

Le passage à `1L` ne suffisait pas. **`WebDriverFacade` (Serenity) maintient son *propre* implicit
timeout**, au-dessus du driver natif — champ interne + interface `ConfigurableTimeouts`. Or c'est
**celui-là** que consulte la résolution d'élément. Régler `driver.manage().timeouts().implicitlyWait(0)`
ne le touchait pas : la couche Serenity continuait d'attendre ses 3 s. Conséquences observées :

- chaque prédicat coûtait ~3 s → **T14 mesuré à 12 124 ms** pour quatre prédicats ;
- une action **absorbait** l'attente au point de faire **réussir** un cas qui devait expirer (L4, L5) —
  d'où le diagnostic initial erroné « profils non appliqués ».

**Correctif** : `withoutImplicitWait` neutralise désormais via **`ConfigurableTimeouts`** (API Serenity),
avec repli si le driver n'est pas Serenity. *Aucun test unitaire ne pouvait révéler ce point : seule
l'exécution navigateur l'a exposé — illustration directe de PA-7.*

### 2. Une régression introduite par le passage au brut, puis corrigée

`WebElementFacade.click()` appelait `waitUntilEnabled()` avant d'agir. Le `WebElement` natif, lui,
**accepte silencieusement** un clic sur un `<button disabled>` : le navigateur n'exécute rien et ne lève
rien. Le socle rendait donc un **faux succès** — plus nuisible qu'un échec franc. Correctif :
`requireEnabled()` dans `actRaw`, qui lève une exception **classifiable** (`DISABLED`) absorbée par le
polling → diagnostic `[DISABLED]` au timeout. Garde appliquée **aux 8 actions mutantes** (point de
passage unique `actRaw`), donc légèrement **plus stricte que Serenity**, qui ne vérifiait l'état que pour
`click` — écart volontaire, au bénéfice de l'uniformité.

## Résultats de la campagne (Chrome, 2026-08-02)

| # | Avant | Après |
|---|---|---|
| **L4** | aucune exception (profil ignoré) | ✅ `SyncException` en **2123 ms** `[ABSENT]` |
| **L5-SHORT** | aucune exception | ✅ `SyncException` en **1069 ms** `[ABSENT]` |
| **L5-MEDIUM** | abandon à ~4 s au lieu de 14 s | ✅ `click OK en 14144 ms` |
| **T2** | `ElementShouldBeEnabledException` brute | ✅ `SyncException [ABSENT]` |
| **T8** | exception Serenity brute | ✅ `SyncException [DISABLED]` |
| **T14** | fuite `NoSuchElement` | ✅ **25 ms**, tous prédicats à `false` |

**Non-régression** : `BornesLimitesTest` 14 tests / 0 échec, artefacts 4/4 (DOM réel, **0 occurrence** du
mot de passe en clair), T7/T9/T17/T22/T24 inchangés.

**KO restants — tous côté `qa-test`, aucun défaut socle** : **T6** (le test navigue sans solliciter le
socle), **T10** (auto-scroll : `OUT_OF_BOUNDS` inatteignable comme cause d'un clic ; reste valide comme
code *latent*), **T16** (le test attend `SyncException` alors que le contrat impose `AssertionError`).

> ⚠️ **Piège de campagne** : les tests aux limites exigent des `-D` **contradictoires** (L4 veut
> `medium=2s`, L5-MEDIUM veut les 15 s réels). Une invocation unique en configuration par défaut ne peut
> donc pas tous les valider — elle produit des « échecs » qui n'en sont pas. Toujours **répartir** en runs
> ciblés, et nettoyer `target/qa-results` avant chaque campagne.

## Décisions tranchées

### D-1 — Recette du C8 → **option 2-pragmatique** *(tranchée 2026-08-02)*

Les **8 actions mutantes** (`click`, `type`×2, `typeAndEnter`, `typeAndTab`, `clear`, `doubleClick`,
`contextClick`) agissent sur `WebElement` **brut** ; les **52 autres** méthodes restent sur la facade —
l'audit bytecode prouve qu'elles ne portent **aucun** mécanisme d'attente propre. Motif : `WithRetries`
(12 tentatives × 250 ms) est une **constante compilée**, non désactivable autrement qu'en ne l'invoquant
pas. Serenity n'apportant ici aucune capacité que Selenium n'ait (compositions de `clear`/`sendKeys`/
`Keys`/`Actions`), la reproduction est fidèle et le reporting est réémis explicitement.

### D-2 — Contrat du `pageLoadTimeout` → **option (a)** *(tranchée 2026-08-02)*

Protection **à partir du 1ᵉʳ contact `WebSync`**, marqueur corrigé pour suivre **l'identité du driver**.
Options écartées : **(b)** aucune prise Serenity exploitable (pas de clé de page-load timeout, driver créé
paresseusement) ; **(c)** `WebSync.openUrl()` réintroduirait un mécanisme **opt-in** que D16 rejette.
Enjeu résiduel chiffré : 300 s (défaut W3C) au lieu de 120 s, sur la **seule** première navigation.

### D-3 — Fail-fast des coquilles vs reporting AUTO *(à ressortir au bloc G)*

La règle « toute coquille lève » vaut pour `data`/`secret` (appelées via factory). Elle **ne peut pas**
s'appliquer telle quelle au **reporting AUTO** : `publishStart`/`publishEnd` sont invoquées par un listener
sur **chaque** test → lever **casserait tous les tests**. État correct visé : **inerte et gaté**
(`qa.reporting.enabled`), pas `throw`.

## Règles de séquencement

- **Ne pas relancer de campagne `qa-test` avant la fin du bloc A** : les 5 écarts socle resteront rouges
  par construction — c'est **attendu**, ce n'est pas une régression.
- **A.1 et A.2 vont ensemble** : corriger la seule classification sans l'horloge donnerait des messages
  corrects sur un moteur toujours faux (« faux correctif »).
- **A.3 est détachable** : il peut être livré seul, sans attendre l'arbitrage D-1.
- Côté `qa-test`, les corrections de tests (T6, T10, T16) et d'hygiène (secret loggé, traçabilité du SHA
  socle) sont décrites dans `qa-test/docs/qa-test-briefing-erreurs-phase1.md` — **hors périmètre de ce
  plan**, qui ne couvre que le socle.
