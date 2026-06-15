package com.example.qa.internal.secret;

import com.example.qa.api.secret.Secret;
import com.example.qa.api.secret.SecretManager;
import com.example.qa.api.secret.SecretRequest;

/**
 * Récupération de secrets via l'API REST CyberArk : construction de l'URL à partir
 * des paramètres (AppID, Safe, Folder, Object...), appel HTTP, extraction du mot de
 * passe de la charge JSON retournée.
 *
 * <p>Nommée {@code ApiClient} (et non {@code Client}) pour lever toute ambiguïté :
 * il s'agit d'un consommateur de l'API CyberArk, pas d'un composant applicatif côté client.
 * Squelette — contenu réel à fournir (mécanisme déjà validé manuellement, cf. D12).
 */
public class CyberArkApiClient implements SecretManager {

	@Override
	public Secret getSecret(SecretRequest request) {
		return null;
	}
}
