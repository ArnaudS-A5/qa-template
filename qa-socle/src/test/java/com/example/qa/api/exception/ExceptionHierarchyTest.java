package com.example.qa.api.exception;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests de la hiérarchie d'exceptions du socle (étape 3).
 *
 * <p>Vérifie que chaque exception de domaine est bien un {@link QaToolkitException} (donc attrapable
 * d'un seul {@code catch}) et une {@link RuntimeException} (unchecked), et que la {@code cause}
 * d'origine est conservée — point clé pour la traduction des exceptions Selenium/POI en {@code cause}.
 */
class ExceptionHierarchyTest {

    @Test
    @DisplayName("Chaque exception de domaine est un QaToolkitException unchecked")
    void domaineHeriteDeLaRacineUnchecked() {
        assertInstanceOf(QaToolkitException.class, new SyncException("x"));
        assertInstanceOf(QaToolkitException.class, new DataFileException("x"));
        assertInstanceOf(QaToolkitException.class, new SecretException("x"));
        assertInstanceOf(QaToolkitException.class, new ReportingException("x"));
        assertInstanceOf(RuntimeException.class, new QaToolkitException("x"));
    }

    @Test
    @DisplayName("La cause d'origine est conservée (traduction d'exception)")
    void causeConservee() {
        Throwable cause = new IllegalStateException("driver timeout");
        SyncException e = new SyncException("application instable", cause);

        assertSame(cause, e.getCause());
    }
}
