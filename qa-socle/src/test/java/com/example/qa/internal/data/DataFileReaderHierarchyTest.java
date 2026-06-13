package com.example.qa.internal.data;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.qa.api.data.DataFileManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests de câblage des gestionnaires de données.
 *
 * <p>Les classes du socle sont encore des squelettes : ces tests valident pour
 * l'instant le contrat de hiérarchie (interface / abstract / implémentations).
 * Ils seront enrichis avec des cas métier (readRows / writeRows / getValue)
 * dès que la logique sera implémentée.
 */
class DataFileReaderHierarchyTest {

    @Test
    @DisplayName("CsvFileReaderWriter est un DataFileManager via AbstractDataFileManager")
    void csvFileReaderWriterRespecteLaHierarchie() {
        CsvFileReaderWriter readerWriter = new CsvFileReaderWriter();

        assertInstanceOf(AbstractDataFileManager.class, readerWriter);
        assertInstanceOf(DataFileManager.class, readerWriter);
    }

    @Test
    @DisplayName("ExcelFileReaderWriter est un DataFileManager via AbstractDataFileManager")
    void excelFileReaderWriterRespecteLaHierarchie() {
        ExcelFileReaderWriter readerWriter = new ExcelFileReaderWriter();

        assertInstanceOf(AbstractDataFileManager.class, readerWriter);
        assertInstanceOf(DataFileManager.class, readerWriter);
    }

    @Test
    @DisplayName("AbstractDataFileManager implémente bien l'interface DataFileManager")
    void abstractImplementeLInterface() {
        assertTrue(DataFileManager.class.isAssignableFrom(AbstractDataFileManager.class));
    }
}
