package com.example.qa.reporting;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.qa.QaToolkitException;

/**
 * Tests de {@link ReportingException} : exception de domaine rattachée à la racine {@link QaToolkitException}
 * (attrapable d'un seul {@code catch}) et unchecked.
 */
class ReportingExceptionTest {

    @Test
    @DisplayName("ReportingException est un QaToolkitException unchecked")
    void domaineHeriteDeLaRacineUnchecked() {
        assertInstanceOf(QaToolkitException.class, new ReportingException("x"));
    }
}
