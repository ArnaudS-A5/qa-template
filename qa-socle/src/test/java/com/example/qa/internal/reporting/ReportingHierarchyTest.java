package com.example.qa.internal.reporting;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests de câblage du package reporting.
 *
 * <p>Squelette pour l'instant : valide le contrat de hiérarchie (interface neutre /
 * implémentation API ALM). À enrichir avec la publication réelle de résultats
 * une fois implémentée.
 */
class ReportingHierarchyTest {

    @Test
    @DisplayName("AlmApiClient est un ReportingManager")
    void almApiClientRespecteLaHierarchie() {
        assertInstanceOf(ReportingManager.class, new AlmApiClient());
    }
}
