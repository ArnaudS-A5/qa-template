package com.example.qa;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests de la racine de la hiérarchie d'exceptions du socle (étape 3).
 *
 * <p>Vérifie que {@link QaToolkitException} est une {@link RuntimeException} (unchecked) et qu'elle est
 * {@code abstract} : le consommateur l'attrape, il ne la lève jamais. Chaque exception de domaine est
 * testée dans son propre package ({@code SyncExceptionTest}, {@code DataFileExceptionTest}…), seul
 * endroit où ses constructeurs package-private sont accessibles.
 */
class ExceptionHierarchyTest {

    @Test
    @DisplayName("La racine est une RuntimeException abstraite")
    void racineAbstraiteEtUnchecked() {
        assertTrue(RuntimeException.class.isAssignableFrom(QaToolkitException.class));
        assertTrue(Modifier.isAbstract(QaToolkitException.class.getModifiers()));
    }
}
