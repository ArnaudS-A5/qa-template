---
name: vscode-config-troubleshooting
description: "Configuration et dépannage de VS Code pour un projet Java/Maven/Serenity : settings.json, launch.json, exécution/debug Maven, extensions Java, connexion navigateur en debug, depuis une installation de base. USE FOR : configurer l'IDE, faire fonctionner run/debug, résoudre les erreurs d'environnement Java dans VS Code. DO NOT USE FOR : commandes Maven (voir maven-local-run-debug)."
---

# Skill — Configuration & dépannage VS Code (Java/Maven/Serenity)

## Extensions requises (installation de base → projet QA)

- **Extension Pack for Java** (`vscjava.vscode-java-pack`) : langage, debug, Maven, test runner.
- **Maven for Java** (inclus dans le pack).
- (Optionnel) **Test Runner for Java** pour lancer les `@Test` depuis l'éditeur.

## `settings.json` (workspace `.vscode/settings.json`)

```json
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.test.config": {
    "vmArgs": ["-Dwebdriver.driver=chrome"]
  },
  "maven.executable.preferMavenWrapper": true
}
```

- Vérifier `java.jdt.ls.java.home` / le JDK sélectionné (palette → « Java: Configure Java Runtime »).

## `launch.json` (debug)

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Debug Test courant",
      "request": "launch",
      "mainClass": "",
      "vmArgs": "-Dwebdriver.driver=chrome -Dheadless.mode=false"
    }
  ]
}
```

- Pour déboguer un test précis : lancer via le **CodeLens « Debug »** au-dessus de la méthode `@Test`,
  ou via `mvnDebug` + attach (cf. `maven-local-run-debug`).

## Connexion navigateur en debug

- Garder le mode **non-headless** (`-Dheadless.mode=false`) pour voir le navigateur pas-à-pas.
- Profil **local** (WebDriver direct) recommandé pour le debug interactif.

## Dépannage courant

| Symptôme | Cause | Action |
|---|---|---|
| « Java runtime not found » | JDK non configuré | configurer le JDK (palette → Configure Java Runtime) |
| Projet non reconnu comme Maven | `pom.xml` non détecté | « Java: Clean Java Language Server Workspace », recharger |
| Tests non listés | Test Runner non actif / build KO | recompiler (`mvn -q test-compile`), recharger la fenêtre |
| Dépendances rouges | résolution Artifactory KO | cf. `artifactory-jfrog-settings` (settings.xml, 401) |
| Breakpoints ignorés | sources désynchronisées | rebuild + « Clean Workspace » |

## Bonnes pratiques

- Versionner `.vscode/settings.json` et `launch.json` **utiles** au projet (sans secrets).
- En cas d'état incohérent : « Clean Java Language Server Workspace » résout la majorité des cas.
