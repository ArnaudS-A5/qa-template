package com.example.qa.secret;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.qa.QaToolkitException;

/**
 * Tests de {@link SecretException} : exception de domaine rattachée à la racine {@link QaToolkitException}
 * (attrapable d'un seul {@code catch}) et unchecked.
 */
class SecretExceptionTest {

    @Test
    @DisplayName("SecretException est un QaToolkitException unchecked")
    void domaineHeriteDeLaRacineUnchecked() {
        assertInstanceOf(QaToolkitException.class, new SecretException("x"));
    }
}
