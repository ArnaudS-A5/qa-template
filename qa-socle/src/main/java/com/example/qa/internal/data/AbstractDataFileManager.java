package com.example.qa.internal.data;

import java.util.List;
import java.util.Map;
import com.example.qa.api.data.DataFileManager;
import com.example.qa.api.exception.DataFileException;

/**
 * Code <b>commun</b> aux gestionnaires de données (interne, D15). Porte ce qui ne dépend pas du format :
 * mapping en {@code List<Map<String,String>>}, gestion des en-têtes, validation des colonnes, accès
 * ciblé. Les sous-classes {@code ExcelFileReaderWriter} (POI) et {@code CsvFileReaderWriter} (texte)
 * ne fournissent que la <b>lecture/écriture brute</b> propre à leur format.
 *
 * <p>Patron de conception : {@link #readRows()} / {@link #getValue(int, String)} sont implémentés ici
 * en s'appuyant sur les primitives abstraites {@link #readRawRows(String)} /
 * {@link #writeRawRows(String, List)} que chaque format précise.
 *
 * <p>Squelette — signatures arrêtées (étape 6), corps réel à fournir (étape 8).
 */
public abstract class AbstractDataFileManager implements DataFileManager {

	@Override
	public List<Map<String, String>> readRows() {
		return null;
	}

	@Override
	public List<Map<String, String>> readRows(String source) {
		return null;
	}

	@Override
	public String getValue(int rowIndex, String column) {
		return null;
	}

	@Override
	public void writeRows(List<Map<String, String>> rows) {
	}

	@Override
	public void writeRows(String source, List<Map<String, String>> rows) {
	}

	// ============================================================================================
	// Primitives propres au format (à fournir par chaque sous-classe)
	// ============================================================================================

	/**
	 * Lit les lignes brutes de la source (1ʳᵉ ligne = en-têtes), sans interprétation commune.
	 *
	 * @param source nom de la source (feuille Excel) ; {@code null} = source par défaut
	 * @return les lignes brutes, chaque ligne étant la liste ordonnée de ses cellules
	 * @throws DataFileException en cas d'échec de lecture propre au format
	 */
	protected abstract List<List<String>> readRawRows(String source);

	/**
	 * Écrit les lignes brutes (1ʳᵉ ligne = en-têtes) dans la source, sans interprétation commune.
	 *
	 * @param source nom de la source (feuille Excel) ; {@code null} = source par défaut
	 * @param rawRows les lignes brutes à écrire (en-têtes inclus)
	 * @throws DataFileException en cas d'échec d'écriture propre au format
	 */
	protected abstract void writeRawRows(String source, List<List<String>> rawRows);
}
