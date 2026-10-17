package com.example.qa.data;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.qa.QaToolkitException;

/**
 * Tests de {@link DataFileException} : exception de domaine rattachée à la racine {@link QaToolkitException}
 * (attrapable d'un seul {@code catch}) et unchecked.
 */
class DataFileExceptionTest {

    @Test
    @DisplayName("DataFileException est un QaToolkitException unchecked")
    void domaineHeriteDeLaRacineUnchecked() {
        assertInstanceOf(QaToolkitException.class, new DataFileException("x"));
    }
}
