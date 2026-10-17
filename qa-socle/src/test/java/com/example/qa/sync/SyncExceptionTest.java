package com.example.qa.sync;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.qa.QaToolkitException;

/**
 * Tests de {@link SyncException} : exception de domaine rattachée à la racine {@link QaToolkitException}
 * (attrapable d'un seul {@code catch}) et unchecked.
 */
class SyncExceptionTest {

    @Test
    @DisplayName("SyncException est un QaToolkitException unchecked")
    void domaineHeriteDeLaRacineUnchecked() {
        assertInstanceOf(QaToolkitException.class, new SyncException("x"));
    }

    @Test
    @DisplayName("La cause d'origine est conservée (traduction d'exception)")
    void causeConservee() {
        Throwable cause = new IllegalStateException("driver timeout");
        SyncException e = new SyncException("application instable", cause);

        assertSame(cause, e.getCause());
    }
}
