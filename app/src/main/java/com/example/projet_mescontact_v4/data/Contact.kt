package com.example.projet_mescontact_v4.data

/**
 * Modèle d'un contact (V4), correspondant à une ligne de la table `contacts`.
 *
 * @property id 0 tant que le contact n'est pas enregistré, puis la valeur `_id` attribuée par SQLite.
 * @property photoUri chemin d'une COPIE de la photo dans filesDir (jamais une URI de galerie temporaire).
 * @property latitude / [longitude] null = aucune position (NULL en base).
 * @property favori bonus : colonne ajoutée par la migration vers la version 2 de la base.
 */
data class Contact(
    val id: Long = NEW_ID,
    val nom: String,
    val prenom: String,
    val telephone: String,
    val email: String? = null,
    val photoUri: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val favori: Boolean = false
) {
    val nomComplet: String get() = "$prenom $nom".trim()

    val estNouveau: Boolean get() = id == NEW_ID

    val aUnePosition: Boolean
        get() = latitude != null && longitude != null &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    companion object {
        const val NEW_ID = 0L
    }
}
