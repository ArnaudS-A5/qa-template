# Revue technique consolidée — `qa-template` et `qa-test`

## Objet et périmètre

Ce document consolide la revue croisée des branches `develop` des deux dépôts :

- [`ArnaudS-A5/qa-template`](https://github.com/ArnaudS-A5/qa-template/tree/develop), qui contient le parent Maven et le module `qa-socle` ;
- [`ArnaudS-A5/qa-test`](https://github.com/ArnaudS-A5/qa-test/tree/develop), projet consommateur et harnais de validation du socle.

La revue évalue la trajectoire technique d’un projet en cours de construction. Elle ne considère donc pas l’absence d’une fonctionnalité planifiée comme un défaut en soi. Elle retient uniquement :

- les défauts de comportement déjà observables ;
- les choix structurants susceptibles de fragiliser l’ensemble du projet ;
- les pratiques dangereuses si elles sont généralisées ;
- les écarts entre les besoins exprimés et le comportement réel ;
- les faiblesses du dispositif de validation pouvant produire de faux résultats ;
- les points à traiter avant la diffusion du socle aux projets consommateurs.

Les conclusions initiales devenues obsolètes après l’analyse de `qa-test` ont été retirées. En particulier, le socle dispose bien d’une validation sur navigateurs réels, sous Chrome et Firefox, couvrant déjà une partie substantielle du domaine SYNC et des mécanismes transverses.

## Niveaux de priorité

| Priorité | Signification |
|---|---|
| **P0 — Structurant** | À corriger avant d’étendre significativement le composant ou de le diffuser largement. Le défaut affecte le contrat central ou peut imposer une reprise coûteuse. |
| **P1 — Important** | À traiter avant une version stable. Le fonctionnement nominal peut être correct, mais la robustesse, la sécurité ou la compatibilité ne sont pas suffisamment garanties. |
| **P2 — Durcissement** | Amélioration de maintenabilité, de testabilité ou de qualité industrielle. Ne met pas immédiatement l’architecture en danger. |
| **PA — Point d’attention** | Décision acceptable si elle reste explicitement assumée, documentée et contrôlée. |

---

# Partie I — Dépôt `qa-template` / module `qa-socle`

## 1. Synthèse

La trajectoire générale du socle est saine :

- expression du besoin détaillée et traçable ;
- séparation entre API publique et implémentations internes ;
- neutralité vis-à-vis des fournisseurs ;
- API fondée sur des `By`, sans fuite de `WebElement` vivant ;
- activation automatique par SPI ;
- diagnostics structurés ;
- artefacts d’échec exploitables ;
- centralisation Maven cohérente ;
- présence d’un véritable projet consommateur.

Il n’existe pas de dérive généralisée imposant une refonte du projet. Un défaut structurant précis doit néanmoins être traité dans le moteur SYNC : le polling du socle n’est pas réellement l’unique horloge, car chaque tentative délègue à un `WebElementFacade` qui conserve des comportements d’attente internes.

## 2. P0 — Moteur SYNC : la règle « une seule horloge » n’est pas respectée

### Constat

Le moteur appelle schématiquement :

```java
poll(() -> action.apply(resolve(locator)))
```

`resolve(locator)` renvoie un `WebElementFacade`, puis l’action exécute par exemple :

```java
element.click();
```

Or `WebElementFacade.click()` n’est pas une primitive strictement instantanée. Serenity applique encore ses propres vérifications et attentes. Le timeout `0L` fourni lors du wrapping ne neutralise manifestement pas l’ensemble de ces mécanismes.

### Preuves observées dans `qa-test`

- un profil SHORT abaissé à une seconde peut attendre au-delà de cette limite ;
- un profil MEDIUM de quinze secondes abandonne vers quatre ou cinq secondes ;
- un élément absent remonte `ElementShouldBeEnabledException` au lieu de `SyncException [ABSENT]` ;
- un élément désactivé remonte une exception Serenity brute au lieu de `SyncException [DISABLED]` ;
- les résultats sont identiques sous Chrome et Firefox, ce qui écarte une particularité de navigateur.

### Risques

- timeout effectif différent du profil demandé ;
- impossibilité de garantir une deadline stricte ;
- exceptions Serenity s’échappant de la frontière du socle ;
- diagnostics incomplets ou absents ;
- comportement dépendant des évolutions internes de Serenity ;
- multiplication des anomalies à mesure que la surface `WebSync` grandit.

### Correction recommandée

Chaque tentative du polling doit être strictement **one shot** :

```text
résolution immédiate → contrôle immédiat → opération immédiate
```

Le cœur du moteur devrait s’appuyer sur les primitives Selenium sans attente implicite interne :

```java
WebElement element = scope.findElement(locator);
element.click();
```

Le polling du socle doit rester seul responsable :

- de la deadline ;
- du pas de polling ;
- de la réévaluation du locator ;
- de l’absorption des exceptions transitoires ;
- de la classification ;
- du diagnostic final.

`WebElementFacade` peut rester utilisé là où son comportement est réellement instantané ou pour l’intégration Serenity, mais ne doit plus constituer un second moteur d’attente sous `poll()`.

### Faux correctif à éviter

Ajouter uniquement les exceptions Serenity, par exemple `ElementShouldBeEnabledException`, à la classification corrigerait partiellement les messages mais pas l’horloge interne. Ce serait un contournement, pas une résolution du problème.

## 3. P0 — Contrat BF-SYNC-08 appliqué de manière non uniforme

### Constat

Le besoin impose que les prédicats d’état soient immédiats et retournent `false` lorsque l’élément est absent.

Certaines méthodes utilisent correctement `query()` :

```java
isSelected(...)
isEnabled(...)
```

D’autres appellent directement `resolve(locator)` :

```java
isDisabled(...)
isVisible(...)
isCurrentlyVisible(...)
isClickable(...)
isPresent(...)
hasFocus(...)
containsText(...)
containsOnlyText(...)
containsValue(...)
```

### Preuve

`qa-test` confirme que `containsText()` propage `NoSuchElementException` sur un locator absent, alors que `isVisible()`, `isPresent()` et `isEnabled()` retournent `false`.

### Correction

Tous les prédicats de BF-SYNC-08 doivent passer par un point unique :

```java
return query(locator, element -> element.containsText(value));
```

Le même principe doit être appliqué aux prédicats spécifiques de `WebSync`, notamment `hasClass`, `containsElements` et les vérifications de listes.

### Tests attendus

Pour chaque prédicat public :

- élément présent et condition vraie ;
- élément présent et condition fausse ;
- élément absent ;
- container `within` absent ;
- élément remplacé pendant l’évaluation ;
- locator invalide, qui doit rester distingué d’une simple absence.

## 4. P0 — Compatibilité de l’API publique héritée depuis une classe `internal`

### Constat

`WebSync` et `MobileSync` exposent une grande partie de leur API par héritage depuis :

```text
com.example.qa.internal.sync.AbstractSyncManager
```

Les méthodes sont publiques pour Java, mais leur déclaration appartient à une classe située hors du périmètre `api.*`.

### Risque

Un contrôle de compatibilité filtré sur `api.*` peut ne pas détecter la suppression ou la modification d’une méthode héritée. Les projets consommateurs peuvent alors casser malgré un contrôle `japicmp` vert.

Ce risque est déjà identifié dans la documentation sous C7 ; il doit être résolu avant le gel d’une version stable.

### Options

1. Créer une interface publique portant explicitement le contrat commun de synchronisation.
2. Redéclarer explicitement les méthodes publiques dans `WebSync` et `MobileSync`.
3. Prouver par un test automatisé que l’outil de compatibilité analyse bien les membres hérités.
4. Compiler en CI un projet consommateur de référence contre l’ancienne et la nouvelle version.

### Recommandation

Une interface publique commune est la solution la plus lisible si le contrat est réellement partagé entre web et mobile. Elle expose le contrat sans rendre public le moteur interne.

## 5. P1 — Cycle de vie du `pageLoadTimeout`

### Constat

Le timeout de chargement est appliqué dans `AbstractSyncManager.driver()`, au premier appel passant par une instance de synchronisation.

Le test T6 de `qa-test` navigue directement avec :

```java
getDriver().get(...);
```

sans appel préalable au socle. Il ne prouve donc pas que le réglage interne échoue : le socle n’a jamais été sollicité avant la navigation.

### Problème de conception sous-jacent

Dans un PageObject réel, la navigation précède souvent la première interaction `WebSync`. Le premier chargement — potentiellement celui qui bloque — n’est donc pas protégé.

Le marqueur `pageLoadTimeoutApplied` est en outre attaché à l’instance de `AbstractSyncManager`, alors que le commentaire parle d’une application unique au driver :

- plusieurs instances peuvent réappliquer le timeout au même driver ;
- une instance réutilisée avec un nouveau driver peut ne pas l’appliquer au nouveau driver ;
- la portée réelle ne correspond pas à la portée documentée.

### Décision attendue

Deux contrats sont possibles :

- protection des navigations effectuées après le premier appel à `WebSync` ;
- protection automatique de toute navigation, y compris la première.

Si la seconde option est attendue, le timeout doit être appliqué par la configuration Serenity ou par un hook de cycle de vie du driver, pas lors d’une interaction sur un élément.

## 6. P1 — Sécurité du rendu `Secret.masked()`

### Constat

Le rendu expose :

- les deux premiers caractères ;
- la longueur exacte ;
- les 64 premiers bits d’un SHA-256 non salé.

Pour un secret d’un ou deux caractères, la valeur complète est exposée.

### Risque

Pour un mot de passe de faible entropie ou issu d’un dictionnaire, un attaquant peut filtrer les candidats par préfixe et longueur, puis confirmer hors ligne un candidat grâce à l’empreinte SHA-256.

L’affirmation documentaire selon laquelle ce mécanisme permet de comparer sans permettre de reconstituer est donc trop forte.

### Correction recommandée

Avant de figer ce format :

- remplacer SHA-256 par un HMAC utilisant une clé non journalisée ;
- ou produire un identifiant aléatoire stable uniquement pendant le run ;
- ou supprimer entièrement la comparaison par empreinte dans les logs ;
- ne révéler aucun caractère lorsque le secret est court ;
- reconsidérer la nécessité de révéler la longueur exacte.

Le blanchiment des champs `input[type=password]` dans le DOM est, lui, validé et doit être conservé.

## 7. P1 — Cycle de vie du `ThreadLocal` de capture DOM

### État validé

`qa-test` prouve que le passage `stepFailed` → `executionFinished` fonctionne dans le cas nominal, y compris avec deux pages distinctes exécutées en parallèle.

### Risque résiduel

La purge intervient uniquement lors de `consumeCapturedDom()`. Une capture peut rester attachée au worker si :

- les artefacts sont désactivés ;
- le test ne finit pas avec le statut `FAILED` ;
- aucun `TestOutcome` n’est disponible ;
- l’écriture est court-circuitée avant la consommation ;
- une step échoue mais l’erreur est ensuite récupérée.

Un worker réutilisé peut alors transmettre un DOM obsolète à un test ultérieur.

### Correction

- purger explicitement au démarrage et à la fin de chaque test ;
- ou associer la capture à l’identifiant unique du test ;
- refuser une capture dont l’identité ne correspond pas au test en cours ;
- ajouter un test séquentiel reproduisant une capture non consommée suivie d’un autre test KO.

## 8. P1 — Collision et identification des artefacts d’échec

### Constat

Le dossier est nommé à la seconde :

```text
KO__ENV__Test__yyyy-MM-dd_HH-mm-ss
```

Deux exécutions du même test dans la même seconde peuvent écrire dans le même répertoire.

### Risques

- écrasement d’artefacts ;
- mélange entre retries ;
- lecture d’un résultat ancien par le vérificateur ;
- diagnostic erroné en exécution parallèle ;
- dépassement de longueur de chemin avec des noms de tests ou de steps longs.

Le projet `qa-test` a déjà rencontré un faux diagnostic lié à des artefacts périmés.

### Correction

- ajouter des millisecondes ;
- ajouter un identifiant de run ou un suffixe unique court ;
- plafonner la longueur des segments ;
- conserver le nom lisible tout en garantissant l’unicité ;
- transmettre l’identifiant du dossier produit au vérificateur au lieu de rechercher « le plus récent ».

## 9. P1 — Toutes les actions mutantes ne passent pas par `act()`

### Constat

`act()` réserve le futur point d’injection de la sentinelle JavaScript de détection de rerender.

Certaines actions y passent :

- `click` ;
- `typeAndEnter`.

D’autres l’évitent :

- `type` ;
- `type(By, Secret)` ;
- `clear` ;
- plusieurs sélections et actions spécifiques de `WebSync`.

### Risque

Lorsque la sentinelle JS sera implémentée, elle ne couvrira qu’une partie des opérations susceptibles de déclencher un rerender.

### Correction

- définir explicitement la notion d’action mutante ;
- faire passer toutes les mutations par un point unique ;
- séparer clairement les opérations de lecture, les prédicats et les mutations ;
- tester qu’une mutation rerendant le DOM attend bien le nouvel état cible.

## 10. P1 — Les tests unitaires ne sont pas exécutés dans la CI du socle

### Constat

Le workflow SonarCloud exécute :

```text
test-compile sonar:sonar
```

Les tests sont compilés, mais pas exécutés.

### Risques

- Quality Gate vert malgré une suite rouge ;
- régression du noyau `poll()` non détectée ;
- divergence entre analyse statique et comportement ;
- fausse confiance dans le statut du dépôt.

### Correction

Créer des jobs distincts :

1. `mvn test` pour les tests unitaires du socle ;
2. analyse Sonar après réussite des tests ;
3. publication du SNAPSHOT ;
4. déclenchement du harnais `qa-test` sur le commit exact publié.

L’absence de navigateur sur le runner ne justifie pas de sauter les tests unitaires sans navigateur.

## 11. P1 — Les coquilles ne doivent jamais retourner `null` ou réussir silencieusement

### Constat

Tant que les domaines CyberArk, reporting, data ou log restent volontairement incomplets, leur présence n’est pas un défaut. En revanche, certains comportements de coquille sont dangereux :

- `CyberArkApiClient` peut retourner `null` ;
- `AlmApiClient.publishStart()` et `publishEnd()` peuvent ne rien faire ;
- un appelant peut croire l’opération réussie.

### Risque

Le défaut apparaît loin de sa cause, sous forme de `NullPointerException`, de secret absent ou de résultat ALM jamais publié.

### Règle recommandée

Toute coquille exécutable doit échouer immédiatement :

```java
throw new UnsupportedOperationException("Composant non implémenté");
```

ou lever l’exception métier correspondante. Aucun `null` ni no-op silencieux ne doit sortir d’une fonctionnalité annoncée mais non disponible.

## 12. P2 — Validation des paramètres et invariants

Ajouter des validations explicites pour :

- `SyncTimeout` non nul ;
- `parent` et `container` non nuls ;
- `locator` non nul ;
- action et prédicat non nuls ;
- durées strictement positives ;
- pas de polling nul ou négatif ;
- messages de configuration invalides traduits en exception métier lisible.

`parseDuration()` transmet actuellement directement `NumberFormatException` et accepte techniquement des valeurs nulles, nulles au sens temporel ou négatives selon le chemin d’appel.

## 13. P2 — Injecter l’horloge et le mécanisme de sommeil

### Constat

Le moteur utilise directement :

```java
System.nanoTime();
Thread.sleep(...);
```

### Amélioration

Injecter une abstraction `Clock`/`Sleeper` interne permettrait :

- des tests instantanés ;
- un contrôle exact du nombre de tentatives ;
- la vérification des deadlines ;
- la simulation de l’interruption ;
- l’absence de tests unitaires dormant réellement ;
- une meilleure maîtrise des dépassements de timeout.

Cette évolution devient particulièrement pertinente lors de la reprise P0 du moteur.

## 14. P2 — Classification trop large de certaines exceptions

`InvalidElementStateException` est assimilée à `DISABLED`. Or cette exception peut représenter d’autres états invalides qu’un simple élément désactivé.

### Risque

Une erreur d’utilisation réelle peut être absorbée pendant tout le timeout et présentée à tort comme un problème transitoire.

### Action

- classifier sur la base de l’état diagnostiqué, pas uniquement du type d’exception ;
- limiter les exceptions transitoires à celles réellement récupérables ;
- tester les sous-classes Selenium et Serenity rencontrées en conditions réelles.

## 15. P2 — Accès global et répété à la configuration

Le code recrée régulièrement :

```java
SystemEnvironmentVariables.createEnvironmentVariables()
```

### Limites

- couplage fort à un service locator global ;
- tests plus difficiles ;
- lectures répétées pendant les actions ;
- comportement potentiellement variable au milieu d’un test.

### Recommandation

Injecter un fournisseur de configuration interne, tout en conservant les constructeurs publics simples. Une configuration de run peut être figée par instance, sauf besoin explicitement démontré de modification dynamique.

## 16. PA — Étendue des dépendances transitives

Le socle réexporte Selenium, Serenity, JUnit, POI et SLF4J. Ce choix simplifie fortement les projets consommateurs, mais transforme `qa-socle` en plateforme de versions.

### Points à décider avant stabilisation

- conserver un JAR monolithique ;
- ou séparer `qa-sync`, `qa-data`, `qa-failure`, avec un BOM ou un parent commun ;
- définir qui contrôle les conflits de versions ;
- tester les impacts d’une montée de version sur les consommateurs.

Ce n’est pas une mauvaise pratique automatique : c’est une décision de gouvernance à assumer.

## 17. PA — Abstraction commune web/mobile

Le partage du moteur entre Selenium et Appium est cohérent sur le papier, mais la compatibilité effective de `WebElementFacade`, des exceptions et des opérations mobiles n’est pas encore démontrée.

Avant d’ajouter les gestes mobiles :

- valider les hypothèses de driver et de recherche ;
- vérifier les différences entre DOM web et arbre natif ;
- éviter d’introduire des branches conditionnelles nombreuses dans l’abstraction commune ;
- réexaminer l’abstraction si le mobile impose une sémantique d’attente différente.

## 18. P2 — Documentation à réaligner

Plusieurs éléments documentaires sont en retard ou contradictoires :

- classes encore qualifiées de « squelette » alors qu’elles sont implémentées ;
- fonctions annoncées comme restantes alors qu’un début d’implémentation existe ;
- commentaire du workflow laissant entendre que les tests sont exécutés ;
- portée du `pageLoadTimeout` décrite comme liée au driver alors qu’elle est liée à l’instance ;
- niveau de preuve de certains comportements supérieur aux tests disponibles.

### Action

Pour chaque domaine, utiliser trois statuts non ambigus :

1. contrat défini ;
2. implémenté et testé unitairement ;
3. validé de bout en bout par `qa-test`.

## 19. P2 — Points de durcissement du gestionnaire d’échec

### Gestion des exceptions

`TestFailureManager.writeArtefacts()` intercepte principalement `IOException`. Une erreur de configuration, d’extraction Serenity ou de masquage peut encore s’échapper du listener.

Décider explicitement si la capture d’artefacts doit :

- être strictement best effort et ne jamais altérer le résultat du test ;
- ou faire échouer l’exécution lorsque les artefacts sont contractuellement obligatoires.

### Masquage HTML

Le masquage par expressions régulières est documenté comme best effort. Il ne couvre pas nécessairement :

- secret reflété dans un `span` ;
- attribut `data-*` ;
- JSON inline ;
- script ;
- état JavaScript sérialisé ;
- valeur sensible hors `input[type=password]` ;
- balises atypiques contenant `>` dans des valeurs d’attribut.

Le traitement doit rester présenté comme une défense complémentaire, et non comme une garantie générale de « zéro secret ».

## 20. P2 — Gestion future des ressources DATA

Lors de l’implémentation Excel et CSV :

- ouvrir les flux avec try-with-resources ;
- fermer systématiquement `Workbook`, flux chiffrés et packages OPC ;
- ne pas conserver le mot de passe en champ plus longtemps que nécessaire ;
- traduire les exceptions POI en `DataFileException` avec conservation de la cause ;
- tester les fichiers corrompus, chiffrés, vides et verrouillés.

Le code actuel n’appelle pas de critique particulière sur try-with-resources, car les ressources concernées ne sont pas encore ouvertes.

---

# Partie II — Dépôt `qa-test`

## 1. Synthèse

`qa-test` constitue un bon choix architectural :

- véritable consommateur externe du socle ;
- une seule dépendance directe ;
- fixtures déterministes ;
- validation Chrome et Firefox ;
- contrôle des artefacts ;
- tests de parallélisme ;
- journal de campagne précis ;
- remontée effective de défauts du socle.

Sa faiblesse principale n’est pas sa conception générale, mais son industrialisation. Le harnais repose encore sur des commandes manuelles, des tests désactivés, des artefacts persistants et des vérificateurs dépendant d’un run préalable.

## 2. P0 — Absence d’exécution automatique en CI

### Constat

Le projet est présenté comme un harnais de non-régression pérenne, mais aucun workflow n’exécute automatiquement ses campagnes.

### Risques

- une modification de `qa-template` peut casser le comportement sans alerte ;
- les résultats documentés deviennent progressivement historiques ;
- Chrome et Firefox ne sont pas rejoués sur chaque version du socle ;
- les tests désactivés peuvent ne plus compiler ou ne plus fonctionner sans être détectés.

### Correction

Créer une pipeline comprenant :

1. installation ou récupération du commit exact de `qa-parent` et `qa-socle` ;
2. suite déterministe verte ;
3. campagne Chrome ;
4. campagne Firefox ;
5. campagne de tests KO volontaires ;
6. vérification des artefacts ;
7. publication des rapports et dossiers KO comme artefacts CI.

## 3. P0 — Producteurs et vérificateurs d’artefacts non hermétiques

### Constat

Les vérificateurs recherchent des dossiers existants sous `target/qa-results`. Des artefacts d’un run précédent ont déjà provoqué un faux diagnostic.

### Risques

- faux positif ;
- faux négatif ;
- sélection du mauvais dossier ;
- mélange Chrome/Firefox ;
- contamination entre relances ;
- résultat dépendant de l’état antérieur du workspace.

### Correction

- nettoyer automatiquement le répertoire avant chaque campagne ;
- utiliser un `runId` unique ;
- utiliser un répertoire propre par navigateur et par campagne ;
- transmettre explicitement le chemin produit au vérificateur ;
- refuser tout artefact ne portant pas le `runId` courant ;
- ne jamais utiliser simplement `findFirst()` ou le dossier « le plus récent ».

Exemple :

```text
target/qa-results-it/{runId}/{browser}/...
```

## 4. P1 — Orchestration des tests volontairement KO

### Constat

Le mécanisme actuel repose sur :

- `@Disabled` ;
- désactivation manuelle de `DisabledCondition` ;
- `maven.test.failure.ignore=true` ;
- lancement séparé du producteur puis du vérificateur.

Ce montage est acceptable pour un spike, mais fragile pour une non-régression pérenne.

### Correction recommandée

Utiliser un profil Maven dédié et Failsafe :

```text
pre-integration-test  → nettoyage et création du runId
integration-test      → producteurs attendus KO
post-integration-test → vérification des artefacts
verify                → verdict consolidé
```

Le build doit distinguer :

- un échec attendu du producteur ;
- l’absence d’échec alors qu’un KO était attendu ;
- une erreur d’infrastructure ;
- un défaut du vérificateur.

## 5. P1 — Traçabilité insuffisante de la version exacte du socle

### Constat

Le projet consomme `1.0.0-SNAPSHOT`. Le journal donne une date de reconstruction, mais pas systématiquement le SHA Git exact du socle.

### Risque

Un même numéro SNAPSHOT peut désigner plusieurs contenus. Il devient difficile de déterminer si un résultat appartient exactement au code actuellement présent sur `develop`.

### Correction

À chaque campagne, enregistrer automatiquement :

- SHA de `qa-template` ;
- SHA de `qa-test` ;
- version Maven résolue ;
- checksum du JAR `qa-socle` ;
- navigateur et version du driver ;
- JDK et Maven ;
- paramètres `-D` effectifs.

Ces informations doivent être placées dans le rapport de campagne et dans les artefacts CI.

## 6. P1 — T6 ne valide pas correctement le `pageLoadTimeout`

### Constat

Le test appelle directement `Serenity.getDriver()` puis navigue. Il ne déclenche pas le chemin `AbstractSyncManager.driver()` avant la navigation.

### Correction du test

Si le contrat retenu reste « application au premier contact par WebSync », provoquer ce contact avant la navigation :

```java
WebSync web = new WebSync();
web.isPresent(By.tagName("html"));
getDriver().get(slowPage);
```

Si le besoin réel est de protéger la première navigation, le test est pertinent mais révèle alors un défaut de point d’activation dans le socle. Le besoin doit être clarifié avant de classer T6.

## 7. P1 — T10 repose sur une attente discutable

### Constat

Un élément placé à `top:5000px` peut être automatiquement scrollé dans le viewport par Selenium avant le clic. Le succès du clic n’est donc pas une anomalie.

### Correction

- clarifier si le scroll automatique est autorisé ;
- si oui, le test doit attendre un succès ;
- si `OUT_OF_BOUNDS` doit être testé, construire un scénario produisant réellement `MoveTargetOutOfBoundsException`.

T10 ne doit plus être comptabilisé comme preuve d’un défaut du socle dans sa forme actuelle.

## 8. P1 — T16 impose le mauvais type d’exception

### Constat

BF-SYNC-09 impose qu’une assertion dure échoue immédiatement et retourne `void`. Les décisions d’implémentation prévoient une `AssertionError`, notamment pour l’intégration au soft-assert.

T16 attend une `SyncException`.

### Correction

Le test doit vérifier :

- échec à la première assertion en mode hard ;
- type `AssertionError` ;
- message identifiant la condition et le locator ;
- absence d’exécution de l’instruction suivante.

L’ajout d’un code `[ABSENT]` peut être demandé comme amélioration de diagnostic, mais ne doit pas être confondu avec le contrat fonctionnel actuel.

## 9. P1 — T5 ne force pas un stale à l’intérieur d’une opération

### Constat

T5 effectue plusieurs appels indépendants à `getText()`. Chaque appel réévalue le `By`, ce qui valide le no-leak entre appels.

Il ne garantit pas qu’un élément devienne stale entre sa résolution et l’exécution de l’action dans un même tour de polling.

### Complément attendu

Créer une fixture capable de :

- remplacer le nœud au moment exact d’un clic ou d’une lecture ;
- provoquer au moins une vraie `StaleElementReferenceException` ;
- confirmer que le même appel `WebSync` finit par réussir ;
- confirmer que la dernière exception stale devient la cause en cas de timeout.

## 10. P1 — T11 ne prouve pas la localisation du maillon fautif

### Constat

Le test vérifie `[ABSENT]` pour un container absent et pour un child absent, mais ne vérifie pas que le message distingue les deux niveaux.

### Action

Soit :

- réduire l’affirmation du test à « `within` fonctionne et signale une absence » ;

soit :

- enrichir le diagnostic du socle ;
- puis vérifier explicitement `container` contre `child`.

## 11. P1 — L6 ne mesure pas réellement le pas de polling

### Constat

La cible apparaît vers 2,5 secondes et les deux mécanismes rendent la main vers cette durée. L’assertion vérifie surtout que `WebSync` n’est pas beaucoup plus lent que Selenium brut.

### Ce que L6 prouve

- absence d’empilement massif avec l’implicit wait ;
- restauration nominale du timeout implicite.

### Ce qu’il ne prouve pas

- polling exact à 250 ms ;
- nombre de tentatives ;
- respect précis de la deadline.

### Action

Renommer le test ou compléter la preuve avec :

- instrumentation de la fixture comptant les tentatives ;
- horloge injectée côté socle pour la précision unitaire ;
- seuils temporels plus robustes côté navigateur.

## 12. P1 — T23 agrège plusieurs preuves distinctes

La présence des trois services est bien démontrée par l’ensemble de la campagne :

- artefacts : `TestFailureManager` ;
- DOM brut : `RawDomCaptureListener` ;
- agrégation soft : `SoftAssertionExtension`.

Un seul test d’artefacts ne prouve toutefois pas à lui seul les trois activations.

### Action

Documenter T23 comme une exigence composite et rattacher chaque service à son test de preuve.

## 13. P1 — Secret écrit en clair par le test

### Constat

`FormEtSecretTest` écrit la valeur récupérée dans la console :

```java
System.out.println("[T22] password value (via DOM property) = " + value);
```

Une assertion `assertEquals(expectedSecret, actualValue)` peut également afficher le secret en cas d’échec.

### Risque

Le projet valide le masquage du socle tout en normalisant une pratique qui contourne ce masquage dans le code consommateur.

### Correction

- supprimer toute sortie du clair ;
- ne pas inclure les valeurs dans les messages d’assertion ;
- comparer sans restituer les opérandes ;
- utiliser une valeur fictive dédiée aux tests, sans pour autant la journaliser.

## 14. P1 — Tests temporels potentiellement instables

### Constat

Certaines bornes utilisent une marge de seulement 200 ms autour de délais de plusieurs secondes.

### Sources de variabilité

- charge du runner ;
- ordonnancement JavaScript ;
- démarrage ou ralentissement du navigateur ;
- polling interne du driver ;
- antivirus ou I/O ;
- concurrence CI.

### Correction

- élargir les marges des tests navigateur ;
- répéter les mesures critiques ;
- distinguer test fonctionnel et benchmark ;
- réserver la précision fine à des tests avec horloge simulée ;
- ne pas imposer 14 secondes réelles lorsqu’un profil réduit peut tester la même règle.

## 15. P2 — Interruption absorbée dans T5

Le test contient :

```java
catch (InterruptedException ignored) {
}
```

### Correction

```java
catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    fail("Test interrompu", e);
}
```

Ignorer l’interruption est une mauvaise pratique, même dans du code de test.

## 16. P2 — Sorties console trop nombreuses

Les `System.out.println()` facilitent un spike, mais deviennent bruyants et difficiles à exploiter en CI.

### Action

- conserver uniquement les mesures utiles ;
- utiliser un logger avec un niveau dédié ;
- produire un rapport structuré ;
- ne jamais journaliser de donnée sensible ;
- intégrer les mesures dans les messages d’assertion uniquement lorsqu’elles sont nécessaires au diagnostic.

## 17. P2 — Documentation partiellement obsolète

Le README indique encore que `WebSync` est un squelette et que le seul comportement runtime du socle est `TestFailureManager`, alors que la phase 1 couvre déjà SYNC, le soft-assert et les trois services.

### Action

- aligner le README sur le suivi de phase 1 ;
- distinguer clairement tests automatiques, tests manuels et tests planifiés ;
- indiquer le SHA du socle validé ;
- ne pas présenter un comportement comme prouvé lorsque l’assertion ne couvre qu’une partie du besoin.

## 18. P2 — Couverture restant à compléter

Sans considérer cette incomplétude comme un défaut de trajectoire, le harnais devra encore couvrir :

- phase 2 sur widgets réels ;
- davantage de méthodes de la surface `WebSync` ;
- actions mutantes déclenchant un rerender ;
- collections ;
- listes et désélections ;
- géométrie et captures ;
- focus et actions avancées ;
- parallélisme au-delà de la seule capture DOM ;
- cycle de vie anormal du `ThreadLocal` ;
- collision de dossiers ;
- première navigation et changement de driver ;
- comportement avec iframe et shadow DOM ;
- configuration invalide.

## 19. P2 — Organisation du code de test

À mesure que le harnais grandit :

- factoriser le chronométrage ;
- centraliser l’ouverture des fixtures ;
- créer des assertions métier pour les diagnostics ;
- éviter les constantes de configuration dispersées ;
- séparer tests nominaux, tests de limites, tests KO et vérificateurs ;
- adopter des suffixes cohérents `Test` / `IT` selon Surefire et Failsafe.

---

# Plan d’action transverse recommandé

## Lot 1 — Sécurisation immédiate du cœur

1. Reprendre le moteur SYNC pour garantir des tentatives one shot.
2. Rendre le polling du socle réellement souverain sur la deadline.
3. Uniformiser tous les prédicats par `query()`.
4. Corriger ABSENT et DISABLED au navigateur.
5. Rejouer L3, L4, L5, T2, T8 et T14 sous Chrome et Firefox.

## Lot 2 — Fiabilisation du harnais

1. Corriger T6, T10 et T16.
2. Renforcer T5, T11 et L6.
3. Supprimer toute journalisation de secret.
4. Introduire un `runId`.
5. Rendre producteurs et vérificateurs hermétiques.
6. Automatiser les campagnes par Maven/Failsafe.

## Lot 3 — Intégration continue

1. Exécuter les tests unitaires de `qa-template` en CI.
2. Publier un SNAPSHOT identifié par commit.
3. Déclencher `qa-test` sur ce commit exact.
4. Exécuter Chrome et Firefox.
5. Archiver rapports, timings et artefacts KO.

## Lot 4 — Stabilisation avant version publique

1. Résoudre la compatibilité de l’API héritée depuis `internal`.
2. Décider le point d’application du `pageLoadTimeout`.
3. Revoir le format de `Secret.masked()`.
4. Garantir le cycle de vie du DOM capturé.
5. Garantir l’unicité des artefacts.
6. Rendre toutes les coquilles fail-fast.
7. Aligner les documents sur le niveau de preuve réel.

## Lot 5 — Durcissement

1. Injecter horloge, sleeper et fournisseur de configuration.
2. Valider les paramètres.
3. Affiner la classification des exceptions.
4. Décider la stratégie de modularisation et de transitivité Maven.
5. Étendre les campagnes aux cas avancés et au parallélisme généralisé.

---

# Conclusion

Les deux dépôts suivent une trajectoire globalement saine. `qa-test` joue effectivement son rôle de banc d’essai et a permis d’identifier un défaut central avant diffusion large : l’empilement résiduel du polling maison et des comportements d’attente de Serenity.

Ce défaut doit être corrigé prioritairement, mais il reste localisé au moteur d’exécution SYNC. Il ne remet pas en cause :

- la séparation des responsabilités ;
- l’API fondée sur les locators ;
- le modèle no-leak ;
- les mécanismes SPI ;
- les artefacts d’échec ;
- le principe du projet consommateur ;
- la gouvernance Maven.

Le second enjeu majeur est désormais industriel : transformer `qa-test` d’un excellent harnais de validation manuelle en une non-régression hermétique, versionnée et exécutée automatiquement.
