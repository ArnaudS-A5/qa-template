package com.example.qa.api.secret;

/**
 * Requête <b>neutre</b> décrivant le secret à récupérer via {@link SecretManager#getSecret(SecretRequest)}.
 *
 * <p>Type marqueur du contrat public (D12) : il <b>n'expose aucun paramètre spécifique</b> à un
 * fournisseur. Les paramètres concrets (CyberArk : AppID, Safe, Folder, Object/Query... probablement
 * via Builder) vivront dans l'implémentation {@code internal.secret}, soit par une requête concrète qui
 * implémente ce type, soit par traduction interne — à trancher lors du traitement du composant
 * {@code secret} (cf. D12).
 *
 * <p>Squelette — à étoffer (étape 6, composant secret).
 */
public interface SecretRequest {

}
