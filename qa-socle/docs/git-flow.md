# Gouvernance Git flow — équipe QA

Document de référence pour la stratégie de branches, commits et PR.
Sert de base au support de formation (PPT).

## 0. Contexte de l'équipe (état des lieux)

| Élément | Situation actuelle |
| --- | --- |
| Taille | 7 à 10 contributeurs (auteurs de tests + mainteneurs du socle) |
| Niveau Git | Débutant (clone / commit / push ; peu de branches) |
| Périmètre | Le socle (`qa-template`) **et** les ~17 projets de tests consommateurs |
| Revue | Cible : PR obligatoire + 1 reviewer minimum |
| Release | Pas encore de process formalisé (Jenkins géré par une autre équipe, publication Artifactory) |
| Branches actuelles | « le bordel » : `master` + `upgrade-v4` (nouveau master de fait, suite à l'upgrade Serenity 4) |
| Conventions commit | Quasi inexistantes : trigramme du dev, parfois message vide après le trigramme |

**Conclusion** : viser un flow **simple et robuste** — `master` + `develop` + branches de travail —
sans `release/*`/`hotfix/*` au démarrage (trop coûteux pour un niveau débutant). Objectif n°1 =
arrêter le bazar des branches et donner une règle claire que tout le monde peut suivre.

## 1. Principe directeur

> **`master` stable + `develop` d'intégration + branches de travail courtes + PR obligatoire.**

- **`master`** : référence stable, taguée aux releases. Personne ne pousse dessus directement.
- **`develop`** : branche d'intégration continue, créée depuis `master`. C'est la cible de toutes les PR
  de travail.
- **Branches de travail** : chaque membre crée une branche par action en cours, nommée d'après
  l'action réalisée (`fix/*` la plupart du temps, sinon `feature/*`, etc.), puis ouvre une **PR vers
  `develop`**.
- Pas de `release/*` ni `hotfix/*` au démarrage : on les introduira **seulement** si un vrai besoin
  apparaît (cf. §9 évolution).

Trois règles mentales (ma branche → PR vers `develop` → release = `develop` vers `master`), adaptées à
un niveau débutant tout en isolant `master` du travail quotidien.

## 2. Étape préalable — sortir du « bordel » actuel

Avant d'imposer le flow, on assainit la situation `master` / `upgrade-v4` :

1. **Décider quelle branche est la vérité.** `upgrade-v4` (Serenity 4) est le nouveau master de fait → elle
   devient la **nouvelle `master`**.
2. **Basculer `master`** sur le contenu de `upgrade-v4` (via PR de `upgrade-v4` → `master`, ou redéfinition
   de la branche par défaut puis renommage — à arbitrer ensemble, opération sensible à confirmer).
3. **Geler puis supprimer** les branches obsolètes une fois fusionnées (ne pas laisser traîner d'anciens
   « master parallèles »).
4. **Créer `develop`** depuis la nouvelle `master` assainie.
5. **Protéger `master` et `develop`** (cf. §5) pour que le bazar ne se reforme pas.
6. Faire ce nettoyage **par dépôt** (socle d'abord, puis les 17 projets), pas tout d'un coup.

> ⚠️ Renommer/rebaser une branche partagée impacte tout le monde : à planifier (communication + fenêtre),
> jamais en `--force` sauvage sur une branche que d'autres utilisent.

## 3. Modèle de branches (démarrage)

| Branche | Rôle | Source | Cible merge | Durée de vie |
| --- | --- | --- | --- | --- |
| `master` | Référence stable, taguée aux releases | — | — | permanente |
| `develop` | Intégration continue de l'équipe | `master` | `master` (release, via PR) | permanente |
| `fix/*` | Correctif (locator, bug) — cas le plus fréquent | `develop` | `develop` (via PR) | courte (heures/jours) |
| `feature/*` | Nouveau test / évolution / refacto | `develop` | `develop` (via PR) | courte (jours) |

Pas d'autres types au démarrage. **Branches courtes** : on évite les branches qui vivent des semaines
(source n°1 de conflits pour des débutants). Le nom de la branche de travail **décrit l'action en
cours** ; chaque membre travaille sur sa propre branche.

## 4. Conventions de nommage et de commit

### Nom de branche

```
feature/<TICKET>-<slug-court>
fix/<TICKET>-<slug-court>
```

Exemples :

```
feature/QA-1234-souscription-chien
fix/QA-1290-locator-garantie
```

- Préfixe = type (`feature` / `fix`).
- Clé de ticket si elle existe (sinon, slug descriptif court).
- minuscules, mots séparés par `-`.

### Message de commit

On remplace « trigramme + parfois vide » par un format **simple mais imposé** :

```
<type>(<zone>): <résumé impératif court>

<TICKET ou trigramme si pas de ticket>
```

Exemple :

```
fix(souscription): corrige le locator obsolète de la garantie chien

QA-1234
```

- Types autorisés : `feat`, `fix`, `test`, `refactor`, `chore`, `docs`, `ci`.
- **Règle minimale non négociable** : un message non vide qui décrit *quoi* et *pourquoi*. Le trigramme
  seul ne suffit plus.
- Format complet « Conventional Commits » = cible idéale, mais on tolère la forme simple ci-dessus le
  temps de la montée en compétence.

## 5. Protection de `master` et `develop` (garde-fous)

À configurer dans Bitbucket sur **chaque dépôt**, pour les **deux** branches permanentes :

- ❌ Interdire le push direct sur `master` **et** `develop`.
- ✅ Exiger une **PR** pour tout merge vers `master` ou `develop`.
- ✅ Exiger **1 reviewer** minimum (approbation) avant merge.
- ✅ Exiger que le **build CI passe** (quand le hook Jenkins est branché).
- ✅ Supprimer la branche source après merge (garde le dépôt propre — ne s'applique qu'aux branches
  de travail, jamais à `develop`).

Ces règles transforment la convention en **contrainte technique** : impossible de retomber dans le bazar.

## 6. Cycle de travail type (le geste quotidien)

```bash
# 1. partir de develop à jour
git checkout develop
git pull

# 2. créer sa branche de travail (nom = action en cours)
git checkout -b fix/QA-1290-locator-garantie

# 3. travailler, committer UNIQUEMENT les fichiers ciblés
git add <fichiers ciblés>
git commit -m "fix(souscription): corrige le locator obsolète de la garantie chien

QA-1290"

# 4. pousser
git push -u origin fix/QA-1290-locator-garantie

# 5. ouvrir la PR vers develop dans Bitbucket -> 1 reviewer -> merge
```

> Phase 1 outillée : l'assistant peut **préparer branche + commit + push** mais **n'ouvre pas la PR**
> automatiquement ; l'ouverture/merge de PR reste un geste humain (cf. skill `bitbucket-gitflow`).

## 7. Convention de PR

- **Titre** : résumé impératif + clé de ticket (`fix(souscription): locator garantie chien — QA-1290`).
- **Description** : contexte, ce qui change, preuve de validation (re-run vert / `mvn test`), impact
  éventuel sur les consommateurs du socle.
- **Cible** : `develop` (travail quotidien). Le merge `develop` → `master` est une PR de **release**.
- **1 reviewer** minimum. Le reviewer vérifie : périmètre cohérent, pas de fichiers parasites, tests verts.

## 8. Releases (socle)

- Pas encore de process formalisé → on commence **minimal** : une release = une **PR `develop` → `master`**
  + un **tag sur `master`** + publication Artifactory (cf. skills `artifactory-jfrog-settings` et la
  feuille de route `roadmap.md` étape 10).
- Versioning **SemVer** (à figer dans `roadmap.md` étape 2). Tag = `1.0.0`, `1.1.0`, ...
- Qui publie / sur quel critère : à décider à l'étape 2 de la roadmap. **Ne pas** bloquer le Git flow
  là-dessus : le flow de branches est indépendant et utilisable dès maintenant.

## 9. Évolution possible (plus tard, si besoin réel)

On **n'introduit pas** ces éléments au démarrage ; on les garde en réserve :

- `release/*` : seulement si on stabilise des versions sur plusieurs jours.
- `hotfix/*` : seulement si on doit patcher une version live pendant qu'un autre travail est en cours
  sur `develop`.
- Conventional Commits strict + commitlint : quand l'équipe est à l'aise avec le format simple.

> Règle : **ajouter de la complexité seulement quand un problème concret la justifie**, pas par principe.

## 10. Points à arbitrer ensemble (avant de figer le PPT)

- [ ] Stratégie exacte de bascule `upgrade-v4` → `master` (merge PR vs renommage de branche par défaut).
- [ ] Ticketing : a-t-on toujours une clé Jira/ticket, ou parfois rien (impact sur le nommage) ?
- [ ] Merge strategy Bitbucket : merge commit / squash / fast-forward (recommandation : **squash** pour
      un historique `master` propre et lisible par des débutants).
- [ ] Qui a le droit d'approuver une PR (tout le monde / un noyau de reviewers).
- [ ] Calendrier du nettoyage des branches sur les 17 projets.
