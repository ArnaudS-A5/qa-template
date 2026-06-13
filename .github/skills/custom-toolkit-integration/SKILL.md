---
name: custom-toolkit-integration
description: "Câblage des outils du socle custom (WebSync, DataFileManager, QaLogger, TestFailureManager) dans les tests : points d'injection, cycle de vie, hooks d'échec, et règle interface/abstract/classe. USE FOR : intégrer/étendre le socle, brancher logs et capture d'échec, décider d'une abstraction. DO NOT USE FOR : implémenter le moteur de synchro (voir stable-locators-and-waits)."
---

# Skill — Intégration du socle d'outils custom

> ⚠️ Les **signatures réelles** des classes du socle seront fournies par l'utilisateur (plusieurs
> propositions existent, notamment pour la synchro — à trier). Aligner l'intégration dessus.

## Composants du socle

| Composant | Rôle |
|---|---|
| `AbstractSyncManager` → `WebSync` / `MobileSync` | synchro robuste (fluentWait + flag JS) |
| `interface DataFileManager` + `AbstractDataFileManager` + `ExcelFileReaderWriter`/`CsvFileReaderWriter` | données (lecture + écriture) |
| `QaLogger` | journalisation aux points clés (façade SLF4J) |
| `TestFailureManager` | production des artefacts d'échec (`ERROR_*.log` + `FAIL_*.log` + dump HTML) |
| `interface SecretManager` + `CyberArkApiClient` | récupération de secrets au runtime |
| `interface ReportingManager` + `AlmApiClient` | remontée des résultats vers ALM |

## Règle de conception interface / abstract / classe

- **Plusieurs implémentations interchangeables** ⇒ **interface** (ex. `DataFileManager`).
- **Code commun à partager** ⇒ **classe abstraite** (ex. `AbstractDataFileManager`, `AbstractSyncManager`).
- **Les deux** ⇒ interface **+** classe abstraite.
- **Une seule implémentation, sans besoin de variante** ⇒ **classe simple** (pas d'abstraction prématurée).

> L'abstraction `AbstractSyncManager`/`MobileSync` n'est justifiée que par l'arrivée **future du
> mobile** (driver Appium différent, moteur commun). Ne pas abstraire « au cas où ».

## Points d'injection & cycle de vie

- **PageObjects** : reçoivent/instancient un `WebSync` ; toutes les interactions passent par lui.
- **QaLogger** : tracer l'entrée des actions métier et les choix de données (lisibilité des rapports).
- **Données** : injecter un `DataFileManager` (Excel/CSV) dans les `@MethodSource` / setup.
- **Hooks d'échec** : à l'échec, le socle produit le dossier `KO__...` avec ses **trois fichiers**
  (`ERROR_*.log` synthétique, `FAIL_*.log` complet, dump HTML de la step). Aucun artefact pour un test
  OK. **Activation native** (ServiceLoader / `StepListener` Serenity, cf. **D16**) : **rien à brancher**
  côté consommateur, **aucune annotation**, la seule présence du jar suffit. `TestFailureManager` reste
  un **orchestrateur mince** : l'écriture/format/séparation des logs est **déléguée à Logback**
  (`logback.xml`) et l'exécution/répertoire à **Surefire**. Réglages via clés `serenity.conf` /
  system properties (toutes avec défaut : `qa.failure.enabled`, `outputDir`, `dumpHtml`, `env`...).

## Exemple de câblage (indicatif)

```java
public class SouscriptionPage {
    private final WebSync sync;

    public SouscriptionPage(WebSync sync) {
        this.sync = sync;
    }

    public void cocherGarantie(String libelle) {
        QaLogger.action("Cocher garantie: " + libelle);
        sync.click(By.cssSelector("[data-testid='garantie-" + slug(libelle) + "']"));
    }
}
```

## Règles

- Les consommateurs ne manipulent **jamais** `WebDriver`/`WebElement` directement : tout via le socle.
- Ne pas dupliquer le moteur d'attente : **étendre** plutôt que copier.
- Respecter la rétro-compatibilité de l'API du socle (cf. agent `socle-framework-builder`).
- Attendre les signatures réelles avant de figer le câblage définitif.
