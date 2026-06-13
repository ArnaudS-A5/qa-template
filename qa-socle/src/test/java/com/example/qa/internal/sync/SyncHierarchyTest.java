package com.example.qa.internal.sync;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.example.qa.api.sync.MobileSync;
import com.example.qa.api.sync.WebSync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests de câblage des gestionnaires de synchronisation.
 *
 * <p>Squelettes pour l'instant : valide la hiérarchie de types. À enrichir avec
 * les stratégies d'attente (fluentWait / flag JS) une fois implémentées.
 */
class SyncHierarchyTest {

    @Test
    @DisplayName("WebSync hérite d'AbstractSyncManager")
    void webSyncHeriteDeLAbstract() {
        WebSync sync = new WebSync();

        assertInstanceOf(AbstractSyncManager.class, sync);
    }

    @Test
    @DisplayName("MobileSync hérite d'AbstractSyncManager")
    void mobileSyncHeriteDeLAbstract() {
        MobileSync sync = new MobileSync();

        assertInstanceOf(AbstractSyncManager.class, sync);
    }
}
