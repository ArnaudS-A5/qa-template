package com.example.qa.internal.failure;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests de câblage du gestionnaire d'artefacts d'échec.
 *
 * <p>Squelette pour l'instant : valide l'instanciation. À enrichir avec la
 * production des artefacts (ERROR_/FAIL_*.log, dump HTML) une fois implémentée.
 */
class TestFailureManagerTest {

    @Test
    @DisplayName("TestFailureManager est instanciable")
    void testFailureManagerEstInstanciable() {
        assertNotNull(new TestFailureManager());
    }
}
