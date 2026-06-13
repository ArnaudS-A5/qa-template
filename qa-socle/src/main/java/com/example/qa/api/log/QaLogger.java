package com.example.qa.api.log;

/**
 * Façade de journalisation du socle, au-dessus de SLF4J (qui reste le moteur d'écriture).
 *
 * <p>Responsabilités propres (qui justifient son existence face à SLF4J/Serenity) :
 * <ul>
 *   <li>format uniforme pour les 17 projets consommateurs (point d'entrée unique) ;</li>
 *   <li>accumulation de la trace par test, matière première des artefacts
 *       {@code FAIL_*.log} / {@code ERROR_*.log} produits par {@code TestFailureManager} ;</li>
 *   <li>masquage des secrets avant toute écriture (roadmap étape 7, OWASP).</li>
 * </ul>
 *
 * <p>Nommée {@code QaLogger} (et non {@code Logger}) pour éviter toute collision
 * d'auto-import avec {@code org.slf4j.Logger} / {@code java.util.logging.Logger}.
 * Squelette vide — signatures réelles à fournir.
 */
public class QaLogger {

}
