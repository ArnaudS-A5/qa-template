---
name: auto-fix-failed-test
description: "Algorithme obligatoire en 7 étapes pour corriger UNIQUEMENT un locator obsolète, avec vérification de cohérence métier stricte. USE FOR : appliquer une correction de locator validée comme obsolète. DO NOT USE FOR : classer la cause (voir failure-classification) — n'appliquer ce skill QUE si la cause est 'locator obsolète'."
---

# Skill — Auto-correction d'un locator obsolète (algorithme obligatoire)

> **Pré-requis** : la cause a été classée **« locator obsolète »** par `failure-classification`.
> **Interdiction absolue** de correction aveugle. Si un seul doute métier subsiste → **SIGNALER**, ne pas corriger.

## Algorithme en 7 étapes

> Les sources ci-dessous sont normalement **toutes présentes** (contrat de sortie, cf. D16). Si l'une
> manque, applique le principe de **souplesse en lecture** : analyse avec ce qui est disponible. Seule
> exception **non négociable pour CORRIGER** : le **DOM réel** (étape 2). Sans preuve DOM, on ne peut
> pas valider la correspondance métier d'un locator → **SIGNALER**, ne pas corriger (mais l'analyse de
> cause, elle, peut quand même conclure).

### 1. Lire le log d'erreur
Extraire la **step en échec** et le **locator attendu** (`ERROR_*.log`). Si `ERROR_*.log` manque,
récupère la même information dans `FAIL_*.log` ou la sortie du re-run.

### 2. Lire le dump HTML — **indispensable pour corriger**
Charger `{nomDeLaStepEnErreur}.html` → **DOM réel** au moment de l'échec. **Absent ⇒ pas de
correction** (aucune preuve de correspondance) → SIGNALER. À défaut, un DOM **rejoué** par
réexécution locale du test peut servir de substitut explicite (à mentionner dans la justification).

### 3. Lire les steps (intention métier)
Via `FAIL_*.log` : reconstituer l'**intention métier** complète (parcours, garantie, données saisies).
On ne corrige pas une step isolée hors contexte. Si `FAIL_*.log` manque, reconstitue l'intention via
les steps du **cas de test rejoué** ; si l'intention reste **trop incertaine** → SIGNALER.

### 4. Trouver le locator candidat
Chercher dans le DOM réel l'élément qui correspond à l'**action métier ET à la donnée** visées par la
step.

### 5. Vérifier la cohérence métier (étape critique)
**Ne pas confondre** des éléments adjacents.

> Exemple : un id de checkbox a changé. Il existe « Assurance chien tous risques » et « Assurance chien
> simple ». Le candidat doit correspondre **exactement** à l'intention (tous risques ≠ simple). En cas
> d'ambiguïté → **SIGNALER**, ne pas choisir au hasard.

### 6. Proposer un locator stable
Respecter la hiérarchie (`stable-locators-and-waits`) : `data-*` > `id` > CSS sémantique >
XPath par label métier. Préférer le locator le **plus stable disponible** dans le DOM réel.

### 7. Appliquer la correction au bon endroit + justifier
- Modifier **la déclaration du locator dans le PageObject** (jamais en dur dans une Step/Test).
- Fournir une **justification** : ancien locator, raison de l'obsolescence, nouveau locator, preuve de
  correspondance métier.

## Validation obligatoire

- **Réexécuter uniquement le test corrigé** (`-Dtest=...`) pour confirmer le passage au vert.
- Si toujours KO → ne pas empiler des corrections ; reclasser via `failure-classification`.

## Garde-fous

- Une seule cause = « locator obsolète ». Pour toute autre cause → STOP + signalement.
- Pas de modification de logique métier, d'assertion, ni de données.
- Tracer la correction dans le suivi (`maintenance-run-tracking`).
