package com.example.qa.data;

import com.example.qa.QaToolkitException;

/**
 * Erreur de lecture/écriture d'un jeu de données (Excel/CSV) levée par les implémentations de
 * {@code DataFileManager} : fichier introuvable, colonne attendue manquante, format invalide,
 * classeur chiffré illisible... L'exception bas-niveau (Apache POI, I/O) est conservée en
 * {@code cause}.
 */
public final class DataFileException extends QaToolkitException {

    DataFileException(String message) {
        super(message);
    }

    DataFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
