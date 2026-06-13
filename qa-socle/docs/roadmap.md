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
| 2 | Politique de versioning & compatibilité (compléter D6) | 1 | 🟡 En cours (D6 acté) |
| 3 | Stratégie transverse d'erreurs | — (avant 6) | ⬜ À faire |
| 4 | Stratégie de configuration & cycle de vie | — (avant 6) | ⬜ À faire |
| 5 | Stratégie de test du socle | — (avant 8) | ⬜ À faire |
| 6 | Signatures d'API par composant (coquilles typées) + gel de l'API | 1→5 | ⬜ À faire |
| 7 | Cas sécurité : masquage des secrets | 6 | ⬜ À faire |
| 8 | Implémentation, composant par composant (+ tests) | 5→7 | ⬜ À faire |
| 9 | Validation pilote sur SNAPSHOT/RC + ajustements | 8 | ⬜ À faire |
| 10 | Garde-fou compat (japicmp/revapi) + RELEASE 1.0.0 + doc + généralisation | 9 | ⬜ À faire |

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
- [x] Types existants classés api/internal (cf. tableau D15) : interfaces + `WebSync`/`MobileSync` +
      `QaLogger` en `api` ; abstraits, impls et `TestFailureManager` en `internal`.
- [x] Décision actée dans `decisions.md` (D15).

---

## Étape 2 — Politique de versioning & compatibilité (compléter D6)

**Objectif** : compléter ce qui n'est pas déjà tranché par **D6** (Parent POM + `RELEASE` + SemVer +
garde-fous + mécanisme d'échappement). Découle de l'étape 1 (on ne versionne que ce qui est public).

**Statut : 🟡 En cours** — *le cœur est acté en [D6](decisions.md), ne reste que ci-dessous*

- [x] SemVer + Parent POM + `RELEASE` + mécanisme d'échappement → **acté en D6**.
- [ ] Règle de dépréciation : `@Deprecated` + maintien sur **N** versions (fixer N).
- [ ] Qui publie une RELEASE et **sur quel critère** (cf. `artifactory-jfrog-settings`).
- [ ] Définir la **non-régression** qui conditionne une publication (quels tests, comment elle bloque
      la release — exigée par D6, à expliciter ici ; s'appuie sur l'étape 5).

> La **date de gel** de l'API n'est PAS ici : l'API n'existe qu'à l'étape 6 → gel rattaché à sa fin.

---

## Étape 3 — Stratégie transverse d'erreurs

