---
name: failure-classification
description: "Taxonomie de classification des échecs de tests automatisés pour décider SIGNALER vs CORRIGER. USE FOR : qualifier la cause racine d'un KO avant toute action, éviter les corrections aveugles. DO NOT USE FOR : appliquer le fix (voir auto-fix-failed-test) ou lire les rapports (voir serenity-reporting-triage)."
---

# Skill — Classification des échecs

> Règle d'or : **l'agent ne corrige QUE le « locator obsolète »**. Toutes les autres catégories sont
> **signalées**, jamais corrigées automatiquement.

## Taxonomie

### 1. Application indisponible
- **Symptômes** : timeout généralisé, flag JS impossible à maintenir (disparaît en continu), erreur
  serveur (5xx), page blanche.
- **Action** : **SIGNALER** « environnement instable ». Ne rien corriger. Le run n'est pas fiable.

### 2. Page non chargée
- **Symptômes** : élément cible **absent** MAIS flag JS **disparu** au timeout (un chargement/re-render
  était encore en cours — rappel : la disparition du flag signale un chargement, cf.
  `stable-locators-and-waits`).
- **Action** : **SIGNALER**. Suggérer d'augmenter le timeout / vérifier la latence. Pas de correction
  de code.

### 3. Locator obsolète ✅ (seule catégorie corrigeable)
- **Symptômes** : élément cible absent **MAIS** un **équivalent métier** est présent dans le DOM sous
  un autre locator (id/attribut changé, refonte de markup).
- **Action** : **CORRIGER** (cf. `auto-fix-failed-test`) après vérification de cohérence métier stricte.

### 4. Erreur métier / règle fonctionnelle
- **Symptômes** : ex. valeur absente d'un menu déroulant **parce qu'elle dépend** d'une saisie
  précédente non faite ; enchaînement métier invalide.
- **Action** : **SIGNALER** « donnée/enchaînement incohérent ». Ne pas forcer un locator.

### 5. Écart sémantique données ↔ UI
- **Symptômes** : libellé du jeu de données ≠ libellé applicatif (« professionnel de santé » vs
  « professionnel santé »).
- **Action** : **SIGNALER** une divergence de référentiel. **Ne jamais inventer** la valeur correcte.

### 6. Anomalie applicative réelle
- **Symptômes** : une valeur/un élément **légitimement attendu** est réellement absent (pas
  d'équivalent métier).
- **Action** : **SIGNALER** un **bug applicatif** potentiel. Ne pas masquer par une correction de test.

## Arbre de décision (résumé)

```
Élément cible trouvé ? 
 ├─ Non → flag JS disparu (chargement en cours) ?
 │        ├─ Oui → (2) Page non chargée → SIGNALER
 │        └─ Non (flag stable = page prête) → équivalent métier présent dans le DOM ?
 │                 ├─ Oui → (3) Locator obsolète → CORRIGER
 │                 └─ Non → valeur légitimement attendue ?
 │                          ├─ Oui → (6) Anomalie applicative → SIGNALER
 │                          └─ dépend d'une saisie/donnée ? → (4)/(5) → SIGNALER
 └─ Oui mais inerte / timeout massif → (1) App indisponible → SIGNALER
```

## Sortie attendue

Catégorie (1–6) + justification factuelle (extraits de log + constat DOM). Si catégorie 3 → passer la
main à `auto-fix-failed-test`. Sinon → entrée « signalé » dans le suivi (`maintenance-run-tracking`).
