---
name: serenity-reporting-triage
description: "Lecture et corrélation des rapports Serenity et des artefacts d'échec (target/site/serenity, logs ERROR_/FAIL_, dump HTML, screenshots) pour comprendre une cause racine. USE FOR : analyser un échec, corréler step/screenshot/stacktrace. DO NOT USE FOR : classer formellement la cause (voir failure-classification) ou appliquer un fix (voir auto-fix-failed-test)."
---

# Skill — Triage des rapports Serenity & artefacts d'échec

## Sources à corréler

1. **Rapport Serenity** : `target/site/serenity/index.html`
   - vue par test, steps OK/KO, screenshots automatiques, stacktrace.
2. **Artefacts custom** sous `target/qa-results/KO__{ENV}__{Test}__{ts}/` — dossier créé **uniquement**
  si le test est KO, contenant **toujours les trois fichiers** :
   - `ERROR_{ENV}_{ts}.log` : **erreur synthétique** (uniquement la cause directe).
   - `FAIL_{ENV}_{ts}.log` : **trace complète** (stacktrace + toutes les steps, OK incluses) → intention métier.
   - `{nomDeLaStepEnErreur}.html` : **dump du DOM** au moment de l'échec → DOM réel.

## Méthode de corrélation

1. Identifier la **step en échec** (nom = nom du fichier `.html`).
2. Lire `ERROR_*.log` → message + locator attendu.
3. Lire `FAIL_*.log` → **toutes** les steps précédentes pour reconstituer l'**intention métier**
   (quelle garantie, quelle donnée, quel parcours).
4. Ouvrir le **dump HTML** → comparer le locator attendu au **DOM réel**.
5. Regarder le **screenshot Serenity** → état visuel (page blanche, erreur serveur, mauvais écran).

## Ce qu'on cherche à distinguer

- Élément **absent** vs **présent mais inerte** (cf. messages différenciés du `WebSync`).
- Équivalent **métier** présent sous un autre locator (⇒ locator obsolète) vs réellement absent
  (⇒ anomalie applicative / donnée).
- Page **fausse/non chargée** vs application **instable**.

## Intégration `TestFailureManager`

`TestFailureManager` est responsable, à l'échec, de produire **systématiquement les trois artefacts** :

- `ERROR_*.log` (erreur synthétique),
- `FAIL_*.log` (trace complète),
- le dump HTML de la step en erreur.

(Le screenshot reste fourni par Serenity dans son propre rapport.)

Le mécanisme **produit toujours les trois** (contrat de sortie, cf. D16). Si malgré tout un fichier
**manque** : ne **bloque pas** l'analyse. Exploite ce qui est disponible (fichiers présents + steps du
cas de test) et **note la complétude** (« analyse partielle »). Un dump HTML manquant fragilise surtout
la **comparaison locator↔DOM** (donc la décision de **corriger**), pas l'analyse de cause en général.

## Sortie attendue

Un constat factuel : step concernée, message, locator attendu, présence/absence dans le DOM, équivalent
métier éventuel, hypothèse de cause. Cette sortie alimente `failure-classification` puis, si pertinent,
`auto-fix-failed-test`.
