# Rapport d'exécution — suite complète `qa-test`

**Commande :** `mvn clean verify -Dmaven.test.failure.ignore=true`
**Env :** Java 17 (Microsoft 17.0.19), Maven 3.9.12, Chrome (défaut `serenity.conf`)
**Fin de build :** 2026-08-02 05:52:45 · **Durée :** 1 min 48 · **Statut Maven :** `BUILD SUCCESS`

> **Note de lecture (2026-10-17)** — Rapport daté, conservé tel quel : les extraits Surefire cités
> portent les noms de classes **d'avant** la réorganisation par fonctionnalité (`com.example.qa.api.exception.SyncException`,
> aujourd'hui `com.example.qa.sync.SyncException`).

## 1. Synthèse globale

| Indicateur | Valeur |
|---|---:|
| Tests recensés (total Surefire) | **34** |
| Réellement exécutés | **24** |
| ✅ Réussis | **18** |
| ❌ Échecs | **6** |
| ⏭️ Ignorés (`@Disabled`/conditionnels) | **10** |
| Erreurs (exceptions non prévues) | 0 |

## 2. Détail par classe

| Classe | Exécutés | ✅ | ❌ | ⏭️ | Temps |
|---|---:|---:|---:|---:|---:|
| `phase1.BornesLimitesTest` | 14 | 12 | 2 | 0 | 65,1 s |
| `phase1.DiagnosticTest` | 4 | 3 | 1 | 0 | 18,5 s |
| `phase1.FormEtSecretTest` | 3 | 2 | 1 | 0 | 7,8 s |
| `phase1.PageLoadEtConfigTest` | 2 | 0 | 2 | 0 | 8,8 s |
| `AuthenticationTest` (PHASE 2) | 1 | 1 | 0 | 1 | 5,3 s |
| `phase1.SoftAssertTest` | 0 | – | – | 1 | — |
| `phase1.ArtefactsProducerTest` | 0 | – | – | 1 | — |
| `phase1.ArtefactsVerificationTest` | 0 | – | – | 1 | — |
| `RawDomCaptureProducerTest` | 0 | – | – | 2 | — |
| `RawDomCaptureVerificationTest` | 0 | – | – | 4 | — |

## 3. Les 6 échecs — tous des écarts de niveau socle

| Test | Attendu | Obtenu | Écart socle |
|---|---|---|---|
| `BornesLimitesTest.L4_profil_borne_haute` | `SyncException` au timeout | rien levé | profil `SyncTimeout` non appliqué |
| `BornesLimitesTest.L5_short_borne_haute` | `SyncException` | rien levé | idem |
| `DiagnosticTest.T10_out_of_bounds` | `SyncException [OUT_OF_BOUNDS]` | rien levé | diagnostic hors-bornes non produit |
| `FormEtSecretTest.T16_should_dur` | `SyncException` | `AssertionError` | `should*` sans code diagnostic |
| `PageLoadEtConfigTest.T6_page_load_timeout` | échec avant 8 s | pas d'échec | `pageLoadTimeout` non borné |
| `PageLoadEtConfigTest.T24_precedence_D` | `-D` prime → `true` | `qatest` → `false` | override `-D` non appliqué |

Aucune régression côté tests : ce sont les écarts déjà documentés (moteur SYNC partiellement squelette).

### Messages Surefire bruts (extraits du log)

```
BornesLimitesTest.L4_profil_borne_haute:86 L4 : SyncException attendue au timeout
  ==> Expected com.example.qa.api.exception.SyncException to be thrown, but nothing was thrown.
BornesLimitesTest.L5_short_borne_haute:102
  ==> Expected com.example.qa.api.exception.SyncException to be thrown, but nothing was thrown.
DiagnosticTest.T10_out_of_bounds:67
  ==> Expected com.example.qa.api.exception.SyncException to be thrown, but nothing was thrown.
FormEtSecretTest.T16_should_dur:69 T16 : should* dur doit lever immediatement (soft=false)
  ==> Unexpected exception type thrown, expected: <com.example.qa.api.exception.SyncException>
      but was: <java.lang.AssertionError>
PageLoadEtConfigTest.T24_precedence_D:53 T24 : la valeur -D (override-D) doit primer sur
  serenity.conf (qatest), obtenu=qatest ==> expected: <true> but was: <false>
PageLoadEtConfigTest.T6_page_load_timeout:41 T6 : la navigation doit echouer (timeout) avant
  les 8s de blocage ==> expected: <true> but was: <false>
```

## 4. Les 10 ignorés

- **`@Disabled` (9)** — producteurs d'échec volontaire, hors build normal : `SoftAssertTest` (1), `ArtefactsProducerTest` (1), `ArtefactsVerificationTest` (1), `RawDomCaptureProducerTest` (2), `RawDomCaptureVerificationTest` (4).
- **Conditionnel (1)** — 1 méthode de `AuthenticationTest` sautée (assumption non remplie).

## 5. Écart entre ce run et la campagne documentée (17 ✅ / 8 ❌)

Ce lancement est **une invocation unique en config par défaut**. La campagne documentée répartit les tests sur **plusieurs invocations** avec des `-D` **contradictoires** (L4 veut `medium=2s`, L5-LONG veut `medium=15s`…). Ici, faute du bon `-D`, certains tests ne basculent pas — d'où **6 KO** (sous-ensemble des 8 écarts) au lieu de 8, et des tests comme T2/T8/T14 non déclenchés en mode « échec attendu ».

## 6. Deux points d'honnêteté à signaler

1. **Le premier chiffre annoncé oralement (« 28 réussis ») était faux** → le bon est **18 ✅**. Le total 34 englobe les 10 ignorés (24 réellement exécutés, dont 6 KO).
2. **Les dossiers `KO__` actuellement sur le disque n'appartiennent pas à ce build.** Le `clean` a vidé `target/qa-results` puis le build a fini à **05:52** ; or les 11 dossiers `KO__` présents sont horodatés **06:04–06:09** (dont celui ouvert dans l'IDE, `…L5…06-05-15`). Ils proviennent donc d'une **exécution ultérieure** non lancée depuis cette session — ils ne sont pas rattachés à ce rapport. Aucun rapport Serenity agrégé (`target/site/serenity/index.html`) n'a été généré par ce run.

---

_Log intégral conservé hors dépôt : `scratchpad/mvn-verify.log`._
