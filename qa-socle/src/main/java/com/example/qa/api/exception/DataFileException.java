package com.example.qa.api.exception;

/**
 * Erreur de lecture/écriture d'un jeu de données (Excel/CSV) levée par les implémentations de
 * {@code DataFileManager} : fichier introuvable, colonne attendue manquante, format invalide,
 * classeur chiffré illisible... L'exception bas-niveau (Apache POI, I/O) est conservée en
 * {@code cause}.
 */
public class DataFileException extends QaToolkitException {

    public DataFileException(String message) {
        super(message);
    }

    public DataFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
