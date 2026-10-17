package com.example.qa.data;

import java.nio.file.Path;
import java.util.List;

/**
 * Lecture/écriture de jeux de données au format <b>Excel</b> (Apache POI), y compris classeurs
 * <b>chiffrés</b> (.xlsx protégés par mot de passe). Implémentation interne (D15) : le consommateur
 * passe par la factory {@code DataFiles}, jamais par {@code new} direct.
 *
 * <p>Ne porte que le brut Excel (lecture/écriture des cellules, feuilles, déchiffrement) ; tout le
 * commun (mapping, en-têtes, validation) vient de {@link AbstractDataFileManager}.
 *
 * <p>Squelette — signatures arrêtées (étape 6), corps réel à fournir (étape 8).
 */
public class ExcelFileReaderWriter extends AbstractDataFileManager {

	/** Message du brut Excel non encore implémenté (à fournir à l'étape 8). */
	private static final String A_FOURNIR = "Lecture/écriture Excel non implémentée (à fournir étape 8)";

	/** Constructeur sans cible (câblage / tests de hiérarchie). */
	public ExcelFileReaderWriter() {
	}

	/** Cible un classeur Excel non chiffré. */
	public ExcelFileReaderWriter(Path file) {
		// Cible mémorisée à l'implémentation (étape 8).
	}

	/** Cible un classeur Excel chiffré, ouvert avec le mot de passe fourni. */
	public ExcelFileReaderWriter(Path file, String password) {
		// Cible et mot de passe mémorisés à l'implémentation (étape 8).
	}

	@Override
	protected List<List<String>> readRawRows(String source) {
		throw new UnsupportedOperationException(A_FOURNIR);
	}

	@Override
	protected void writeRawRows(String source, List<List<String>> rawRows) {
		throw new UnsupportedOperationException(A_FOURNIR);
	}
}
