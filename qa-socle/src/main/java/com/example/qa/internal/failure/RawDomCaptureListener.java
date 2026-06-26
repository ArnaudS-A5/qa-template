package com.example.qa.internal.failure;

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
 * {@code META-INF/services/net.thucydides.model.steps.StepListener}. La prise en compte effective de ce
 * mécanisme (vs un enregistrement manuel sur le {@code StepEventBus}) reste à valider par le spike côté
 * {@code qa-test}. Nécessite un constructeur public sans argument (fourni par défaut).
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
