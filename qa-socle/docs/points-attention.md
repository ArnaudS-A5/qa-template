# Points d'attention — risques et vigilances non couverts par les décisions

> **Rôle de ce document** : tracer les points levés lors des passes d'implémentation/revue qui ne sont
> **mentionnés nulle part ailleurs** (ni decisions.md, ni revue-socle.md, ni SFD). Chaque point porte un
> identifiant `PA-n`, un statut, et l'action attendue. À maintenir au fil de l'eau ; un point traité est
> soit **clos ici**, soit **promu** en décision (decisions.md) ou en piège (revue-socle.md).
>
> Créé le 2026-07-12 (passe doc/code post-implémentation du masquage DOM et de l'infra soft-assert).

| # | Statut | Point |
|---|---|---|
| PA-1 | ⬜ Ouvert | Soft-assert : extension non détectée = échecs avalés |
| PA-2 | ⬜ Ouvert (assumé) | Masquage DOM : périmètre résiduel du blanking password |
| PA-3 | ⬜ Ouvert | Preuve d'activation soft-assert à rejouer à chaque bump JUnit |
| PA-4 | ⬜ Ouvert (mineur) | Lecture de la config Serenity à chaque appel |
| PA-5 | ⬜ Ouvert (mineur) | Table CLAUDE.md/README : activation `log` documentée avant l'implémentation |
| PA-6 | ⬜ Ouvert (assumé) | Sondes JS (interception, bounds) : best-effort, iframe/shadow DOM non garantis |
| PA-7 | ⬜ Ouvert (confirmé) | Surface SYNC non testable unitairement → validation `qa-test` **— démontré au sens fort (PA-15)** |
| PA-8 | ⬜ Ouvert (assumé) | `pageLoadTimeout` : 1ʳᵉ navigation non couverte — contrat **(a)** acté, enjeu chiffré 300 s vs 120 s |
| PA-9 | ⬜ Ouvert | Diagnostic `within` : distinction container/child portée par la `cause` (message actif = `ABSENT` global) |
| PA-10 | ✅ Clos (2026-08-02) | `pageLoadTimeout` : marqueur désormais lié à **l'identité du driver** — (revue ChatGPT) |
| PA-11 | ✅ Clos (2026-08-02) | Capture DOM : `ThreadLocal` purgé à l'**ouverture** de chaque test — (revue ChatGPT) |
| PA-12 | 🔄 Partiel | Actions mutantes : les 8 passent par le point unique `actRaw` ; `submit` reste sur la facade — (revue ChatGPT) |
| PA-13 | ⬜ Ouvert (mineur) | Validation d'entrées absente (`parseDuration` brut, `null`, durées ≤ 0) — (revue ChatGPT) |
| PA-14 | ⬜ Ouvert (durcissement) | Horloge/sleeper non injectés (testabilité moteur) — reporté après la campagne — (revue ChatGPT) |
| PA-15 | ⬜ Ouvert (assumé) | Le socle dépend de **comportements internes Serenity** non contractuels → à rejouer à chaque bump |
| PA-16 | ⬜ Ouvert (assumé) | Pas de dump `{step}.html` quand l'échec survient **hors `@Step`** |
| PA-17 | ⬜ Ouvert | `QaPageObject` n'**empêche** pas l'usage de l'API Serenity native, qui court-circuite la synchro |
| PA-18 | ⬜ Ouvert (assumé) | Le package-private protège le **compilateur**, pas le runtime : split package et réflexion le contournent |

---

## PA-1 — Soft-assert : si l'extension n'est pas détectée, les échecs soft sont avalés 🔴

Le mode soft repose sur **deux maillons** : la clé `qa.assertions.soft=true` (les `should…` collectent au
lieu de lever) **et** l'extension `SoftAssertionExtension` (draine et fait échouer en fin de test). Si le
**second maillon manque**, les échecs collectés ne sont **jamais drainés** : le test passe **vert à
tort**, sans erreur ni avertissement — et les `AssertionError` restent dans le `ThreadLocal` du worker
(fuite vers le test suivant, jusqu'au premier drain).

Deux scénarios réalistes où l'extension n'est pas active :

1. **Exécution directe depuis l'IDE** (IntelliJ/Eclipse lancent JUnit sans passer par Surefire) : le
   paramètre `junit.jupiter.extensions.autodetection.enabled=true` posé dans la **config Surefire** du
   Parent POM **ne s'applique pas**. L'IDE ne voit pas les `configurationParameters` Maven.
2. **Consommateur qui override la config Surefire** du parent (une `<configuration>` enfant remplace
   celle du parent sauf `combine.*` explicite) : l'autodetection saute silencieusement.

**Pistes (à arbitrer)** : livrer un `junit-platform.properties` dans le jar `qa-socle` (couvre l'IDE,
mais **entre en collision** avec un éventuel `junit-platform.properties` du consommateur — un seul est
lu) ; ou self-check dans `SoftAssertions.check()` (détecter un résidu non drainé au check suivant et
échouer bruyamment) ; ou documentation consommateur explicite. Même famille de piège que **C5**
(no-op silencieux, l'« annotation oubliée » que D16 rejette).

## PA-2 — Masquage DOM : périmètre résiduel du blanking password 🟠 (assumé)

`maskPasswordInputs` ne masque que la `value` des `<input type="password">`. Restent **en clair** dans
le dump `.html` si présents :

- un secret **reflété hors champ password** (span, JSON inline, `data-*`, état JS sérialisé dans un
  `<script>`) ;
- la **source de repli option C** (vue Serenity HTML-échappée : `&lt;input…` ne matche pas la regex) —
  le repli n'est donc **pas** couvert par le masquage ;
- le traitement est **regex, pas parser HTML** : une balise `input` très malformée peut échapper au
  motif (best-effort, suffisant pour un dump de diagnostic).

Compromis **acté** (D16 amendé 2026-07-12) en écartant le registre `ThreadLocal`. À réévaluer **si** le
pilote (étape 9) révèle des applications qui reflètent des secrets hors champs password.

## PA-3 — Preuve d'activation soft-assert à rejouer à chaque bump JUnit 🟡

L'activation de `SoftAssertionExtension` (autodetection ServiceLoader) a été **prouvée empiriquement**
le 2026-07-12 par un test jetable (soft-fail non drainé → test KO via l'extension). Comme la validation
C2/C11 (thread de `executionFinished`), cette preuve repose sur un comportement **non contractuel**
du moteur : elle doit être **rejouée à chaque bump `junit.jupiter.version`**. Piste : la pérenniser en
test automatisé via `EngineTestKit` (JUnit Platform Test Kit) plutôt qu'en test jetable manuel —
à trancher à l'étape 8 (avec le test de non-régression parallèle C11 déjà prévu).

## PA-4 — Lecture de la config Serenity à chaque appel 🟡 (mineur)

`SoftAssertions.check()` (via `isSoftEnabled()`) et `TestFailureManager` recréent
`SystemEnvironmentVariables.createEnvironmentVariables()` à **chaque** lecture de clé. Négligeable pour
`TestFailureManager` (chemin d'échec uniquement), mais `check()` sera appelé par **tous les `should…`**
en fonctionnement nominal. Optimisation interne libre (types package-private) : à mesurer au pilote avant
d'optimiser (pas de cache prématuré — la clé doit rester surchargeable par `-D` au run).

## PA-5 — Activation `log` documentée avant l'implémentation 🟡 (mineur)

La table des domaines (CLAUDE.md, README) affiche `log` : « Auto — ServiceLoader Logback » alors que ni
le fichier `META-INF/services/ch.qos.logback.classic.spi.Configurator`, ni `logback-socle.xml`, ni
l'implémentation de `Configurator` n'existent encore (prévus étape 8, D16-bis). C'est l'**intention
documentée en avance sur le code** — cas assumé « doc décrit la cible » ; à résorber mécaniquement quand
l'item Logback de l'étape 8 sera livré.

## PA-6 — Sondes JS du diagnostic (interception, bounds) : best-effort 🟠 (assumé)

Le diagnostic actif (`AbstractSyncManager#diagnose`) détecte `INTERCEPTED` via `document.elementFromPoint`
et `OUT_OF_BOUNDS` via `getBoundingClientRect` (JS injecté). Cas nominal (overlay classique dans le
document principal) couvert ; **edge cases non garantis** : élément dans une **iframe** (le JS ne traverse
pas la frontière — il faut `switchTo().frame` avant, limite commune à toute la surface), **shadow DOM**
(`elementFromPoint` renvoie le shadow host). Comportement documenté, à valider au navigateur (`qa-test`).

## PA-7 — Surface SYNC (80 méthodes) non testable unitairement 🟠 (confirmé au sens fort)

Seuls le cœur pur (`poll`, `parseDuration`, `resolveTimeout`, `tryAssert`, `SyncCondition.classify`) et la
résolution des profils sont testés **sans navigateur**. Tout le reste (résolution `WebElementFacade`,
sondes, délégation Serenity, `within`, prédicats, `should…` de bout en bout) exige un **vrai driver** → la
validation comportementale relève du **projet consommateur `qa-test`** (site bidon, navigateur réel,
ralenti pour observer les timings), conformément à D20. À faire au lot 2c.

**Confirmation empirique (2026-08-02)** — le point est plus fort qu'un simple « non testable » : la
campagne a révélé un défaut que **la suite unitaire ne pouvait pas voir**, et pire, **qu'elle validait à
tort**. `FluentWaitPollingTest` est vert parce qu'il nourrit `poll` d'exceptions Selenium **brutes**
simulées ; or en production le moteur reçoit des types **Serenity** et subit l'implicit wait **propre à
`WebDriverFacade`** (cf. PA-15). Le moteur était donc prouvé **contre un contrat que la production ne
respecte pas**. Conséquence pratique : aucun objectif de couverture unitaire ne doit être lu comme une
garantie de conformité du domaine SYNC — seule la campagne navigateur fait foi.

## PA-8 — `pageLoadTimeout` : la 1ʳᵉ navigation n'est pas couverte 🟠 (assumé)

Vérifié au bytecode Serenity 4.2.22 : il existe une clé de **stratégie** (`serenity.driver.page.load.strategy`,
`normal` = attendre `readyState=complete`) mais **aucune clé de *timeout* de page load**. Le plafond
`qa.sync.pageLoadTimeout` (2 min) est donc **appliqué par le socle** via
`driver.manage().timeouts().pageLoadTimeout(...)`, au **1ᵉʳ contact `WebSync`** avec le driver.

**Contrat acté (2026-08-02, option (a))** : sont protégées les navigations **postérieures** au premier
appel au socle. Une navigation qui le **précède** — typiquement le `openUrl` d'ouverture d'un test —
retombe sur le **défaut W3C du navigateur, 300 s**. L'enjeu réel se chiffre donc à **300 s au lieu de
120 s**, sur cette seule navigation, dans le cas rare d'une page qui pend : jamais un blocage infini.

Options écartées, pour mémoire : **(b)** protéger toute navigation supposerait de prendre la main sur la
**fabrique de driver** Serenity (le driver est créé paresseusement, aucun hook exploitable) ou de forcer
sa création à chaque test (effet de bord inacceptable) ; **(c)** un `WebSync.openUrl()` maison ne
protégerait que si le consommateur y pense — exactement le **mécanisme opt-in que D16 rejette**, en plus
de faire doublon avec `PageObject.openUrl()`.

C'est cette limite qui explique l'échec de **T6** côté `qa-test` : le test navigue sans avoir sollicité le
socle, il ne démontre donc aucun défaut. Le correctif est côté test (provoquer un contact `WebSync` avant
la navigation).

## PA-9 — Diagnostic `within` : distinction container/child portée par la `cause` 🟡

Retenu en séance (« niveau actuel suffit ») : la résolution **deux-temps** fait que la **`cause`** de la
`SyncException` pointe le maillon fautif (container résolu avant child), mais le **message actif**
(`SyncCondition`) ajoute `ABSENT` sans étiquette « container » / « child ». Suffisant pour le débogage via
la cause ; un raffinement (`[ABSENT:container]`) reste possible sans changement d'architecture si le besoin
émerge au pilote.

## PA-10 — `pageLoadTimeout` : marqueur porté par l'instance, pas par le driver ✅ (clos 2026-08-02)

Relevé par la **revue externe ChatGPT** (complète PA-8, qui ne portait que sur le *moment* d'application).
Le plafond est posé dans `AbstractSyncManager.driver()` au 1ᵉʳ contact, sous garde du champ
`pageLoadTimeoutApplied` — **porté par l'instance**, alors que le Javadoc annonce une pose « **une seule
fois sur le driver** ». La portée réelle (par instance) **ne correspond donc pas** à la portée documentée
(par driver) :

- plusieurs instances (`WebSync`, vues `within`) partageant le driver du thread **ré-appliquent** chacune
  le plafond (redondant, sans dommage, mais pas « une seule fois ») ;
- une instance **réutilisée avec un nouveau driver** (driver recréé en cours de session) **ne l'applique
  pas** au nouveau driver (marqueur déjà à `true`).

Distinct du « la 1ᵉʳᵉ navigation n'est pas protégée » (timing, PA-8/T6).

**Corrigé le 2026-08-02** : le champ `boolean pageLoadTimeoutApplied` est remplacé par
`WebDriver pageLoadTimeoutAppliedTo`, qui retient **l'identité du driver** traité. Tout changement de
driver (recréation en cours de session) déclenche donc une **nouvelle pose** du plafond. Reste
thread-confiné, non `static` (D19). Le point d'application, lui, relève de PA-8 et a été tranché
séparément (option (a)).

## PA-11 — Capture DOM : `ThreadLocal` non purgé si non consommé ✅ (clos 2026-08-02)

Relevé par la **revue externe ChatGPT**. `RawDomCaptureListener` mémorise le DOM brut dans un
`ThreadLocal<String>` au `stepFailed` ; `TestFailureManager#consumeCapturedDom()` le lit **et le purge**
dans `executionFinished`. La purge n'intervient **qu'à la consommation**. Si le DOM capté n'est **jamais
consommé** — artefacts désactivés (`qa.failure.artefacts.enabled=false`), test non `FAILED`, step KO puis
**rattrapée**, `TestOutcome` indisponible, écriture court-circuitée avant consommation — la valeur **reste**
attachée au worker. Un worker de pool **réutilisé** pour un test ultérieur pourrait alors exposer un **DOM
périmé**. Le chemin nominal est prouvé (parallèle OK, D22-bis) ; c'est **l'edge « non consommé »** qui n'est
pas couvert. Même famille que **PA-1** (fuite `ThreadLocal`), porteur différent.

**Corrigé le 2026-08-02** : `RawDomCaptureListener` purge le porteur à l'**ouverture** de chaque test —
les **trois** surcharges de `testStarted` (Serenity peut appeler l'une ou l'autre) et `testRetried`.
Choix d'implémentation important : la purge n'a **pas** lieu en fin de test, car `TestFailureManager`
consomme dans `executionFinished` (listener **JUnit Platform**) tandis que `testFinished` est émis par le
bus **Serenity** — l'ordre entre les deux n'étant pas contractuel, purger là risquerait d'effacer le DOM
**avant** son écriture. Couvert par `RawDomCaptureLifecycleTest` (5 cas), via un point d'entrée
package-private `rememberForTests` (le chemin réel exige un driver vivant).

## PA-12 — Actions mutantes : toutes ne passent pas par `act()` 🔄 (partiellement traité)

Relevé par la **revue externe ChatGPT**. `act()` **réserve** le point d'injection de la future sentinelle
JS de re-render (lot 2c). Aujourd'hui, **`click`** et **`typeAndEnter`** y passent, mais **`type`**,
**`type(By, Secret)`**, **`clear`** et plusieurs sélections/actions spécifiques de `WebSync` appellent
`fluentWait` **directement**. Quand la sentinelle sera câblée, elle ne couvrira donc **qu'une partie** des
opérations susceptibles de déclencher un re-render. La notion d'**« action mutante »** n'est définie nulle
part. **Pistes** : définir explicitement ce qu'est une action mutante ; faire passer **toutes** les
mutations par le point unique `act()` ; séparer proprement lectures / prédicats / mutations ; tester qu'une
mutation re-rendant le DOM attend bien le **nouvel état cible**.

**Avancement (2026-08-02)** : la reprise C8 a créé un **point de passage unique** — `actRaw` — par lequel
transitent désormais les **8 actions mutantes** (`click`, `type`×2, `typeAndEnter`, `typeAndTab`,
`clear`, `doubleClick`, `contextClick`) ; depuis le 2026-10-17, `clickViaJS` et `typeViaJS` y passent
aussi (10 actions). La sentinelle s'y branchera donc **une seule fois** et couvrira tout le groupe. **Reste** : `submit` (spécifique web) passe encore par `act()`/facade, n'ayant pas de
mécanisme parasite à contourner — à basculer si l'on veut que la règle « aucune mutation hors `actRaw` »
soit absolue. Le mécanisme de sentinelle lui-même est décrit en **C.7** du plan de remédiation.

## PA-13 — Validation d'entrées absente 🟡 (mineur)

Relevé par la **revue externe ChatGPT**. Aucune garde explicite sur les invariants : `SyncTimeout`,
`locator`, action, prédicat, `parent`/`container` non `null` ; durées strictement positives ; pas de
polling nul ou négatif. `parseDuration()` propage une **`NumberFormatException` brute** et accepte
techniquement des valeurs `null`, nulles au sens temporel ou négatives selon le chemin d'appel. **Piste** :
valider à la frontière et traduire une config invalide en **exception métier lisible**
(`SyncException`/`QaToolkitException`, D18) plutôt que de laisser fuiter `NumberFormatException`. Mineur,
mais peu coûteux et bon pour le diagnostic.

## PA-15 — Dépendance à des comportements internes Serenity non contractuels 🟠 (assumé)

La reprise C8 (2026-08-02) s'appuie sur des constats obtenus **par lecture du bytecode** de
serenity-core 4.2.22, pas sur des garanties d'API :

- `WebElementFacadeImpl` remplace un `waitForTimeout` ≤ 0 par un **défaut de 5 s**
  (`DefaultTimeouts.DEFAULT_WAIT_FOR_TIMEOUT`) → d'où `NO_FACADE_CLOCK = 1L` et **non** `0` ;
- `click`/`type`/`clear`/`typeAndEnter`/`typeAndTab`/`doubleClick`/`contextClick` passent par
  `WithRetries.perform(..., 12)` — **12 tentatives × 250 ms**, nombre **compilé en dur**, aucune clé ;
- `WebDriverFacade` maintient un implicit timeout **distinct** du driver natif, exposé via
  `ConfigurableTimeouts` — c'est lui qui gouverne la résolution d'élément ;
- les exceptions Serenity (`ElementShouldBePresent/Enabled…`) héritent de `TimeoutException`, ce qui les
  rendait invisibles à `classify`.

Ces quatre points sont **exacts pour 4.2.22** et rien ne garantit leur stabilité. **Action attendue à
chaque bump `serenity.version`** : rejouer la campagne `qa-test` (bornes L1–L6 + diagnostic T2/T7/T8/T9)
**avant** publication — c'est le seul filet. Même exigence que PA-3 (bump JUnit) et C2/C11.

## PA-16 — Pas de dump `{step}.html` si l'échec survient hors `@Step` 🟡 (assumé)

`RawDomCaptureListener` est un `StepListener` : il ne capte le DOM que sur **`stepFailed`**, événement
émis uniquement si l'échec se produit **dans une step** (méthode `@Step` d'une classe `@Steps`). Un test
qui appelle `WebSync` **directement depuis la méthode `@Test`** ne produit donc aucun `stepFailed` → le
dossier `KO__` contient `ERROR.log` et `FAIL.log`, mais **pas** de `.html`. Le repli « option C » (source
Serenity) ne s'applique pas davantage : le nom du fichier est celui de la **step fautive**, inexistante ici.

**Conforme au contrat** (D8 : « dump HTML *de la step en erreur* ») et **sans impact consommateur** : les
17 projets écrivent des PageObjects avec `@Step`. Le cas ne concerne en pratique que les **tests de socle**
(`qa-test` phase 1), qui exercent `WebSync` à nu. Élargir la capture (capter aussi dans
`TestFailureManager`, nommer d'après le test) **modifierait le contrat de sortie versionné** — breaking
change SemVer pour un bénéfice limité aux tests internes : écarté.

## PA-17 — `QaPageObject` facilite mais n'impose pas l'usage de `WebSync` 🟡

`QaPageObject` (2026-08-03) fournit `web` à toute page consommatrice sans déclaration. Mais comme elle
étend `PageObject` (Serenity), elle continue d'exposer `$()`, `find()`, `element()` — qui
**court-circuitent intégralement** la synchro du socle : ni profils `SyncTimeout`, ni diagnostic, ni
no-leak, ni capture DOM. Un projet peut donc, par habitude, écrire du code qui contourne le socle sans
s'en apercevoir (cas déjà observé sur les PageObjects PHASE 2 de `qa-test`, depuis migrés).

Ne pas hériter de `PageObject` reviendrait à perdre la navigation (`open()`, `@DefaultUrl`) et tout
l'outillage Serenity : disproportionné. **Piste retenue** : une **règle ArchUnit** livrée par le socle
(« aucun PageObject n'appelle `$`/`find`/`element` »), activable par les consommateurs — cf. F.11. La
classe rend le bon geste gratuit, la règle rend le mauvais geste visible.

## PA-18 — Le package-private protège le compilateur, pas le runtime 🟡 (assumé)

Depuis la réorganisation par fonctionnalité (2026-10-17, D15), les implémentations et le moteur sont
**package-private** : un consommateur qui écrit `import com.example.qa.secret.CyberArkApiClient` obtient une
**erreur de compilation** (« is not public … cannot be accessed from outside package »), de même que
`new SyncException(…)` ou la sous-classe de `WebSync` (désormais `final`). Vérifié le 2026-10-17 sur une
classe compilée dans un package tiers.

La protection est cependant celle du **compilateur**, pas de la JVM. Deux contournements existent, tous deux
**délibérés** (jamais par autocomplétion ou import automatique) :

- déclarer **dans le projet consommateur** une classe du même package (`package com.example.qa.sync;`) :
  les deux se retrouvent dans un *split package* sur le classpath et la visibilité package s'applique ;
- la **réflexion** (`setAccessible(true)`).

Compromis **acté** : l'objectif — empêcher l'usage **involontaire** d'une implémentation et forcer le
passage par la façade — est atteint. **Durcissements possibles, non décidés** : (a) jar scellé
(`Sealed: true` dans le manifeste, la JVM refuse alors de charger une autre classe du même package depuis
un autre emplacement) — à tester contre l'exécution des tests du socle et le *shading* éventuel ; (b) règle
ArchUnit livrée aux consommateurs : « aucune classe du projet ne se trouve dans un package `com.example.qa.*` »,
qui rend le contournement **visible**. À réévaluer si un projet est observé en train de contourner.

## PA-14 — Horloge / sleeper non injectés (testabilité moteur) 🟡 (durcissement)

Relevé par la **revue externe ChatGPT**. `poll` et `tryAssert` utilisent directement `System.nanoTime()`
et `Thread.sleep()` → les **tests unitaires du socle dorment réellement** et ne peuvent affirmer ni le
**nombre exact de tentatives** ni le **respect précis de la deadline**. Injecter deux petites abstractions
**internes** (`Clock`/`Sleeper`, package-private dans `sync`, impls réelles par défaut, doublures en test) rendrait les
tests **instantanés et déterministes** (tentatives, deadline, interruption simulée). **Portée** :
**interne au socle, niveau moteur** (`AbstractSyncManager`) — **zéro impact** API/consommateur, sans rapport
avec `qa-test`. **À faire avec la reprise C8** (on touchera `poll`/`tryAssert` de toute façon, et on voudra
des tests rapides pour **prouver** l'horloge unique) — pas en item isolé.
