package com.example.projet_mescontact_v4.data

import com.example.projet_mescontact_v4.data.ContactContract.ContactEntry as E

/**
 * Ordres de tri autorisés (préférence « ordre_tri », bonus).
 * La clause ORDER BY vient de cette liste fermée, jamais d'une saisie utilisateur.
 */
enum class SortOrder(val prefValue: String, val orderBy: String) {
    NOM(
        "nom",
        "${E.COLUMN_NOM} COLLATE NOCASE ASC, ${E.COLUMN_PRENOM} COLLATE NOCASE ASC"
    ),
    PRENOM(
        "prenom",
        "${E.COLUMN_PRENOM} COLLATE NOCASE ASC, ${E.COLUMN_NOM} COLLATE NOCASE ASC"
    ),
    FAVORIS(
        "favoris",
        "${E.COLUMN_FAVORI} DESC, ${E.COLUMN_NOM} COLLATE NOCASE ASC, ${E.COLUMN_PRENOM} COLLATE NOCASE ASC"
    );

    companion object {
        val DEFAULT = FAVORIS
        fun fromPref(value: String?): SortOrder = entries.firstOrNull { it.prefValue == value } ?: DEFAULT
    }
}
