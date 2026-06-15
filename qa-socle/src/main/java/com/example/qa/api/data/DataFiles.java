package com.example.qa.api.data;

import java.nio.file.Path;

/**
 * Factory <b>publique</b> des gestionnaires de données : <b>seul</b> point d'accès aux implémentations
 * {@code internal.data} (Excel/CSV), conformément à D15. Le consommateur obtient un
 * {@link DataFileManager} sans jamais faire {@code new} sur une impl, ni connaître son type concret.
 *
 * <p>Exemple (data-driven JUnit 5) :
 * <pre>{@code
 * DataFileManager data = DataFiles.excel(Path.of("src/test/resources/souscription.xlsx"));
 * return data.readRows("chien").stream();
 * }</pre>
 *
 * <p>Squelette — signatures arrêtées (étape 6), corps réel à fournir (étape 8).
 */
public final class DataFiles {

	private DataFiles() {
	}

	/**
	 * Gestionnaire pour un classeur Excel (non chiffré).
	 *
	 * @param file chemin du fichier {@code .xlsx}
	 * @return un {@link DataFileManager} adossé à Excel
	 */
	public static DataFileManager excel(Path file) {
		return null;
	}

	/**
	 * Gestionnaire pour un classeur Excel <b>chiffré</b> (protégé par mot de passe).
	 *
	 * @param file     chemin du fichier {@code .xlsx} chiffré
	 * @param password mot de passe d'ouverture
	 * @return un {@link DataFileManager} adossé à Excel chiffré
	 */
	public static DataFileManager excel(Path file, String password) {
		return null;
	}

	/**
	 * Gestionnaire pour un fichier CSV (séparateur par défaut).
	 *
	 * @param file chemin du fichier {@code .csv}
	 * @return un {@link DataFileManager} adossé à CSV
	 */
	public static DataFileManager csv(Path file) {
		return null;
	}

	/**
	 * Gestionnaire pour un fichier CSV avec un séparateur explicite.
	 *
	 * @param file      chemin du fichier {@code .csv}
	 * @param delimiter séparateur de colonnes (ex. {@code ';'})
	 * @return un {@link DataFileManager} adossé à CSV
	 */
	public static DataFileManager csv(Path file, char delimiter) {
		return null;
	}
}
