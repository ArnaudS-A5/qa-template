package com.example.qa.internal.failure;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import net.serenitybdd.model.environment.EnvironmentSpecificConfiguration;
import net.thucydides.core.steps.BaseStepListener;
import net.thucydides.core.steps.StepEventBus;
import net.thucydides.model.domain.TestOutcome;
import net.thucydides.model.domain.TestStep;
import net.thucydides.model.domain.stacktrace.FailureCause;
import net.thucydides.model.environment.SystemEnvironmentVariables;
import net.thucydides.model.screenshots.ScreenshotAndHtmlSource;
import net.thucydides.model.util.EnvironmentVariables;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Capture d'échec : <b>hook d'activation natif</b> ET <b>orchestrateur</b> de la production des
 * artefacts, en une seule <b>classe simple</b> (D5). Reste entièrement {@code internal} : jamais
 * référencée par le consommateur.
 *
 * <p><b>Activation native par présence du jar</b> (D16) : implémente le SPI JUnit
 * {@link TestExecutionListener}, déclaré dans
 * {@code META-INF/services/org.junit.platform.launcher.TestExecutionListener}. Le {@code Launcher}
 * JUnit l'auto-enregistre à chaque {@code mvn test}/{@code verify}. <b>Aucune annotation côté
 * consommateur.</b>
 *
 * <p><b>Comportement</b> : sur {@link #executionFinished} en échec (et si {@link #ENABLED_KEY} est
 * actif), produit — pour un test KO uniquement — le dossier {@code KO__{ENV}__{Test}__{ts}/} avec ses
 * trois fichiers ({@code ERROR.log} synthétique, {@code FAIL.log} trace complète + historique des steps,
 * dump HTML de la step en erreur). Le {@code TestOutcome} Serenity est lu dans {@code executionFinished}
 * (point de lecture <b>validé</b> — D22-bis, y compris en parallèle).
 *
 * <p><b>Masquage</b> : appliqué <b>à la source</b> via {@link com.example.qa.api.secret.Secret#toString()}
 * — les descriptions de steps remontées par Serenity contiennent déjà le rendu masqué. Ce composant
 * écrit le contenu tel quel ; il ne tente pas de re-scanner du texte arbitraire à la recherche de secrets.
 */
public class TestFailureManager implements TestExecutionListener {

    /**
     * Clé activant la production des artefacts d'échec (opt-out global). Défaut {@link #ENABLED_DEFAULT}.
     * Mettre à {@code false} dans {@code serenity.conf} désactive le mécanisme sans toucher au code.
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

    /** Propriété Serenity portant l'environnement actif, source du {@code {ENV}} du nommage (D19). */
    static final String ENV_PROPERTY = "environment";

    /** Valeur de {@code {ENV}} si l'environnement Serenity n'est pas défini. */
    static final String ENV_DEFAULT = "UNKNOWN";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static final Logger LOGGER = LoggerFactory.getLogger("qa");

    /**
     * Hook de cycle de vie JUnit : appelé en fin d'exécution de chaque test. Produit les artefacts
     * <b>uniquement</b> si le test est en échec et que le mécanisme est activé.
     */
    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (!testIdentifier.isTest() || testExecutionResult.getStatus() != TestExecutionResult.Status.FAILED) {
            return;
        }
        if (!isEnabled()) {
            return;
        }
        TestOutcome outcome = currentOutcome();
        if (outcome != null) {
            writeArtefacts(outcome);
        }
    }

    /**
     * Produit les artefacts du test en échec : dossier {@code KO__{ENV}__{Test}__{ts}/} +
     * {@code ERROR.log} / {@code FAIL.log} / dump HTML, depuis le {@code TestOutcome} Serenity.
     */
    public void writeArtefacts(TestOutcome outcome) {
        try {
            String failingStepName = failingStep(outcome).map(TestStep::getDescription).orElse("step");
            String html = isDumpHtmlEnabled() ? failingStepHtml(outcome) : null;
            FailureArtefacts content = new FailureArtefacts(
                    buildErrorLog(outcome), buildFailLog(outcome), failingStepName, html);
            Path dir = writeArtefacts(Paths.get(outputDir()), env(), outcome.getName(),
                    ZonedDateTime.now(ZoneId.systemDefault()), content);
            LOGGER.debug("Artefacts d'échec écrits dans {}", dir);
        } catch (IOException e) {
            LOGGER.warn("Échec de l'écriture des artefacts KO pour le test « {} »", outcome.getName(), e);
        }
    }

    // ============================================================================================
    // Cœur « pur » (testable sans navigateur) : nommage + écriture des 3 fichiers à partir de données
    // ============================================================================================

    /**
     * Écrit le dossier {@code KO__…} et ses fichiers à partir de données déjà extraites (pas de
     * dépendance Serenity ici → testable directement). Le dump HTML n'est écrit que si {@code htmlSource}
     * est non {@code null}.
     *
     * @return le dossier créé
     */
    static Path writeArtefacts(Path baseDir, String env, String testName, ZonedDateTime when,
                               FailureArtefacts content) throws IOException {
        Path dir = baseDir.resolve(folderName(env, testName, when));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("ERROR.log"), nullToEmpty(content.errorLog()), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("FAIL.log"), nullToEmpty(content.failLog()), StandardCharsets.UTF_8);
        if (content.htmlSource() != null) {
            Files.writeString(dir.resolve(sanitize(content.failingStepName()) + ".html"),
                    content.htmlSource(), StandardCharsets.UTF_8);
        }
        return dir;
    }

    /** Données extraites du {@code TestOutcome} pour produire les 3 fichiers (groupe les contenus). */
    record FailureArtefacts(String errorLog, String failLog, String failingStepName, String htmlSource) {
    }

    /** Nom de dossier contractuel {@code KO__{ENV}__{NomDuTest}__{yyyy-MM-dd_HH-mm-ss}} (délimiteur {@code __}). */
    static String folderName(String env, String testName, ZonedDateTime when) {
        return "KO__" + sanitize(env) + "__" + sanitize(testName) + "__" + TIMESTAMP.format(when);
    }

    /**
     * Rend une chaîne sûre comme champ de nom de fichier/dossier : caractères interdits → {@code _},
     * runs de {@code _} réduits à un seul (jamais de {@code __} parasite qui casserait le re-découpage),
     * bords nettoyés.
     */
    static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        // Translittération ASCII (é->e, à->a, ç->c...) via décomposition NFD + suppression des diacritiques.
        String ascii = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        // Apostrophes / guillemets supprimés (collage) : "l'écran" -> "lecran".
        ascii = ascii.replaceAll("['’‘`\"]", "");
        String cleaned = trimUnderscores(collapseToUnderscores(ascii));
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }

    /** Remplace toute séquence de caractères interdits par un unique {@code _}. */
    private static String collapseToUnderscores(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        boolean lastUnderscore = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (isFilenameSafe(c)) {
                sb.append(c);
                lastUnderscore = false;
            } else if (!lastUnderscore) {
                sb.append('_');
                lastUnderscore = true;
            }
        }
        return sb.toString();
    }

    private static boolean isFilenameSafe(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-';
    }

    private static String trimUnderscores(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && s.charAt(start) == '_') {
            start++;
        }
        while (end > start && s.charAt(end - 1) == '_') {
            end--;
        }
        return s.substring(start, end);
    }

    // ============================================================================================
    // Extraction depuis le TestOutcome Serenity (couche fine)
    // ============================================================================================

    private static String buildErrorLog(TestOutcome outcome) {
        String type = nullToEmpty(outcome.getTestFailureErrorType());
        String message = nullToEmpty(outcome.getTestFailureMessage());
        return (type.isEmpty() ? "" : type + ": ") + message;
    }

    private static String buildFailLog(TestOutcome outcome) {
        StringBuilder sb = new StringBuilder("=== Cause ===\n");
        FailureCause cause = outcome.getTestFailureCause();
        if (cause != null) {
            StringWriter sw = new StringWriter();
            cause.toException().printStackTrace(new PrintWriter(sw));
            sb.append(sw);
        } else {
            sb.append(nullToEmpty(outcome.getTestFailureMessage())).append('\n');
        }
        sb.append("\n=== Historique des steps ===\n");
        for (TestStep step : outcome.getFlattenedTestSteps()) {
            sb.append('[').append(step.getResult()).append("] ").append(step.getDescription()).append('\n');
            if (isFailing(step) && step.getErrorMessage() != null && !step.getErrorMessage().isBlank()) {
                sb.append("    -> ").append(step.getErrorMessage()).append('\n');
            }
        }
        return sb.toString();
    }

    private static Optional<TestStep> failingStep(TestOutcome outcome) {
        return failingLeaf(outcome.getFlattenedTestSteps());
    }

    /**
     * Step en échec la plus pertinente : la <b>feuille</b> KO (l'action réellement fautive), pas la step
     * parente vers laquelle l'échec s'est propagé. On ne garde que les feuilles ({@code !hasChildren()})
     * et on prend la <b>dernière</b> KO de l'ordre aplati (la plus profonde réellement exécutée).
     */
    static Optional<TestStep> failingLeaf(List<TestStep> flattenedSteps) {
        return flattenedSteps.stream()
                .filter(step -> !step.hasChildren())
                .filter(TestFailureManager::isFailing)
                .reduce((first, last) -> last);
    }

    /**
     * Dump HTML de la step en échec. Priorité au <b>DOM brut</b> capté au {@code stepFailed} par
     * {@link RawDomCaptureListener} (option A : page rendable telle quelle, driver vivant) ; à défaut,
     * <b>repli</b> sur la source HTML stockée par Serenity (option C : vue « code » Prism, échappée).
     */
    private static String failingStepHtml(TestOutcome outcome) {
        return RawDomCaptureListener.consumeCapturedDom()
                .orElseGet(() -> failingStepHtmlFromSerenity(outcome));
    }

    /** Repli (option C) : dernière source HTML stockée par Serenity pour la step en échec. */
    private static String failingStepHtmlFromSerenity(TestOutcome outcome) {
        return failingStep(outcome)
                .flatMap(step -> step.getScreenshots().stream().reduce((first, last) -> last))
                .flatMap(ScreenshotAndHtmlSource::getHtmlSource)
                .map(TestFailureManager::readQuietly)
                .orElse(null);
    }

    private static boolean isFailing(TestStep step) {
        return Boolean.TRUE.equals(step.isFailure()) || Boolean.TRUE.equals(step.isError());
    }

    // ============================================================================================
    // Lecture de la configuration Serenity (D19) — toutes les clés ont un défaut
    // ============================================================================================

    private boolean isEnabled() {
        return config(ENABLED_KEY).map(Boolean::parseBoolean).orElse(ENABLED_DEFAULT);
    }

    private boolean isDumpHtmlEnabled() {
        return config(DUMP_HTML_KEY).map(Boolean::parseBoolean).orElse(DUMP_HTML_DEFAULT);
    }

    private String outputDir() {
        return config(OUTPUT_DIR_KEY).orElse(OUTPUT_DIR_DEFAULT);
    }

    private String env() {
        return config(ENV_PROPERTY).map(String::toUpperCase).filter(s -> !s.isBlank()).orElse(ENV_DEFAULT);
    }

    private static Optional<String> config(String key) {
        EnvironmentVariables env = SystemEnvironmentVariables.createEnvironmentVariables();
        return EnvironmentSpecificConfiguration.from(env).getOptionalProperty(key);
    }

    private static TestOutcome currentOutcome() {
        BaseStepListener listener = StepEventBus.getEventBus().getBaseStepListener();
        if (listener == null) {
            return null;
        }
        try {
            return listener.getCurrentTestOutcome();
        } catch (RuntimeException e) {
            return listener.latestTestOutcome().orElse(null);
        }
    }

    private static String readQuietly(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            LOGGER.warn("Lecture du dump HTML impossible : {}", file, e);
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
