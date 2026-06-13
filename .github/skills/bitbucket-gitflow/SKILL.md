---
name: bitbucket-gitflow
description: "Modèle de branches Git flow sur Bitbucket, conventions de commit/PR, et capacité branche+commit+push (phase 1 : sans création automatique de PR). USE FOR : créer une branche conforme, committer, pousser, cadrer le flow. DO NOT USE FOR : versioning Maven/Artifactory."
---

# Skill — Git flow Bitbucket

> Document de gouvernance de référence : `qa-socle/docs/git-flow.md` (contexte équipe, protections
> Bitbucket, points à arbitrer). Ce skill en est le résumé opérationnel pour l'agent.

## Modèle de branches

| Branche | Rôle | Source | Cible merge |
|---|---|---|---|
| `master` | référence stable, taguée aux releases | — | — |
| `develop` | intégration continue | `master` | `master` (release : PR + tag) |
| `fix/*` | correctif (locator, bug) — cas le plus fréquent | `develop` | `develop` (via PR) |
| `feature/*` | nouveau test / évolution / refacto | `develop` | `develop` (via PR) |

- Une **branche de travail par action en cours** : son nom décrit l'action réalisée.
- `release/*` et `hotfix/*` ne sont **pas utilisés au démarrage** : en réserve, à introduire seulement
  si un besoin réel apparaît (cf. git-flow.md §9).

## Conventions de nommage de branches

```
fix/QA-1290-locator-garantie
feature/QA-1234-souscription-chien
```

- Préfixe = type d'action (`fix` le plus souvent, sinon `feature`, `refactor`, `docs`, ...).
- Inclure la **clé de ticket** (ex. `QA-1234`) quand elle existe ; sinon slug descriptif court.
- Slug court, en minuscules, mots séparés par `-`.

## Conventions de commit

Format **Conventional Commits** recommandé :

```
fix(souscription): corrige le locator obsolète de la garantie chien

- ancien id #garantie123 absent du DOM
- nouveau locator data-testid='garantie-chien-tous-risques'

QA-1234
```

Types : `feat`, `fix`, `test`, `refactor`, `chore`, `docs`, `ci`.

## Capacité de l'agent — Phase 1

L'agent **prépare et pousse**, sans ouvrir de PR :

```bash
git checkout develop && git pull
git checkout -b fix/QA-1290-locator-garantie
git add <fichiers ciblés>
git commit -m "fix(souscription): corrige le locator obsolète ..."
git push -u origin fix/QA-1290-locator-garantie
```

Puis **s'arrêter** et résumer : branche créée, fichiers modifiés, message de commit, état du push.
**Ne pas** créer de PR/MR automatiquement.

## Convention de PR (pour plus tard / manuel)

- Titre = résumé impératif + clé de ticket.
- Description : contexte, changements, preuve de validation (re-run vert), impact.
- Cible : `develop`. (Le passage `develop` → `master` est un geste de release, humain.)

## Règles strictes

- Une branche = un sujet cohérent. Pas de mélange refacto + correctif fonctionnel.
- Ne jamais committer sur `master`/`develop` directement.
- Committer **uniquement les fichiers ciblés** ; ne pas balayer des fichiers en cours d'autrui.
- Ne pas utiliser `--force` / `reset --hard` sur des branches partagées sans confirmation.
