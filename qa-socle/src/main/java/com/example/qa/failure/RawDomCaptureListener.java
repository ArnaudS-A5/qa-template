package com.example.qa.failure;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import net.thucydides.core.webdriver.ThucydidesWebDriverSupport;
import net.thucydides.model.steps.AnnotatedStepDescription;
import net.thucydides.model.steps.StepFailure;
import net.thucydides.model.steps.StepListenerAdapter;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Capture du <b>DOM brut</b> de la page au moment précis de l'échec d'une step (option A de la revue),
 * tant que le WebDriver est <b>encore vivant</b> — par opposition à la source « rapport » de Serenity
 * (vue Prism échappée, non rendable telle quelle), lue trop tard dans {@code executionFinished}.
 *
 * <p><b>Pourquoi un {@link StepListener}</b> : {@code stepFailed} est notifié <i>pendant</i> l'exécution,
 * avant la fermeture du driver par Serenity. On y appelle {@code driver.getPageSource()} (le DOM rendu,
 * qui s'affiche directement dans un navigateur) et on le mémorise dans un {@link ThreadLocal} — un par
 * thread de test, donc sûr en parallèle.
 *
 * <p><b>Une capture par step en échec</b>, dans l'ordre, avec le nom de la step tel que Serenity
 * l'affiche ({@code AnnotatedStepDescription}). En mode dur, un test n'a qu'une step en échec ; en mode
 * soft, chaque vérification en échec est une sous-étape (cf. {@code SoftAssertions}) et a sa capture.
 *
 * <p><b>Pont avec {@link TestFailureManager}</b> : ce dernier tourne dans {@code executionFinished} (point
 * de lecture validé, même thread que le test — D22-bis) et <b>consomme</b> les captures via
 * {@link #consumeCapturedDoms()} (lecture puis purge). Le thread worker étant réutilisé séquentiellement,
 * cette purge garantit qu'aucun DOM d'un test précédent ne fuit vers un test suivant.
 *
 * <p><b>Enregistrement</b> : déclaré comme SPI Serenity dans
 * {@code META-INF/services/net.thucydides.model.steps.StepListener}. Découverte par la seule présence
 * du jar <b>confirmée empiriquement</b> via le projet consommateur {@code qa-test} (cf. D16) : le DOM
 * brut est effectivement capté à l'exécution. Nécessite un constructeur public sans argument (fourni
 * par défaut).
 *
 * <p>Robuste par construction : si le driver est absent/fermé ou que la capture lève, on ne mémorise rien
 * et {@link TestFailureManager} retombe sur la source Serenity (repli — option C).
 */
public final class RawDomCaptureListener extends StepListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger("qa");

    /** DOM brut capté à chaque step en échec, dans l'ordre, par thread de test. */
    private static final ThreadLocal<List<CapturedDom>> CAPTURED_DOMS = ThreadLocal.withInitial(ArrayList::new);

    /** DOM brut d'une step en échec, avec le nom de la step tel qu'affiché par Serenity. */
    record CapturedDom(String stepName, String html) {
    }

    @Override
    public void stepFailed(StepFailure failure) {
        capturePageSource(failure);
    }

    // ============================================================================================
    // Hygiène du ThreadLocal : repartir d'une ardoise vierge à CHAQUE test (PA-11)
    // ============================================================================================

    @Override
    public void testStarted(String description) {
        discardStaleCapture();
    }

    @Override
    public void testStarted(String description, String id) {
        discardStaleCapture();
    }

    @Override
    public void testStarted(String description, String id, ZonedDateTime startTime) {
        discardStaleCapture();
    }

    @Override
    public void testRetried() {
        discardStaleCapture();
    }

    /**
     * Écarte une capture qui n'aurait <b>pas été consommée</b> par le test précédent, afin qu'un test ne
     * puisse jamais hériter du DOM d'un autre.
     *
     * <p><b>Pourquoi c'est nécessaire</b> : {@link #consumeCapturedDoms()} purge, mais il n'est appelé que
     * lorsque des artefacts sont réellement écrits. Une capture reste donc orpheline si les artefacts sont
     * désactivés ({@code qa.failure.artefacts.enabled=false}), si le test ne finit pas en {@code FAILED}
     * (step en échec puis rattrapée), si aucun {@code TestOutcome} n'est disponible, ou si l'écriture est
     * court-circuitée. Les threads workers étant <b>réutilisés</b>, le test suivant exécuté sur le même
     * thread se verrait alors attribuer un DOM périmé — un artefact faux, et trompeur au diagnostic.
     *
     * <p><b>Pourquoi au démarrage et non en fin de test</b> : {@link TestFailureManager} consomme la valeur
     * dans {@code executionFinished} (listener <b>JUnit Platform</b>), alors que {@code testFinished} est
     * émis par le bus <b>Serenity</b> ; l'ordre entre les deux n'est pas contractuel. Purger en fin de test
     * risquerait d'effacer le DOM <b>avant</b> son écriture. Purger à l'ouverture atteint le même but sans
     * jamais entrer en concurrence avec la consommation.
     */
    private static void discardStaleCapture() {
        CAPTURED_DOMS.remove();
    }

    /**
     * Dépose une capture <b>sans driver</b> — réservé aux tests du socle (visibilité <b>package</b>, donc
     * hors de portée du consommateur). Le chemin réel ({@link #stepFailed}) exige un WebDriver vivant :
     * sans ce point d'entrée, l'hygiène du {@link ThreadLocal} (PA-11) ne serait vérifiable qu'au
     * navigateur, alors que c'est précisément la partie qui se teste sans lui.
     */
    static void rememberForTests(String stepName, String dom) {
        CAPTURED_DOMS.get().add(new CapturedDom(stepName, dom));
    }

    /** Capte le DOM brut si un driver est instancié ; silencieux en cas d'indisponibilité. */
    private static void capturePageSource(StepFailure failure) {
        try {
            if (!ThucydidesWebDriverSupport.isDriverInstantiated()) {
                return;
            }
            WebDriver driver = ThucydidesWebDriverSupport.getDriver();
            String html = driver == null ? null : driver.getPageSource();
            if (html != null) {
                CAPTURED_DOMS.get().add(new CapturedDom(stepNameOf(failure), html));
            }
        } catch (RuntimeException e) {
            LOGGER.debug("DOM brut indisponible au moment de l'échec de la step", e);
        }
    }

    /** Nom de la step en échec tel que Serenity l'affiche dans le rapport ({@code TestStep#getDescription}). */
    private static String stepNameOf(StepFailure failure) {
        try {
            return AnnotatedStepDescription.from(failure.getDescription()).getName();
        } catch (RuntimeException unnamed) {
            return "step";
        }
    }

    /**
     * Renvoie les DOM bruts captés pour le thread courant, dans l'ordre des échecs, <b>et les purge</b>
     * (consommation). Vide si aucune capture n'a eu lieu (driver absent, capture en échec) → le
     * consommateur applique son repli.
     */
    static List<CapturedDom> consumeCapturedDoms() {
        List<CapturedDom> doms = List.copyOf(CAPTURED_DOMS.get());
        CAPTURED_DOMS.remove();
        return doms;
    }
}
