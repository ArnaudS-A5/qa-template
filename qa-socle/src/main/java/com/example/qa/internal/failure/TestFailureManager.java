package com.example.qa.internal.failure;

/**
 * Orchestrateur (mince) de la capture d'échec. Produit le dossier
 * {@code KO__{ENV}__{Test}__{ts}/} avec ses trois fichiers ({@code ERROR_*.log},
 * {@code FAIL_*.log}, dump HTML de la step). Aucun artefact pour un test OK.
 *
 * <p><b>Activation native</b> (cf. décision D16) : déclenché par un {@code StepListener}
 * Serenity auto-découvert (ServiceLoader / {@code META-INF/services}) — aucun type public,
 * aucune annotation, aucune référence côté consommateur.
 *
 * <p><b>Répartition</b> (cf. décision D16-bis) : la classe écrit elle-même les 3 fichiers
 * <b>en Java</b> (depuis le buffer masqué de {@code QaLogger}) — format, séparation
 * {@code ERROR_}/{@code FAIL_} et nommage du dossier {@code KO__} <b>identiques sur les 17 projets</b>,
 * indépendamment de leur {@code logback.xml}. <b>Logback</b> ne gère que le <i>log live</i>
 * (console / fichier courant pendant le run) ; <b>Surefire</b> gère l'exécution et le répertoire de
 * travail. Réglages via clés {@code serenity.conf} / system properties (toutes avec défaut :
 * {@code qa.failure.enabled}, {@code outputDir}, {@code dumpHtml}, {@code env}...).
 *
 * <p>Squelette vide — signatures réelles à fournir (étape 6).
 */
public class TestFailureManager {

}
