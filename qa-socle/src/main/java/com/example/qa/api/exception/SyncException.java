package com.example.qa.api.exception;

/**
 * Erreur de synchronisation (web ou mobile) levée par {@code WebSync} / {@code MobileSync} au bout du
 * délai d'attente. Elle <b>traduit</b> les exceptions bas-niveau du driver (Selenium / Appium) —
 * absorbées pendant le polling (cf. D3) — en un échec porteur de sens, l'exception d'origine étant
 * conservée en {@code cause} (la stacktrace complète reste dans {@code FAIL_*.log}).
 *
 * <p>Porte les <b>messages différenciés</b> de D3 : « mauvaise page / page non chargée » si l'élément
 * est absent au timeout ; « application instable » s'il est présent mais jamais interactable. Ces
 * messages alimentent {@code TestFailureManager} et la classification d'échec de l'agent de
 * maintenance (D9).
 *
 * <p>Type <b>unifié web + mobile</b> (D4) : le consommateur attrape un seul type, que l'erreur vienne
 * de Selenium ou d'Appium — aucune exception {@code org.openqa.selenium.*} ne fuit dans le contrat
 * public (même principe que D12 / D15).
 */
public class SyncException extends QaToolkitException {

    public SyncException(String message) {
        super(message);
    }

    public SyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
