package com.example.qa.internal.reporting;

import com.example.qa.api.reporting.ReportingManager;

/**
 * Remontée des résultats d'exécution vers ALM (OpenText/HP ALM) via son API REST
 * (version ~18.4 à confirmer avant implémentation).
 *
 * <p>Mapping cas de test ↔ ALM porté par les métadonnées Serenity existantes,
 * en priorité {@code @WithTag} (ex. {@code @WithTag("alm.testId:1042")}), lues par
 * réflexion — pas d'annotation maison sauf besoin avéré (décision D13).
 *
 * <p>Nommée {@code ApiClient} (et non {@code Client}) pour lever toute ambiguïté :
 * il s'agit d'un consommateur de l'API ALM, pas d'un composant applicatif côté client.
 * Squelette vide — contenu réel à fournir.
 */
public class AlmApiClient implements ReportingManager {

}
