---
name: test-data-excel-manager
description: "Tests data-driven avec JUnit 5 paramétré et le socle DataFileManager (ExcelFileReaderWriter / CsvFileReaderWriter). USE FOR : externaliser les jeux de données, lire/écrire Excel/CSV, paramétrer des tests. DO NOT USE FOR : conventions POM générales (voir serenity-pom-conventions)."
---

# Skill — Données de test (`DataFileManager`) & JUnit 5 paramétré

## Architecture du socle données

```
api.data                  interface DataFileManager        # contrat PUBLIC : readRows / writeRows / getValue
                                  ▲
internal.data       AbstractDataFileManager                # code commun (interne) : validation colonnes,
                                  ▲                         #   mapping List<Map<String,String>>, en-têtes
                         ┌────────┴────────────┐
                ExcelFileReaderWriter   CsvFileReaderWriter # impls INTERNES (POI / texte délimité)
```

> Suffixe `ReaderWriter` : le contrat couvre lecture **et** écriture (cf. décision D14).
> Frontière `api`/`internal` : cf. décision **D15**. Le consommateur dépend de l'**interface**
> `DataFileManager` (package `api`) ; les impls sont **internes** et obtenues via **factory**
> (créée à l'étape 6 de la roadmap), jamais par `new` direct.

**Règle de conception** (cf. `custom-toolkit-integration`) :
plusieurs implémentations interchangeables ⇒ **interface** ; code commun ⇒ **abstract** ; les deux ⇒
interface **+** abstract.

## Contrat `DataFileManager`

- `readRows(...)` → `List<Map<String,String>>` (clé = en-tête de colonne).
- `writeRows(...)` → écriture (export de résultats, jeux générés).
- `getValue(...)` → accès ciblé à une cellule par clé.

> Signatures exactes à aligner sur le code réel fourni.

## Tests data-driven JUnit 5

```java
@ParameterizedTest
@MethodSource("jeuxDeDonnees")
void souscription_parametree(Map<String, String> ligne) {
    souscription.cocherGarantie(ligne.get("garantie"));
    souscription.selectionnerProfession(ligne.get("profession"));
    // assertions basées sur ligne.get("resultatAttendu")
}

static Stream<Map<String, String>> jeuxDeDonnees() {
    // Le consommateur ne fait PAS `new ExcelFileReaderWriter(...)` (impl interne, cf. D15).
    // Il passe par la factory publique (nom/signature à figer à l'étape 6 de la roadmap) :
    DataFileManager data = DataFiles.excel(/* chemin */);   // exemple indicatif
    return data.readRows(/* feuille */).stream();
}
```

## Bonnes pratiques

- **Externaliser** les données : pas de valeurs métier en dur dans les tests.
- Valider les **en-têtes attendus** (l'`AbstractDataFileManager` doit lever une erreur claire si une
  colonne manque).
- Une colonne `resultatAttendu` pour piloter l'assertion depuis la donnée.
- Choisir Excel (POI) pour les métiers, CSV pour les jeux simples/CI.

## Cohérence métier (lien maintenance)

Les libellés du fichier de données doivent correspondre au **référentiel applicatif**. Un écart
(« professionnel de santé » vs « professionnel santé ») n'est **pas** à corriger en inventant une
valeur : c'est un signal de divergence (cf. `failure-classification`).
