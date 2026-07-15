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
| PA-7 | ⬜ Ouvert | Surface SYNC (78 méthodes) non testable unitairement → validation `qa-test` |
| PA-8 | ⬜ Ouvert | `pageLoadTimeout` : pas de clé Serenity native → appliqué par le socle (2c), moment à valider |
| PA-9 | ⬜ Ouvert | Diagnostic `within` : distinction container/child portée par la `cause` (message actif = `ABSENT` global) |

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
en fonctionnement nominal. Optimisation interne libre (types `internal`) : à mesurer au pilote avant
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

## PA-7 — Surface SYNC (78 méthodes) non testable unitairement 🟠

Seuls le cœur pur (`poll`, `parseDuration`, `resolveTimeout`, `tryAssert`, `SyncCondition.classify`) et la
résolution des profils sont testés **sans navigateur**. Tout le reste (résolution `WebElementFacade`,
sondes, délégation Serenity, `within`, prédicats, `should…` de bout en bout) exige un **vrai driver** → la
validation comportementale relève du **projet consommateur `qa-test`** (site bidon, navigateur réel,
ralenti pour observer les timings), conformément à D20. À faire au lot 2c.

## PA-8 — `pageLoadTimeout` : pas de clé Serenity native 🟡

Vérifié au bytecode Serenity 4.2.22 : il existe une clé de **stratégie** (`serenity.driver.page.load.strategy`,
`normal` = attendre `readyState=complete`) mais **aucune clé de *timeout* de page load**. Le plafond
`qa.sync.pageLoadTimeout` (2 min) devra donc être **appliqué par le socle** via
`driver.manage().timeouts().pageLoadTimeout(...)` (lot 2c) — le **moment** d'application (premier accès
driver du thread ?) est à trancher/valider, le socle ne gérant pas le cycle de vie du driver (Serenity).

## PA-9 — Diagnostic `within` : distinction container/child portée par la `cause` 🟡

Retenu en séance (« niveau actuel suffit ») : la résolution **deux-temps** fait que la **`cause`** de la
`SyncException` pointe le maillon fautif (container résolu avant child), mais le **message actif**
(`SyncCondition`) ajoute `ABSENT` sans étiquette « container » / « child ». Suffisant pour le débogage via
la cause ; un raffinement (`[ABSENT:container]`) reste possible sans changement d'architecture si le besoin
émerge au pilote.
