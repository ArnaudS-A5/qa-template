package com.example.qa.internal.data;

import java.nio.file.Path;
import java.util.List;
import com.example.qa.api.exception.DataFileException;

/**
 * Lecture/écriture de jeux de données au format <b>CSV</b> (texte délimité, sans POI). Implémentation
 * interne (D15) : le consommateur passe par la factory {@code DataFiles}, jamais par {@code new} direct.
 *
 * <p>Ne porte que le brut CSV (parsing/sérialisation des lignes selon le séparateur) ; tout le commun
 * (mapping, en-têtes, validation) vient de {@link AbstractDataFileManager}. En CSV, la notion de
 * « source nommée » (feuille) ne s'applique pas : le paramètre {@code source} est ignoré.
 *
 * <p>Squelette — signatures arrêtées (étape 6), corps réel à fournir (étape 8).
 */
public class CsvFileReaderWriter extends AbstractDataFileManager {

	/** Constructeur sans cible (câblage / tests de hiérarchie). */
	public CsvFileReaderWriter() {
	}

	/** Cible un fichier CSV (séparateur par défaut). */
	public CsvFileReaderWriter(Path file) {
	}

	/** Cible un fichier CSV avec un séparateur explicite (ex. {@code ';'}). */
	public CsvFileReaderWriter(Path file, char delimiter) {
	}

	@Override
	protected List<List<String>> readRawRows(String source) throws DataFileException {
		return null;
	}

	@Override
	protected void writeRawRows(String source, List<List<String>> rawRows) throws DataFileException {
	}
}