**Objectif** : hiérarchie d'exceptions maison du socle, checked vs unchecked. Préalable aux signatures
(toute méthode publique déclare ce qu'elle lève). À figer avant l'étape 6.

**Statut : ⬜ À faire**

- [ ] Définir l'exception racine du socle (ex. `QaToolkitException`) et sa nature (checked/unchecked).
- [ ] Dériver les exceptions par domaine (sync, data, secret, reporting...).
- [ ] Trancher checked vs unchecked et le justifier.

---

## Étape 4 — Stratégie de configuration & cycle de vie

**Objectif** : comment les composants sont configurés (serenity.conf / properties / constructeurs) et
leur portée (par test / par thread / global), thread-safety vu le parallélisme Serenity. Conditionne
la forme des constructeurs et donc les signatures.

**Statut : ⬜ À faire**

- [ ] Source de configuration : `serenity.conf` / properties / constructeurs (hiérarchie de priorité).
- [ ] Portée de chaque composant : par test / par thread / global.
- [ ] Thread-safety vu le parallélisme Serenity (stateless ? ThreadLocal ?).

---

## Étape 5 — Stratégie de test du socle

**Objectif** : décider **comment** on teste des composants à dépendances externes, **avant** d'écrire
l'implémentation — sinon « + tests » (étape 8) n'est pas actionnable. Définit aussi la base de la
non-régression exigée par D6 (étape 2).

**Statut : ⬜ À faire**

- [ ] `WebSync` / `AbstractSyncManager` : unitaire avec **`WebDriver` mocké** vs intégration avec vrai
      navigateur (et quel périmètre pour chaque).
- [ ] `CyberArkApiClient` / `AlmApiClient` : **mock HTTP** (ex. WireMock) pour les appels API, sans
      serveur réel.
- [ ] `ExcelFileReaderWriter` / `CsvFileReaderWriter` : **fixtures** de fichiers (Excel chiffré inclus).
- [ ] `QaLogger` / `TestFailureManager` : vérifier la production des artefacts et le **masquage**.
- [ ] Acter ce qui compose la **CI de non-régression** (réf. étape 2) et le seuil de couverture visé.

---

## Étape 6 — Signatures d'API par composant (coquilles typées) + gel de l'API

**Objectif** : passer des classes vides aux vrais contrats. Méthodes, paramètres, types de retour,
exceptions — **sans corps**. Le vrai moment de conception. S'appuie sur 1→5.

**Statut : ⬜ À faire**

- [ ] **Factories publiques** (`api`) pour `data` / `secret` / `reporting` — seul point d'accès aux
      impls `internal` (cf. D15).
- [ ] `WebSync` (+ `AbstractSyncManager` / `MobileSync`) — signatures de synchro/interaction.
- [ ] `DataFileManager` (+ `ExcelFileReaderWriter` / `CsvFileReaderWriter`) — lecture/écriture data.
- [ ] `QaLogger` — signatures de log (façade SLF4J, accumulation par test, masquage).
- [ ] `TestFailureManager` — capture d'échec (cf. **D16**) :
  - [ ] activation **native** via ServiceLoader (`StepListener` Serenity) — **aucun type public,
        aucune annotation** ;
  - [ ] **déléguer** l'écriture/format/séparation `ERROR_`/`FAIL_` à **Logback** (`logback.xml`) et
        l'exécution/répertoire à **Surefire** — la classe reste un orchestrateur mince ;
  - [ ] définir les **clés de config** (`serenity.conf` / system properties) avec **valeurs par
        défaut** : `enabled` (opt-out), `outputDir`, `dumpHtml`, `env`... (noms à figer ici) ;
  - [ ] graver le **contrat de sortie** (`KO__...` + 3 fichiers, cf. D8) comme contrat versionné.
- [ ] `SecretManager` (+ `CyberArkApiClient`) — récupération de secrets.
- [ ] `ReportingManager` (+ `AlmApiClient`) — remontée résultats ALM.
- [ ] **Fixer la date de gel de l'API** une fois toutes les signatures arrêtées (réf. étape 2).

---

## Étape 7 — Cas sécurité : masquage des secrets

**Objectif** : règle de masquage des secrets dans `QaLogger` + `TestFailureManager` (jamais dans
logs/dumps), définie au niveau des signatures. Isolé car risque OWASP.

**Statut : ⬜ À faire**

- [ ] Définir le contrat de masquage exposé par `SecretManager` (ex. enregistrement des valeurs sensibles).
- [ ] Intégrer le masquage dans les signatures de `QaLogger` (jamais de secret en clair).
- [ ] Intégrer le masquage dans `TestFailureManager` (dumps/logs d'échec).

---

## Étape 8 — Implémentation, composant par composant

**Objectif** : remplacer chaque squelette typé par sa logique, avec de vrais tests comportementaux
(selon la stratégie de l'étape 5) qui remplacent les tests de hiérarchie triviaux actuels.

**Statut : ⬜ À faire** — *première étape AVEC implémentation*

- [ ] `WebSync` (cœur : fluentWait + flag JS) + tests comportementaux.
- [ ] `DataFileManager` (Excel/CSV) + tests.
- [ ] `QaLogger` / `TestFailureManager` + tests.
- [ ] `CyberArkApiClient` + tests.
- [ ] `AlmApiClient` + tests.

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

## Étape 10 — Garde-fou compat + RELEASE 1.0.0 + doc + généralisation

**Objectif** : verrouiller la compatibilité, publier la **première release figée**, documenter pour
les consommateurs, puis généraliser. Le garde-fou de compatibilité n'a de sens qu'**à partir** d'une
baseline publiée — d'où sa place ici (et non avant la 1ʳᵉ release).

**Statut : ⬜ À faire**

- [ ] Brancher **japicmp/revapi** (périmètre = API publique de l'étape 1) et faire **échouer le build**
      sur breaking change non intentionnel ; baseline = la release publiée ci-dessous.
- [ ] Publier la **RELEASE 1.0.0** sur Artifactory.
- [ ] Fournir la **doc consommateur** : guide de démarrage + Javadoc de l'API publique (au-delà de
      `decisions.md`).
- [ ] **Généraliser** aux autres consommateurs (encore en 1.x, retour terrain absorbé en mineur/patch).
