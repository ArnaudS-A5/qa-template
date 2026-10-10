# QA Template --- Revue d'architecture

## Domaine Reporting / Test Management

Ce document consolide les remarques et points d'attention issus de la
revue du domaine Reporting.

## Constat

-   Architecture globale saine.
-   Reporting automatique via listener JUnit.
-   Transparence pour les projets consommateurs.

## Abstraction incomplète

Le contrat expose encore des concepts ALM (`almTestId`,
`@WithTag("alm.testId")`, etc.), ce qui empêche une véritable neutralité
vis-à-vis d'autres outils.

### Recommandations

-   Neutraliser complètement le contrat.
-   Déplacer tout le mapping ALM dans `AlmApiClient`.
-   Utiliser des identifiants métier neutres.
-   Conserver `TestExecutionReport` comme modèle extensible.

## Gestion des exécutions

`publishStart()` devrait retourner une référence d'exécution réutilisée
par `publishEnd()`.

Ne pas confondre : - Test - Exécution

## Compatibilité

Le déclencheur doit rester JUnit Platform afin de fonctionner à
l'identique : - IDE - Maven - Jenkins

## Authentification

Recommandation : une session par JVM.

Partager la session entre les threads d'une même JVM si le client est
thread-safe.

Ne jamais partager entre : - forks Maven - JVM - agents Jenkins -
machines

## Distinction des niveaux

-   Thread
-   Fork Maven
-   JVM
-   Agent Jenkins
-   Node Selenium

Les nodes Selenium ne publient jamais les résultats.

## Gestion de session

-   authentification au premier appel
-   réutilisation cookies/token
-   réauthentification automatique sur expiration
-   rejeu unique
-   fermeture propre

## Résilience

Prévoir timeout, retry exponentiel, circuit breaker, journalisation et
éventuellement une file asynchrone.

## Modes

-   OFF
-   LOCAL
-   CI

## Actions à réaliser

-   Neutraliser l'abstraction.
-   Supprimer les références ALM du contrat.
-   Introduire une référence d'exécution.
-   Documenter officiellement la stratégie de session par JVM.
-   Documenter Thread / Fork / JVM / Jenkins / Selenium.
-   Prévoir la résilience réseau.
-   Ajouter les modes OFF / LOCAL / CI.
