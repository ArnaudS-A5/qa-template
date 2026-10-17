package com.example.qa.secret;

import com.example.qa.QaToolkitException;

/**
 * Erreur de récupération d'un secret levée par les implémentations de {@code SecretManager}
 * (ex. {@code CyberArkApiClient}) : appel API en échec, secret introuvable, réponse illisible.
 *
 * <p>⚠️ Le message ne doit <b>jamais</b> contenir la valeur du secret (cf. masquage, étape 7).
 */
public final class SecretException extends QaToolkitException {

    SecretException(String message) {
        super(message);
    }

    SecretException(String message, Throwable cause) {
        super(message, cause);
    }
}
