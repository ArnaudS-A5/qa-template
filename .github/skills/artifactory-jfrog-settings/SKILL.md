---
name: artifactory-jfrog-settings
description: "Configuration Maven settings.xml pour Artifactory JFrog, repos virtuels, publication, et versioning du socle (Parent POM + RELEASE + garde-fous, BOM en alternative). USE FOR : résoudre/dépanner des dépendances, publier le socle, cadrer le versioning. DO NOT USE FOR : exécution de tests locale."
---

# Skill — Artifactory JFrog & `settings.xml`

## 1. `settings.xml` (poste & CI)

Répartition des rôles :

- **POM (`qa-parent`)** : `distributionManagement` = OÙ publier (ids `artifactory-releases` /
  `artifactory-snapshots`, URLs placeholder à adapter).
- **`settings.xml`** : credentials + résolution. Ne porte JAMAIS le `distributionManagement`.

Éléments clés du `settings.xml` :

- **`<servers>`** : credentials. Les `<id>` doivent correspondre **exactement** à ceux du
  `distributionManagement` du POM. Ne jamais committer en clair — utiliser le chiffrement Maven
  (`settings-security.xml`) ou des variables d'environnement côté CI.
- **`<mirrors>`** : rediriger `*` vers le **repo virtuel** JFrog (agrège releases + snapshots + remotes)
  pour la **résolution** des dépendances.

```xml
<servers>
  <server>
    <id>artifactory-releases</id>
    <username>${env.ARTIFACTORY_USER}</username>
    <password>${env.ARTIFACTORY_TOKEN}</password>
  </server>
  <server>
    <id>artifactory-snapshots</id>
    <username>${env.ARTIFACTORY_USER}</username>
    <password>${env.ARTIFACTORY_TOKEN}</password>
  </server>
  <server>
    <id>jfrog-virtual</id>
    <username>${env.ARTIFACTORY_USER}</username>
    <password>${env.ARTIFACTORY_TOKEN}</password>
  </server>
</servers>

<mirrors>
  <mirror>
    <id>jfrog-virtual</id>
    <mirrorOf>*</mirrorOf>
    <url>https://artifactory.example.com/artifactory/maven-virtual</url>
  </mirror>
</mirrors>
```

## 2. Repos virtuels JFrog

- Un **virtual repo** agrège : `libs-release`, `libs-snapshot`, et les remotes (Maven Central).
- Le socle est **déployé** dans `libs-release-local` (ou `libs-snapshot-local`).

## 3. Publication du socle

```bash
mvn deploy            # publie selon distributionManagement du POM
mvn versions:set -DnewVersion=1.4.0
```

Le `distributionManagement` vit dans le **Parent POM (`qa-parent`)** et pointe vers les repos
`*-local` JFrog (URLs placeholder `artifactory.example.com` à remplacer par l'instance réelle).
Les credentials correspondants vivent dans le `settings.xml` (`<server>` avec les mêmes ids).

## 4. Versioning du socle — décision retenue

**Parent POM (`qa-parent`) + version `RELEASE`** dans les ~17 projets consommateurs.

- Objectif : **zéro modification** dans les projets lors d'une montée de version du socle.
- Les projets héritent du Parent POM ; la dépendance socle est gérée centralement.
- `RELEASE` résout automatiquement la dernière release publiée.

### Garde-fous (obligatoires)

1. **SemVer strict** : breaking change ⇒ version MAJEURE.
2. **CI de non-régression** avant toute release publiée.
3. **Mécanisme d'échappement** : en incident, un projet peut **épingler** une version fixe
   (override local) le temps de corriger.

### Risque assumé

`RELEASE` ⇒ **builds non reproductibles** (la version résolue dépend du moment). Compensé par les
garde-fous ci-dessus.

### Alternative documentée (non retenue)

**BOM** (`dependencyManagement`) : réduit à **1 ligne** par projet, mais pas zéro. Builds reproductibles.
Écartée car ne satisfait pas l'objectif « zéro modification ».

## 5. Dépannage

| Symptôme | Cause probable | Action |
|---|---|---|
| **401 Unauthorized** | credentials manquants/erronés | vérifier `<server>` id ↔ repo, token valide |
| **409 Conflict** au deploy | release déjà publiée (immuable) | bump de version, ne pas écraser une release |
| **Checksum mismatch** | cache local corrompu / mirror | `mvn -U`, purge `~/.m2` ciblée |
| Dépendance introuvable | mirror n'agrège pas le repo | vérifier le virtual repo et `mirrorOf` |
