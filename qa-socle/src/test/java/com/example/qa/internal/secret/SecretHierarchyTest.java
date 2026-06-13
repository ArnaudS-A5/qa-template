package com.example.qa.internal.secret;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.example.qa.api.secret.SecretManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests de câblage du package secret.
 *
 * <p>Squelette pour l'instant : valide le contrat de hiérarchie (interface neutre /
 * implémentation API CyberArk). À enrichir avec la récupération réelle de secrets
 * une fois implémentée.
 */
class SecretHierarchyTest {

    @Test
    @DisplayName("CyberArkApiClient est un SecretManager")
    void cyberArkApiClientRespecteLaHierarchie() {
        assertInstanceOf(SecretManager.class, new CyberArkApiClient());
    }
}
