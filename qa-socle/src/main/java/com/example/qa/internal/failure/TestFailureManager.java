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
 * <p><b>Délégation</b> : la classe ne porte PAS toute la mécanique. L'écriture, le format et la
 * séparation {@code ERROR_}/{@code FAIL_} sont délégués à <b>Logback</b> ({@code logback.xml}) ;
 * l'exécution et le répertoire de travail à <b>Surefire</b>. Réglages via clés
 * {@code serenity.conf} / system properties (toutes avec défaut : {@code qa.failure.enabled},
 * {@code outputDir}, {@code dumpHtml}, {@code env}...).
 *
 * <p>Squelette vide — signatures réelles à fournir (étape 6).
 */
public class TestFailureManager {

}
