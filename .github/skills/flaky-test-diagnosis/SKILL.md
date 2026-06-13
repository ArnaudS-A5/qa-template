---
name: flaky-test-diagnosis
description: "Arbre de décision pour diagnostiquer les tests instables (flaky) : timing, données, environnement, locator, ordre d'exécution. USE FOR : un test qui passe par intermittence, isoler une cause de flakiness, définir une stratégie de reproduction. DO NOT USE FOR : un échec systématique (voir failure-classification)."
---

# Skill — Diagnostic des tests flaky

> Un test **flaky** passe **par intermittence** sans changement de code. Objectif : isoler la cause,
> pas masquer le symptôme par des `sleep` ou des retries aveugles.

## Arbre de décision

```
Le test échoue de façon intermittente
├─ Échoue plus souvent sous charge / en parallèle ?
│   ├─ Oui → cause TIMING ou ENVIRONNEMENT
│   └─ Non → continuer
├─ Échoue selon l'ordre des tests (pas isolé) ?
│   ├─ Oui → cause ORDRE / ÉTAT PARTAGÉ
│   └─ Non → continuer
├─ Échoue selon le jeu de données utilisé ?
│   ├─ Oui → cause DONNÉES
│   └─ Non → continuer
└─ Échoue sur une étape précise du DOM mouvant ?
    └─ cause LOCATOR / SYNCHRO
```

## Causes & remèdes

### TIMING / SYNCHRO
- Symptôme : échoue plus en parallèle / machine lente.
- Remède : **passer par `WebSync`** (jamais `Thread.sleep`), attendre l'état stable de l'élément
  cible + flag JS. Cf. `stable-locators-and-waits`.

### DONNÉES
- Symptôme : échoue avec certains jeux, pas d'autres.
- Remède : isoler la donnée fautive, vérifier les pré-requis métier, externaliser via `DataFileManager`.

### ENVIRONNEMENT
- Symptôme : échoue selon l'env (réseau, latence, dispo backend).
- Remède : classer comme **env instable** (`failure-classification` cat. 1/2), ne pas « corriger » le test.

### ORDRE / ÉTAT PARTAGÉ
- Symptôme : passe en isolé, échoue en suite.
- Remède : supprimer l'état partagé (statics, session réutilisée), rendre chaque test **autonome**
  (setup/teardown propres, redémarrage navigateur si besoin).

### LOCATOR
- Symptôme : DOM qui mute, élément parfois absent au bon moment.
- Remède : locator plus stable + attente via `WebSync`. Si l'élément a réellement changé → c'est un
  **locator obsolète** (cf. `auto-fix-failed-test`).

## Stratégies de reproduction

- Réexécuter **N fois** le test isolé (`-Dtest=...`) pour mesurer le taux d'échec.
- Lancer en **parallèle** / **headless** pour révéler le timing.
- Forcer un **ordre différent** pour révéler l'état partagé.

## Règles

- Ne **jamais** ajouter de `Thread.sleep` ni de retry global pour « stabiliser ».
- Documenter la cause identifiée avant tout correctif.
