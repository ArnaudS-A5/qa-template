package com.example.qa.page;

import com.example.qa.sync.SyncTimeout;
import com.example.qa.sync.WebSync;
import net.serenitybdd.core.pages.PageObject;
import org.openqa.selenium.WebDriver;

/**
 * Classe de base des <b>Page Objects</b> des projets consommateurs : fournit d'emblée le
 * {@link WebSync} du socle, sans aucune déclaration à écrire.
 *
 * <p>Étend {@link PageObject} (Serenity) : la navigation ({@code open()}, {@code @DefaultUrl},
 * {@code webdriver.base.url}) et le reste de l'outillage Serenity restent disponibles à l'identique.
 * Le seul apport est le champ {@link #web} — et la garantie que <b>toute</b> page dispose de la
 * synchronisation du socle sans geste préalable.
 *
 * <p><b>Usage</b> :
 * <pre>{@code
 * public class SouscriptionPage extends QaPageObject {
 *
 *     private static final By VALIDER       = By.id("valider");
 *     private static final By RECAPITULATIF = By.id("recapitulatif");
 *
 *     public void valider() {
 *         web.click(VALIDER);                                     // profil MEDIUM (15 s)
 *         web(SyncTimeout.LONG).shouldBeVisible(RECAPITULATIF);   // zone lente : 60 s
 *     }
 * }
 * }</pre>
 *
 * <p><b>Pourquoi un champ {@code final} est sûr</b> : {@code WebSync} ne mémorise <b>aucun</b> driver.
 * Son état se limite au profil de timeout et au contexte {@code within} ; le driver est résolu
 * <b>à chaque appel</b> sur le thread courant. Construire la page avant l'ouverture du navigateur ne
 * pose donc aucun problème — l'initialisation paresseuse défensive (« instancier à la 1ʳᵉ
 * utilisation ») est inutile.
 *
 * <p><b>Oubli impossible</b> : hériter de cette classe est vérifié par le <b>compilateur</b> — sans
 * elle, {@link #web} n'existe pas. Contrairement à un mécanisme opt-in par annotation (écarté par D16),
 * il n'y a pas de cas « oublié qui ne dit rien ».
 *
 * <p><b>Limite assumée</b> : {@code PageObject} continue d'exposer l'API Serenity native
 * ({@code $()}, {@code find()}, {@code element()}), qui <b>court-circuite</b> la synchro du socle
 * (profils, diagnostic, no-leak). Cette classe rend le bon geste immédiat, elle ne l'impose pas ;
 * la détection des contournements relève d'une règle ArchUnit livrée séparément.
 */
public abstract class QaPageObject extends PageObject {

    /**
     * Synchronisation du socle au profil <b>MEDIUM</b> ({@link SyncTimeout#MEDIUM}, 15 s par défaut) :
     * le cas courant. Pour une zone plus lente ou plus rapide, voir {@link #web(SyncTimeout)}.
     */
    protected final WebSync web = new WebSync();

    /** Page construite sans driver explicite (cas standard : Serenity le résout au thread courant). */
    protected QaPageObject() {
        super();
    }

    /** Page construite sur un driver donné (compatibilité avec l'instanciation Serenity). */
    protected QaPageObject(WebDriver driver) {
        super(driver);
    }

    /**
     * Synchronisation au <b>profil demandé</b> : renvoie une vue <b>distincte et immuable</b>, sans
     * modifier {@link #web}. L'appel suivant repart donc sur le profil par défaut — pas de « mode » à
     * réarmer, pas d'effet de bord.
     *
     * <p>Se compose avec {@code within} :
     * <pre>{@code
     * web(SyncTimeout.LONG).within(LIGNE_CLIENT).click(BOUTON_DETAIL);
     * }</pre>
     *
     * @param timeout profil d'attente à appliquer aux appels de la vue renvoyée
     * @return une façade de synchronisation adossée à ce profil
     */
    protected WebSync web(SyncTimeout timeout) {
        return new WebSync(timeout);
    }
}
