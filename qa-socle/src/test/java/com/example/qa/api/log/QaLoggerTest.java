package com.example.qa.api.log;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests de câblage du QaLogger.
 *
 * <p>Squelette pour l'instant : valide l'instanciation. À enrichir avec les
 * niveaux de log et le format de sortie une fois implémentés.
 */
class QaLoggerTest {

    @Test
    @DisplayName("QaLogger est instanciable")
    void qaLoggerEstInstanciable() {
        assertNotNull(new QaLogger());
    }
}
