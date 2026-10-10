package com.example.qa.internal.failure;

import java.time.ZonedDateTime;
import java.util.Optional;

import net.thucydides.core.webdriver.ThucydidesWebDriverSupport;
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
 * <p><b>Pont avec {@link TestFailureManager}</b> : ce dernier tourne dans {@code executionFinished} (point
 * de lecture validé, même thread que le test — D22-bis) et <b>consomme</b> la valeur via
 * {@link #consumeCapturedDom()} (lecture puis purge). Le thread worker étant réutilisé séquentiellement,
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
public class RawDomCaptureListener extends StepListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger("qa");

    /** DOM brut de la dernière step en échec, par thread de test. Le dernier échec (la feuille) gagne. */
    private static final ThreadLocal<String> CAPTURED_DOM = new ThreadLocal<>();

    @Override
    public void stepFailed(StepFailure failure) {
        capturePageSource();
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
     * <p><b>Pourquoi c'est nécessaire</b> : {@link #consumeCapturedDom()} purge, mais il n'est appelé que
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
        CAPTURED_DOM.remove();
    }

    /**
     * Dépose une capture <b>sans driver</b> — réservé aux tests du socle (visibilité <b>package</b>, donc
     * hors de portée du consommateur). Le chemin réel ({@link #stepFailed}) exige un WebDriver vivant :
     * sans ce point d'entrée, l'hygiène du {@link ThreadLocal} (PA-11) ne serait vérifiable qu'au
     * navigateur, alors que c'est précisément la partie qui se teste sans lui.
     */
    static void rememberForTests(String dom) {
        CAPTURED_DOM.set(dom);
    }

    /** Capte le DOM brut si un driver est instancié ; silencieux en cas d'indisponibilité. */
    private static void capturePageSource() {
        try {
            if (!ThucydidesWebDriverSupport.isDriverInstantiated()) {
                return;
            }
            WebDriver driver = ThucydidesWebDriverSupport.getDriver();
            if (driver != null) {
                CAPTURED_DOM.set(driver.getPageSource());
            }
        } catch (RuntimeException e) {
            LOGGER.debug("DOM brut indisponible au moment de l'échec de la step", e);
        }
    }

    /**
     * Renvoie le DOM brut capté pour le thread courant <b>et le purge</b> (consommation). Vide si aucune
     * capture n'a eu lieu (driver absent, capture en échec) → le consommateur applique son repli.
     */
    public static Optional<String> consumeCapturedDom() {
        String dom = CAPTURED_DOM.get();
        CAPTURED_DOM.remove();
        return Optional.ofNullable(dom);
    }
}
