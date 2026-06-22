package com.example.qa.internal.failure;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * Capture d'échec : <b>hook d'activation natif</b> ET <b>orchestrateur</b> de la production des
 * artefacts, en une seule <b>classe simple</b> (D5 : une seule implémentation, aucune alternative,
 * aucun appelant abstrait → ni interface, ni type public). Reste entièrement {@code internal} : jamais
 * référencée par le consommateur.
 *
 * <p><b>Activation native par présence du jar</b> (D16) : la classe implémente le SPI JUnit
 * {@link TestExecutionListener} et est déclarée dans
 * {@code META-INF/services/org.junit.platform.launcher.TestExecutionListener} (interne au jar). Le
 * {@code Launcher} JUnit Platform — moteur que Surefire lance à chaque {@code mvn test}/{@code verify},
 * en local comme en CI — l'auto-enregistre (même mécanisme que serenity-junit5). <b>Aucune annotation,
 * aucun import côté consommateur.</b>
 *
 * <p>⚠️ <b>Correction de D16</b> : Serenity ne découvre pas les {@code StepListener} par ServiceLoader
 * (enregistrement programmatique via {@code StepEventBus.registerListener}) ; le seul service
 * auto-découvert par présence du jar dans cette stack est le {@code TestExecutionListener} JUnit.
 *
 * <p><b>Comportement</b> : sur {@link #executionFinished} en échec, et si {@link #ENABLED_KEY} est
 * actif, {@link #captureFailure()} produit — <b>pour un test KO uniquement</b> — le dossier
 * {@code KO__{ENV}__{Test}__{ts}/} avec ses trois fichiers ({@code ERROR_*.log} synthétique,
 * {@code FAIL_*.log} trace complète, dump HTML de la step). Aucun artefact pour un test OK.
 *
 * <p><b>Répartition</b> (D16-bis) : écrit les 3 fichiers <b>en Java</b> depuis les {@code TestStep} du
 * {@code TestOutcome} Serenity ({@code getTestSteps()} + {@code TestStep.getException()}), <b>masquage</b>
 * appliqué à la sérialisation — format/nommage identiques sur les 17 projets, indépendamment de tout
 * {@code logback.xml}. <b>Surefire</b> gère l'exécution/répertoire ; <b>Logback</b> uniquement le
 * <i>log live</i>.
 *
 * <p><b>Pas de signature publique à geler</b> : le « contrat » d'étape 6 pour ce composant, ce sont
 * (a) les <b>clés de configuration</b> ci-dessous (toutes avec défaut) et (b) le <b>contrat de sortie</b>
 * {@code KO__…} (D8/D16, contrat versionné). Corps réels à l'étape 8.
 */
public class TestFailureManager implements TestExecutionListener {

    /**
     * Clé activant la production des artefacts d'échec (opt-out global). Défaut {@link #ENABLED_DEFAULT}.
     * Mettre à {@code false} dans {@code serenity.conf} désactive le mécanisme sans toucher au code
     * (remplace le « ne pas mettre l'annotation » rejeté en D16).
     */
    public static final String ENABLED_KEY = "qa.failure.artefacts.enabled";

    /** Production des artefacts activée par défaut. */
    public static final boolean ENABLED_DEFAULT = true;

    /** Clé : racine des dossiers de résultats KO. Défaut {@link #OUTPUT_DIR_DEFAULT}. */
    public static final String OUTPUT_DIR_KEY = "qa.failure.artefacts.outputDir";

    /** Racine par défaut des dossiers KO. */
    public static final String OUTPUT_DIR_DEFAULT = "target/qa-results";

    /** Clé : produire ou non le dump HTML de la step en échec. Défaut {@link #DUMP_HTML_DEFAULT}. */
    public static final String DUMP_HTML_KEY = "qa.failure.artefacts.dumpHtml";

    /** Dump HTML produit par défaut. */
    public static final boolean DUMP_HTML_DEFAULT = true;

    /**
     * Hook de cycle de vie JUnit : appelé en fin d'exécution de chaque test. Coquille (étape 6).
     * Étape 8 : si {@code testExecutionResult.getStatus() == FAILED} et que {@link #ENABLED_KEY} est
     * actif, déléguer à {@link #captureFailure()}. (Le {@code {ENV}} du nommage est lu de l'environnement
     * Serenity actif — propriété {@code environment}, D19 — jamais d'une clé socle dédiée.)
     */
    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        // Coquille (étape 6) : aucun corps tant que la production d'artefacts n'est pas implémentée (étape 8).
    }

    /**
     * Produit les artefacts du test en échec courant : dossier {@code KO__{ENV}__{Test}__{ts}/} +
     * {@code ERROR_*.log} / {@code FAIL_*.log} / dump HTML, depuis le {@code TestOutcome} Serenity (lu
     * via {@code StepEventBus}), masquage appliqué à la sérialisation. Le {@code {ENV}} du nommage vient
     * de l'<b>environnement Serenity actif</b> (propriété {@code environment}, D19), pas d'une clé
     * dédiée. Coquille (étape 6) — corps réel à l'étape 8.
     */
    public void captureFailure() {
        // Coquille (étape 6) : écriture du dossier KO__ + 3 fichiers à implémenter en étape 8.
    }
}
