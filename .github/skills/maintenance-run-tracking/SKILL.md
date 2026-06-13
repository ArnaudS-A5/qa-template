---
name: maintenance-run-tracking
description: "Tenue d'un fichier de suivi persistant d'une campagne de maintenance (boucle sur les dossiers KO__), reprenable après interruption. USE FOR : suivre l'avancement d'un run massif, reprendre où on s'est arrêté, produire un bilan. DO NOT USE FOR : analyser un échec individuel (voir serenity-reporting-triage)."
---

# Skill — Suivi de campagne de maintenance

## But

Maintenir un **fichier de suivi persistant** pendant le traitement des dossiers
`target/qa-results/KO__*`, afin de :

- ne **rien oublier**,
- pouvoir **reprendre** après une interruption,
- produire un **bilan** exploitable.

## Format du fichier de suivi

Markdown (ou CSV) **hors de `target/`** (un `mvn clean` ne doit jamais effacer le suivi d'une
campagne en cours) : `qa-results-tracking/_maintenance-tracking.md` à la racine du projet,
dossier exclu du versionnage via `.gitignore`.

| Test | Dossier KO | Données | Cause (classif.) | Correction appliquée | Statut final | Date traitement |
|---|---|---|---|---|---|---|
| SouscriptionChienTest | KO__RECETTE__SouscriptionChienTest__2024-... | complètes | Locator obsolète | data-testid garantie-chien | ✅ Corrigé & vert | 2024-06-12 |
| DevisHabitationTest | KO__INTEG__DevisHabitationTest__2024-... | partielles (HTML manquant) | App indisponible | — | ⚠️ Signalé | 2024-06-12 |

### Colonnes

- **Test** : nom du test.
- **Dossier KO** : dossier source sous `target/qa-results/`.
- **Données** : complétude des artefacts analysés — `complètes` (3 fichiers) ou `partielles (… manquant)`.
  Permet de pondérer la confiance dans la conclusion (cf. souplesse en lecture).
- **Cause** : catégorie de `failure-classification` (1–6).
- **Correction appliquée** : description courte ou « — » si signalé.
- **Statut final** : `✅ Corrigé & vert`, `⚠️ Signalé`, `❌ Échec persistant`, `⏳ En cours`.
- **Date traitement**.

## Cycle de vie

1. **Initialisation** : lister tous les dossiers `KO__*`, créer une ligne par test (statut `⏳`).
2. **Itération** : pour chaque ligne, classifier → corriger (si locator obsolète) → revalider →
   **mettre à jour la ligne immédiatement**.
3. **Reprise** : au redémarrage, relire le fichier ; traiter uniquement les lignes non terminées
   (`⏳` ou vides).
4. **Bilan** : à la fin, résumer (corrigés / signalés / échecs persistants).

## Règles

- Mettre à jour le fichier **après chaque test**, pas en lot (résilience aux interruptions).
- Ne jamais marquer `✅ Corrigé & vert` sans **re-run vert** du test.
- Conserver une trace des **signalements** (causes non corrigeables) pour le bilan d'équipe.
