package com.example.qa.api.data;

import java.util.List;
import java.util.Map;
import com.example.qa.api.exception.DataFileException;

/**
 * Contrat <b>neutre</b> de lecture/écriture de jeux de données de test (Excel ou CSV).
 *
 * <p>Le consommateur ne dépend que de cette interface (package {@code api.data}) : les implémentations
 * concrètes ({@code ExcelFileReaderWriter} / {@code CsvFileReaderWriter}) sont <b>internes</b> (D15) et
 * obtenues via la factory publique {@link DataFiles}, jamais par {@code new} direct. On peut ainsi
 * basculer Excel ↔ CSV sans toucher au code de test (D5).
 *
 * <p><b>Modèle de données</b> : une ligne = une {@code Map<String,String>} dont les <b>clés sont les
 * en-têtes de colonnes</b> ; un jeu = {@code List<Map<String,String>>} (l'ordre des lignes est
 * préservé). Adapté à {@code @ParameterizedTest} / {@code @MethodSource} de JUnit 5.
 *
 * <p><b>Erreurs</b> : toute défaillance (fichier introuvable, colonne attendue absente, format
 * invalide, classeur chiffré illisible...) est levée en {@link DataFileException} <b>unchecked</b>
 * (D18), l'exception bas-niveau (POI, I/O) étant conservée en {@code cause}.
 *
 * <p>Squelette — signatures arrêtées (étape 6), corps réel à fournir (étape 8).
 */
public interface DataFileManager {

	/**
	 * Lit toutes les lignes de la source par défaut (1ʳᵉ feuille pour Excel, tout le fichier pour CSV).
	 *
	 * @return les lignes, clé = en-tête de colonne ; liste vide si aucune ligne de données
	 * @throws DataFileException en cas d'échec de lecture ou d'en-têtes invalides
	 */
	List<Map<String, String>> readRows() throws DataFileException;

	/**
	 * Lit toutes les lignes d'une source nommée (nom de feuille Excel ; ignoré/òu nom logique en CSV).
	 *
	 * @param source nom de la feuille (Excel) à lire
	 * @return les lignes, clé = en-tête de colonne
	 * @throws DataFileException en cas d'échec de lecture, de source introuvable ou d'en-têtes invalides
	 */
	List<Map<String, String>> readRows(String source) throws DataFileException;

	/**
	 * Accès ciblé à une cellule : valeur de la colonne {@code column} à la ligne d'index {@code rowIndex}
	 * (0-based, hors ligne d'en-têtes).
	 *
	 * @param rowIndex index de la ligne de données (0-based)
	 * @param column   en-tête de la colonne
	 * @return la valeur de la cellule (chaîne), jamais {@code null} (cellule vide → chaîne vide)
	 * @throws DataFileException si l'index est hors bornes ou la colonne inconnue
	 */
	String getValue(int rowIndex, String column) throws DataFileException;

	/**
	 * Écrit (ou réécrit) un jeu de lignes dans la source par défaut. Les en-têtes sont déduits des clés
	 * des {@code Map} (union ordonnée). Sert à exporter des résultats ou des jeux générés.
	 *
	 * @param rows les lignes à écrire (clé = en-tête de colonne)
	 * @throws DataFileException en cas d'échec d'écriture
	 */
	void writeRows(List<Map<String, String>> rows) throws DataFileException;

	/**
	 * Écrit (ou réécrit) un jeu de lignes dans une source nommée (feuille Excel).
	 *
	 * @param source nom de la feuille (Excel) cible
	 * @param rows   les lignes à écrire (clé = en-tête de colonne)
	 * @throws DataFileException en cas d'échec d'écriture
	 */
	void writeRows(String source, List<Map<String, String>> rows) throws DataFileException;
}
